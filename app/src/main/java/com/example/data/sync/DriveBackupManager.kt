package com.example.data.sync

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal

class DriveBackupManager(
    private val context: Context,
    private val authManager: GoogleAuthManager,
    private val okHttpClient: OkHttpClient,
    private val database: AppDatabase
) {
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private suspend fun getAuthHeader(): String {
        val token = authManager.getAccessToken() ?: throw IOException("OAuth token tidak tersedia. Harap login kembali.")
        return "Bearer $token"
    }

    // Ekspor seluruh database ke format JSON string
    suspend fun exportDatabaseToJson(): String = withContext(Dispatchers.IO) {
        val backupJson = JSONObject()

        val accounts = database.accountDao().getAllAccounts().map { AccountEntity.fromDomain(it.toDomain(BigDecimal.ZERO)) }
        val categories = database.categoryDao().getAllCategories()
        val transactions = database.transactionDao().getAllTransactions()
        val holdings = database.assetHoldingDao().getAllAssetHoldings()
        val budgets = database.budgetDao().getAllBudgets()
        val goals = database.goalDao().getAllGoals()
        val recurringRules = database.recurringRuleDao().getAllRecurringRules()
        val parsingRules = database.parsingRuleDao().getAllParsingRules()
        val importTemplates = database.importTemplateDao().getAllImportTemplates()
        val corrections = database.parserCorrectionDao().getAllCorrections()

        backupJson.put("accounts", JSONArray(accounts.map { accountToJson(it) }))
        backupJson.put("categories", JSONArray(categories.map { categoryToJson(it) }))
        backupJson.put("transactions", JSONArray(transactions.map { transactionToJson(it) }))
        backupJson.put("holdings", JSONArray(holdings.map { holdingToJson(it) }))
        backupJson.put("budgets", JSONArray(budgets.map { budgetToJson(it) }))
        backupJson.put("goals", JSONArray(goals.map { goalToJson(it) }))
        backupJson.put("recurringRules", JSONArray(recurringRules.map { recurringRuleToJson(it) }))
        backupJson.put("parsingRules", JSONArray(parsingRules.map { parsingRuleToJson(it) }))
        backupJson.put("importTemplates", JSONArray(importTemplates.map { importTemplateToJson(it) }))
        backupJson.put("corrections", JSONArray(corrections.map { correctionToJson(it) }))

        backupJson.toString()
    }

    // Melakukan backup JSON ke Google Drive
    suspend fun backupToDrive(): Unit = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        val jsonContent = exportDatabaseToJson()

        // 1. Cari apakah file sisauang_backup.json sudah ada
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='sisauang_backup.json'+and+trashed=false"
        val searchRequest = Request.Builder()
            .url(searchUrl)
            .header("Authorization", token)
            .get()
            .build()

        var fileId: String? = null
        okHttpClient.newCall(searchRequest).execute().use { response ->
            if (response.isSuccessful) {
                val resBody = response.body?.string()
                if (resBody != null) {
                    val files = JSONObject(resBody).getJSONArray("files")
                    if (files.length() > 0) {
                        fileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
        }

        // 2. Jika belum ada, buat metadata filenya terlebih dahulu
        if (fileId == null) {
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val metaJson = JSONObject().apply {
                put("name", "sisauang_backup.json")
                put("mimeType", "application/json")
            }
            val createRequest = Request.Builder()
                .url(createUrl)
                .header("Authorization", token)
                .post(metaJson.toString().toRequestBody(mediaTypeJson))
                .build()

            okHttpClient.newCall(createRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Gagal membuat metadata backup di Drive: ${response.code}")
                }
                val resBody = response.body?.string() ?: throw IOException("Metadata respon Drive kosong")
                fileId = JSONObject(resBody).getString("id")
            }
        }

        // 3. Upload konten media (JSON) menggunakan fileId tersebut
        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", token)
            .patch(jsonContent.toRequestBody(mediaTypeJson))
            .build()

        okHttpClient.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal mengupload konten backup: ${response.code} ${response.message}")
            }
        }
    }

    // Melakukan restore JSON dari Google Drive
    suspend fun restoreFromDrive(): Unit = withContext(Dispatchers.IO) {
        val token = getAuthHeader()

        // 1. Cari file sisauang_backup.json
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='sisauang_backup.json'+and+trashed=false"
        val searchRequest = Request.Builder()
            .url(searchUrl)
            .header("Authorization", token)
            .get()
            .build()

        var fileId: String? = null
        okHttpClient.newCall(searchRequest).execute().use { response ->
            if (response.isSuccessful) {
                val resBody = response.body?.string()
                if (resBody != null) {
                    val files = JSONObject(resBody).getJSONArray("files")
                    if (files.length() > 0) {
                        fileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
        }

        if (fileId == null) {
            throw IOException("File backup 'sisauang_backup.json' tidak ditemukan di Google Drive.")
        }

        // 2. Download konten file backup
        val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val downloadRequest = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", token)
            .get()
            .build()

        var jsonContent: String? = null
        okHttpClient.newCall(downloadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal mendownload file backup dari Drive: HTTP ${response.code}")
            }
            jsonContent = response.body?.string()
        }

        if (jsonContent.isNullOrBlank()) {
            throw IOException("Konten backup kosong atau rusak.")
        }

        restoreDatabaseFromJson(jsonContent!!)
    }

    suspend fun restoreDatabaseFromJson(jsonContent: String) = withContext(Dispatchers.IO) {
        val json = JSONObject(jsonContent)
        
        database.runInTransaction {
            // Kita jalankan pembersihan dan pengisian ulang data secara sekuensial
            // 1. Accounts
            val accountsArray = json.optJSONArray("accounts")
            if (accountsArray != null) {
                for (i in 0 until accountsArray.length()) {
                    val obj = accountsArray.getJSONObject(i)
                    val entity = jsonToAccount(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO accounts (id, name, type, icon, color, currency, initialBalance, isArchived, updatedAt, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.name, entity.type, entity.icon, entity.color, entity.currency, entity.initialBalance, if (entity.isArchived) 1 else 0, entity.updatedAt, if (entity.deleted) 1 else 0)
                    )
                }
            }

            // 2. Categories
            val categoriesArray = json.optJSONArray("categories")
            if (categoriesArray != null) {
                for (i in 0 until categoriesArray.length()) {
                    val obj = categoriesArray.getJSONObject(i)
                    val entity = jsonToCategory(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO categories (id, name, type, icon, color, isFavorite, sortOrder, parentId, updatedAt, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.name, entity.type, entity.icon, entity.color, if (entity.isFavorite) 1 else 0, entity.sortOrder, entity.parentId, entity.updatedAt, if (entity.deleted) 1 else 0)
                    )
                }
            }

            // 3. Transactions
            val transactionsArray = json.optJSONArray("transactions")
            if (transactionsArray != null) {
                for (i in 0 until transactionsArray.length()) {
                    val obj = transactionsArray.getJSONObject(i)
                    val entity = jsonToTransaction(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO transactions (id, amount, type, categoryId, subCategoryId, accountId, toAccountId, adminFee, note, dateTime, attachmentPath, tags, goalId, recurringRuleId, sourceInput, transactionHash, updatedAt, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.amount, entity.type, entity.categoryId, entity.subCategoryId, entity.accountId, entity.toAccountId, entity.adminFee, entity.note, entity.dateTime, entity.attachmentPath, entity.tags, entity.goalId, entity.recurringRuleId, entity.sourceInput, entity.transactionHash, entity.updatedAt, if (entity.deleted) 1 else 0)
                    )
                }
            }

            // 4. Holdings
            val holdingsArray = json.optJSONArray("holdings")
            if (holdingsArray != null) {
                for (i in 0 until holdingsArray.length()) {
                    val obj = holdingsArray.getJSONObject(i)
                    val entity = jsonToHolding(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO asset_holdings (id, name, category, quantity, buyPrice, currentPrice, nominal, interestRate, maturityDate, cicilan, isCompleted, linkedAccountId, updatedAt, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.name, entity.category, entity.quantity, entity.buyPrice, entity.currentPrice, entity.nominal, entity.interestRate, entity.maturityDate, entity.cicilan, if (entity.isCompleted) 1 else 0, entity.linkedAccountId, entity.updatedAt, if (entity.deleted) 1 else 0)
                    )
                }
            }

            // 5. Budgets
            val budgetsArray = json.optJSONArray("budgets")
            if (budgetsArray != null) {
                for (i in 0 until budgetsArray.length()) {
                    val obj = budgetsArray.getJSONObject(i)
                    val entity = jsonToBudget(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO budgets (id, name, categoryId, amount, period, customStartDate, customEndDate, carryOver, alertThreshold, updatedAt, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.name, entity.categoryId, entity.amount, entity.period, entity.customStartDate, entity.customEndDate, if (entity.carryOver) 1 else 0, entity.alertThreshold, entity.updatedAt, if (entity.deleted) 1 else 0)
                    )
                }
            }

            // 6. Goals
            val goalsArray = json.optJSONArray("goals")
            if (goalsArray != null) {
                for (i in 0 until goalsArray.length()) {
                    val obj = goalsArray.getJSONObject(i)
                    val entity = jsonToGoal(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO goals (id, name, targetAmount, targetDate, linkedAccountId, currentAmount, updatedAt, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.name, entity.targetAmount, entity.targetDate, entity.linkedAccountId, entity.currentAmount, entity.updatedAt, if (entity.deleted) 1 else 0)
                    )
                }
            }

            // 7. Recurring Rules
            val recurringRulesArray = json.optJSONArray("recurringRules")
            if (recurringRulesArray != null) {
                for (i in 0 until recurringRulesArray.length()) {
                    val obj = recurringRulesArray.getJSONObject(i)
                    val entity = jsonToRecurringRule(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO recurring_rules (id, name, amount, type, categoryId, accountId, toAccountId, note, frequency, interval, customDays, nextExecutionDate, isAutoExecute, isEnabled, goalId, updatedAt, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.name, entity.amount, entity.type, entity.categoryId, entity.accountId, entity.toAccountId, entity.note, entity.frequency, entity.interval, entity.customDays, entity.nextExecutionDate, if (entity.isAutoExecute) 1 else 0, if (entity.isEnabled) 1 else 0, entity.goalId, entity.updatedAt, if (entity.deleted) 1 else 0)
                    )
                }
            }

            // 8. Parsing Rules
            val parsingRulesArray = json.optJSONArray("parsingRules")
            if (parsingRulesArray != null) {
                for (i in 0 until parsingRulesArray.length()) {
                    val obj = parsingRulesArray.getJSONObject(i)
                    val entity = jsonToParsingRule(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO parsing_rules (id, appPackage, name, regexPattern, type, accountId, categoryId, amountGroupIndex, noteGroupIndex, deleted, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.appPackage, entity.name, entity.regexPattern, entity.type, entity.accountId, entity.categoryId, entity.amountGroupIndex, entity.noteGroupIndex, if (entity.deleted) 1 else 0, entity.updatedAt)
                    )
                }
            }

            // 9. Import Templates
            val importTemplatesArray = json.optJSONArray("importTemplates")
            if (importTemplatesArray != null) {
                for (i in 0 until importTemplatesArray.length()) {
                    val obj = importTemplatesArray.getJSONObject(i)
                    val entity = jsonToImportTemplate(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO import_templates (id, sourceName, dateColumnIndex, amountColumnIndex, noteColumnIndex, typeColumnIndex, categoryColumnIndex, delimiter, deleted, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.sourceName, entity.dateColumnIndex, entity.amountColumnIndex, entity.noteColumnIndex, entity.typeColumnIndex, entity.categoryColumnIndex, entity.delimiter, if (entity.deleted) 1 else 0, entity.updatedAt)
                    )
                }
            }

            // 10. Corrections
            val correctionsArray = json.optJSONArray("corrections")
            if (correctionsArray != null) {
                for (i in 0 until correctionsArray.length()) {
                    val obj = correctionsArray.getJSONObject(i)
                    val entity = jsonToCorrection(obj)
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO parser_corrections (id, keyword, mappedCategoryId, mappedAccountId, deleted, updatedAt) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(entity.id, entity.keyword, entity.mappedCategoryId, entity.mappedAccountId, if (entity.deleted) 1 else 0, entity.updatedAt)
                    )
                }
            }
        }
    }

    // JSON Helper Mappers
    private fun accountToJson(e: AccountEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("type", e.type)
        put("icon", e.icon)
        put("color", e.color)
        put("currency", e.currency)
        put("initialBalance", e.initialBalance)
        put("isArchived", e.isArchived)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun jsonToAccount(j: JSONObject) = AccountEntity(
        id = j.getString("id"),
        name = j.getString("name"),
        type = j.getString("type"),
        icon = j.getString("icon"),
        color = j.getString("color"),
        currency = j.optString("currency", "IDR"),
        initialBalance = j.getString("initialBalance"),
        isArchived = j.optBoolean("isArchived", false),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
        deleted = j.optBoolean("deleted", false)
    )

    private fun categoryToJson(e: CategoryEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("type", e.type)
        put("icon", e.icon)
        put("color", e.color)
        put("isFavorite", e.isFavorite)
        put("sortOrder", e.sortOrder)
        put("parentId", e.parentId ?: JSONObject.NULL)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun jsonToCategory(j: JSONObject) = CategoryEntity(
        id = j.getString("id"),
        name = j.getString("name"),
        type = j.getString("type"),
        icon = j.getString("icon"),
        color = j.getString("color"),
        isFavorite = j.optBoolean("isFavorite", false),
        sortOrder = j.optInt("sortOrder", 0),
        parentId = if (j.isNull("parentId")) null else j.getString("parentId"),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
        deleted = j.optBoolean("deleted", false)
    )

    private fun transactionToJson(e: TransactionEntity) = JSONObject().apply {
        put("id", e.id)
        put("amount", e.amount)
        put("type", e.type)
        put("categoryId", e.categoryId ?: JSONObject.NULL)
        put("subCategoryId", e.subCategoryId ?: JSONObject.NULL)
        put("accountId", e.accountId)
        put("toAccountId", e.toAccountId ?: JSONObject.NULL)
        put("adminFee", e.adminFee ?: JSONObject.NULL)
        put("note", e.note)
        put("dateTime", e.dateTime)
        put("attachmentPath", e.attachmentPath ?: JSONObject.NULL)
        put("tags", e.tags)
        put("goalId", e.goalId ?: JSONObject.NULL)
        put("recurringRuleId", e.recurringRuleId ?: JSONObject.NULL)
        put("sourceInput", e.sourceInput)
        put("transactionHash", e.transactionHash ?: JSONObject.NULL)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun jsonToTransaction(j: JSONObject) = TransactionEntity(
        id = j.getString("id"),
        amount = j.getString("amount"),
        type = j.getString("type"),
        categoryId = if (j.isNull("categoryId")) null else j.getString("categoryId"),
        subCategoryId = if (j.isNull("subCategoryId")) null else j.getString("subCategoryId"),
        accountId = j.getString("accountId"),
        toAccountId = if (j.isNull("toAccountId")) null else j.getString("toAccountId"),
        adminFee = if (j.isNull("adminFee")) null else j.getString("adminFee"),
        note = j.optString("note", ""),
        dateTime = j.getLong("dateTime"),
        attachmentPath = if (j.isNull("attachmentPath")) null else j.getString("attachmentPath"),
        tags = j.optString("tags", ""),
        goalId = if (j.isNull("goalId")) null else j.getString("goalId"),
        recurringRuleId = if (j.isNull("recurringRuleId")) null else j.getString("recurringRuleId"),
        sourceInput = j.optString("sourceInput", "MANUAL"),
        transactionHash = if (j.isNull("transactionHash")) null else j.getString("transactionHash"),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
        deleted = j.optBoolean("deleted", false)
    )

    private fun holdingToJson(e: AssetHoldingEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("category", e.category)
        put("quantity", e.quantity)
        put("buyPrice", e.buyPrice)
        put("currentPrice", e.currentPrice)
        put("nominal", e.nominal)
        put("interestRate", e.interestRate)
        put("maturityDate", e.maturityDate ?: JSONObject.NULL)
        put("cicilan", e.cicilan ?: JSONObject.NULL)
        put("isCompleted", e.isCompleted)
        put("linkedAccountId", e.linkedAccountId ?: JSONObject.NULL)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun jsonToHolding(j: JSONObject) = AssetHoldingEntity(
        id = j.getString("id"),
        name = j.getString("name"),
        category = j.getString("category"),
        quantity = j.getString("quantity"),
        buyPrice = j.getString("buyPrice"),
        currentPrice = j.getString("currentPrice"),
        nominal = j.getString("nominal"),
        interestRate = j.getString("interestRate"),
        maturityDate = if (j.isNull("maturityDate")) null else j.getLong("maturityDate"),
        cicilan = if (j.isNull("cicilan")) null else j.getString("cicilan"),
        isCompleted = j.optBoolean("isCompleted", false),
        linkedAccountId = if (j.isNull("linkedAccountId")) null else j.getString("linkedAccountId"),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
        deleted = j.optBoolean("deleted", false)
    )

    private fun budgetToJson(e: BudgetEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("categoryId", e.categoryId ?: JSONObject.NULL)
        put("amount", e.amount)
        put("period", e.period)
        put("customStartDate", e.customStartDate ?: JSONObject.NULL)
        put("customEndDate", e.customEndDate ?: JSONObject.NULL)
        put("carryOver", e.carryOver)
        put("alertThreshold", e.alertThreshold)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun jsonToBudget(j: JSONObject) = BudgetEntity(
        id = j.getString("id"),
        name = j.getString("name"),
        categoryId = if (j.isNull("categoryId")) null else j.getString("categoryId"),
        amount = j.getString("amount"),
        period = j.getString("period"),
        customStartDate = if (j.isNull("customStartDate")) null else j.getLong("customStartDate"),
        customEndDate = if (j.isNull("customEndDate")) null else j.getLong("customEndDate"),
        carryOver = j.optBoolean("carryOver", false),
        alertThreshold = j.optDouble("alertThreshold", 0.8).toFloat(),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
        deleted = j.optBoolean("deleted", false)
    )

    private fun goalToJson(e: GoalEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("targetAmount", e.targetAmount)
        put("targetDate", e.targetDate ?: JSONObject.NULL)
        put("linkedAccountId", e.linkedAccountId ?: JSONObject.NULL)
        put("currentAmount", e.currentAmount)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun jsonToGoal(j: JSONObject) = GoalEntity(
        id = j.getString("id"),
        name = j.getString("name"),
        targetAmount = j.getString("targetAmount"),
        targetDate = if (j.isNull("targetDate")) null else j.getLong("targetDate"),
        linkedAccountId = if (j.isNull("linkedAccountId")) null else j.getString("linkedAccountId"),
        currentAmount = j.getString("currentAmount"),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
        deleted = j.optBoolean("deleted", false)
    )

    private fun recurringRuleToJson(e: RecurringRuleEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("amount", e.amount)
        put("type", e.type)
        put("categoryId", e.categoryId ?: JSONObject.NULL)
        put("accountId", e.accountId)
        put("toAccountId", e.toAccountId ?: JSONObject.NULL)
        put("note", e.note)
        put("frequency", e.frequency)
        put("interval", e.interval)
        put("customDays", e.customDays ?: JSONObject.NULL)
        put("nextExecutionDate", e.nextExecutionDate)
        put("isAutoExecute", e.isAutoExecute)
        put("isEnabled", e.isEnabled)
        put("goalId", e.goalId ?: JSONObject.NULL)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun jsonToRecurringRule(j: JSONObject) = RecurringRuleEntity(
        id = j.getString("id"),
        name = j.getString("name"),
        amount = j.getString("amount"),
        type = j.getString("type"),
        categoryId = if (j.isNull("categoryId")) null else j.getString("categoryId"),
        accountId = j.getString("accountId"),
        toAccountId = if (j.isNull("toAccountId")) null else j.getString("toAccountId"),
        note = j.optString("note", ""),
        frequency = j.getString("frequency"),
        interval = j.optInt("interval", 1),
        customDays = if (j.isNull("customDays")) null else j.getInt("customDays"),
        nextExecutionDate = j.getLong("nextExecutionDate"),
        isAutoExecute = j.optBoolean("isAutoExecute", false),
        isEnabled = j.optBoolean("isEnabled", true),
        goalId = if (j.isNull("goalId")) null else j.getString("goalId"),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
        deleted = j.optBoolean("deleted", false)
    )

    private fun parsingRuleToJson(e: ParsingRuleEntity) = JSONObject().apply {
        put("id", e.id)
        put("appPackage", e.appPackage)
        put("name", e.name)
        put("regexPattern", e.regexPattern)
        put("type", e.type)
        put("accountId", e.accountId)
        put("categoryId", e.categoryId ?: JSONObject.NULL)
        put("amountGroupIndex", e.amountGroupIndex)
        put("noteGroupIndex", e.noteGroupIndex ?: JSONObject.NULL)
        put("deleted", e.deleted)
        put("updatedAt", e.updatedAt)
    }

    private fun jsonToParsingRule(j: JSONObject) = ParsingRuleEntity(
        id = j.getString("id"),
        appPackage = j.getString("appPackage"),
        name = j.getString("name"),
        regexPattern = j.getString("regexPattern"),
        type = j.getString("type"),
        accountId = j.getString("accountId"),
        categoryId = if (j.isNull("categoryId")) null else j.getString("categoryId"),
        amountGroupIndex = j.optInt("amountGroupIndex", 1),
        noteGroupIndex = if (j.isNull("noteGroupIndex")) null else j.getInt("noteGroupIndex"),
        deleted = j.optBoolean("deleted", false),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun importTemplateToJson(e: ImportTemplateEntity) = JSONObject().apply {
        put("id", e.id)
        put("sourceName", e.sourceName)
        put("dateColumnIndex", e.dateColumnIndex)
        put("amountColumnIndex", e.amountColumnIndex)
        put("noteColumnIndex", e.noteColumnIndex)
        put("typeColumnIndex", e.typeColumnIndex ?: JSONObject.NULL)
        put("categoryColumnIndex", e.categoryColumnIndex ?: JSONObject.NULL)
        put("delimiter", e.delimiter)
        put("deleted", e.deleted)
        put("updatedAt", e.updatedAt)
    }

    private fun jsonToImportTemplate(j: JSONObject) = ImportTemplateEntity(
        id = j.getString("id"),
        sourceName = j.getString("sourceName"),
        dateColumnIndex = j.getInt("dateColumnIndex"),
        amountColumnIndex = j.getInt("amountColumnIndex"),
        noteColumnIndex = j.getInt("noteColumnIndex"),
        typeColumnIndex = if (j.isNull("typeColumnIndex")) null else j.getInt("typeColumnIndex"),
        categoryColumnIndex = if (j.isNull("categoryColumnIndex")) null else j.getInt("categoryColumnIndex"),
        delimiter = j.optString("delimiter", ","),
        deleted = j.optBoolean("deleted", false),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun correctionToJson(e: ParserCorrectionEntity) = JSONObject().apply {
        put("id", e.id)
        put("keyword", e.keyword)
        put("mappedCategoryId", e.mappedCategoryId ?: JSONObject.NULL)
        put("mappedAccountId", e.mappedAccountId ?: JSONObject.NULL)
        put("deleted", e.deleted)
        put("updatedAt", e.updatedAt)
    }

    private fun jsonToCorrection(j: JSONObject) = ParserCorrectionEntity(
        id = j.getString("id"),
        keyword = j.getString("keyword"),
        mappedCategoryId = if (j.isNull("mappedCategoryId")) null else j.getString("mappedCategoryId"),
        mappedAccountId = if (j.isNull("mappedAccountId")) null else j.getString("mappedAccountId"),
        deleted = j.optBoolean("deleted", false),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis())
    )
}
