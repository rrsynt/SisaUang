package com.example.data.sync

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.domain.model.SyncMeta
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal

class CloudSyncManager(
    private val context: Context,
    private val sheetsClient: GoogleSheetsSyncClient,
    private val database: AppDatabase
) {
    // Definisi kolom kanonik per tab dan sinonim pencocokan
    private val canonicalColumns = mapOf(
        "Accounts" to listOf("id", "name", "type", "icon", "color", "currency", "initialBalance", "isArchived", "updatedAt", "deleted"),
        "Categories" to listOf("id", "name", "type", "icon", "color", "isFavorite", "order", "parentId", "updatedAt", "deleted"),
        "Transactions" to listOf("id", "amount", "type", "categoryId", "subCategoryId", "accountId", "toAccountId", "adminFee", "note", "dateTime", "attachmentPath", "tags", "goalId", "recurringRuleId", "sourceInput", "transactionHash", "updatedAt", "deleted"),
        "Holdings" to listOf("id", "name", "category", "quantity", "buyPrice", "currentPrice", "nominal", "interestRate", "maturityDate", "cicilan", "isCompleted", "linkedAccountId", "updatedAt", "deleted"),
        "Budgets" to listOf("id", "name", "categoryId", "amount", "period", "customStartDate", "customEndDate", "carryOver", "alertThreshold", "updatedAt", "deleted"),
        "Goals" to listOf("id", "name", "targetAmount", "targetDate", "linkedAccountId", "currentAmount", "updatedAt", "deleted"),
        "RecurringRules" to listOf("id", "name", "amount", "type", "categoryId", "accountId", "toAccountId", "note", "frequency", "interval", "customDays", "nextExecutionDate", "isAutoExecute", "isEnabled", "goalId", "updatedAt", "deleted"),
        "ParsingRules" to listOf("id", "appPackage", "name", "regexPattern", "type", "accountId", "categoryId", "amountGroupIndex", "noteGroupIndex", "deleted", "updatedAt"),
        "ImportTemplates" to listOf("id", "sourceName", "dateColumnIndex", "amountColumnIndex", "noteColumnIndex", "typeColumnIndex", "categoryColumnIndex", "delimiter", "deleted", "updatedAt"),
        "Corrections" to listOf("id", "keyword", "mappedCategoryId", "mappedAccountId", "deleted", "updatedAt")
    )

    private val synonyms = mapOf(
        "uuid" to "id",
        "nominal" to "amount",
        "jumlah" to "amount",
        "jumlahuang" to "amount",
        "value" to "amount",
        "jenis" to "type",
        "kategoriid" to "categoryId",
        "kategori" to "categoryId",
        "category" to "categoryId",
        "akunid" to "accountId",
        "akun" to "accountId",
        "account" to "accountId",
        "keakunid" to "toAccountId",
        "keakun" to "toAccountId",
        "toaccount" to "toAccountId",
        "biayaadmin" to "adminFee",
        "catatan" to "note",
        "keterangan" to "note",
        "tanggal" to "dateTime",
        "waktu" to "dateTime",
        "date" to "dateTime",
        "lampiran" to "attachmentPath",
        "label" to "tags",
        "targetid" to "goalId",
        "sumber" to "sourceInput",
        "diubahpada" to "updatedAt",
        "dihapus" to "deleted",
        "nama" to "name",
        "saldoawal" to "initialBalance",
        "diarsipkan" to "isArchived",
        "warna" to "color",
        "matauang" to "currency",
        "pilihan" to "isFavorite",
        "urut" to "order",
        "indukid" to "parentId",
        "kuantitas" to "quantity",
        "hargabeli" to "buyPrice",
        "hargasakarang" to "currentPrice",
        "bunga" to "interestRate",
        "jatuhtempo" to "maturityDate",
        "selesai" to "isCompleted",
        "periode" to "period",
        "mulaikustom" to "customStartDate",
        "akhirkustom" to "customEndDate",
        "bawasisanya" to "carryOver",
        "ambangperingatan" to "alertThreshold",
        "jumlahsasaran" to "targetAmount",
        "tanggalsasaran" to "targetDate",
        "jumlahsekarang" to "currentAmount",
        "frekuensi" to "frequency",
        "jarak" to "interval",
        "harikustom" to "customDays",
        "eksekusiBerikutnya" to "nextExecutionDate",
        "autoeksekusi" to "isAutoExecute",
        "aktif" to "isEnabled",
        "namasumber" to "sourceName",
        "kolomtanggal" to "dateColumnIndex",
        "kolomnominal" to "amountColumnIndex",
        "kolomcatatan" to "noteColumnIndex",
        "kolomjenis" to "typeColumnIndex",
        "kolomkategori" to "categoryColumnIndex",
        "pembatas" to "delimiter",
        "kunci" to "key",
        "nilai" to "value"
    )

    private fun normalize(header: String): String {
        val clean = header.lowercase().replace("[\\s_-]".toRegex(), "")
        return synonyms[clean] ?: clean
    }

    // Melakukan inisialisasi skema (Bootstrap) & migrasi otomatis
    suspend fun bootstrapOrMigrate(spreadsheetId: String): String = withContext(Dispatchers.IO) {
        val sheets = sheetsClient.getSpreadsheetSheets(spreadsheetId)
        val existingTitles = sheets.map { it.second }
        val updates = JSONArray()

        var tabsCreated = 0
        var columnsAdded = 0
        val actions = mutableListOf<String>()

        // 1. Buat lembar kerja tab jika belum ada
        canonicalColumns.keys.forEach { tabName ->
            if (tabName !in existingTitles) {
                updates.put(JSONObject().apply {
                    put("addSheet", JSONObject().apply {
                        put("properties", JSONObject().apply {
                            put("title", tabName)
                        })
                    })
                })
                tabsCreated++
            }
        }

        // Tambah tab _Meta jika belum ada
        if ("_Meta" !in existingTitles) {
            updates.put(JSONObject().apply {
                put("addSheet", JSONObject().apply {
                    put("properties", JSONObject().apply {
                        put("title", "_Meta")
                    })
                })
            })
            tabsCreated++
        }

        if (updates.length() > 0) {
            sheetsClient.batchUpdate(spreadsheetId, updates)
        }

        // Ambil properti sheet terupdate
        val updatedSheets = sheetsClient.getSpreadsheetSheets(spreadsheetId)
        val sheetIdMap = updatedSheets.associate { it.second to it.first }

        val formatRequests = JSONArray()

        // 2. Periksa & Lengkapi kolom untuk masing-masing tab
        canonicalColumns.forEach { (tabName, canonical) ->
            val sheetId = sheetIdMap[tabName] ?: return@forEach
            val currentValues = sheetsClient.readValues(spreadsheetId, "$tabName!1:1")
            
            val currentHeaders = currentValues.firstOrNull() ?: emptyList()
            val normalizedCurrent = currentHeaders.map { normalize(it) }

            val missingCanonical = canonical.filter { normalize(it) !in normalizedCurrent }

            if (missingCanonical.isNotEmpty()) {
                val finalHeaders = currentHeaders.toMutableList()
                missingCanonical.forEach { finalHeaders.add(it) }
                columnsAdded += missingCanonical.size

                sheetsClient.writeValues(spreadsheetId, "$tabName!A1:${columnName(finalHeaders.size)}1", listOf(finalHeaders))
            }

            // Terapkan format header (Freeze & Bold row 1)
            formatRequests.put(JSONObject().apply {
                put("updateSheetProperties", JSONObject().apply {
                    put("properties", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("gridProperties", JSONObject().apply {
                            put("frozenRowCount", 1)
                        })
                    })
                    put("fields", "gridProperties.frozenRowCount")
                })
            })
            formatRequests.put(JSONObject().apply {
                put("repeatCell", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", 0)
                        put("endRowIndex", 1)
                    })
                    put("cell", JSONObject().apply {
                        put("userEnteredFormat", JSONObject().apply {
                            put("textFormat", JSONObject().apply {
                                put("bold", true)
                            })
                        })
                    })
                    put("fields", "userEnteredFormat.textFormat.bold")
                })
            })
        }

        // Tulis/update metadata tab _Meta
        val metaValues = listOf(
            listOf("Key", "Value", "UpdatedAt"),
            listOf("appName", "Sisa Uang", System.currentTimeMillis().toString()),
            listOf("schemaVersion", "4", System.currentTimeMillis().toString()),
            listOf("createdAt", System.currentTimeMillis().toString(), System.currentTimeMillis().toString())
        )
        sheetsClient.writeValues(spreadsheetId, "_Meta!A1:C4", metaValues)

        // Hapus Sheet1 bawaan kosong jika ada tab lain yang dibuat
        val sheet1Id = sheetIdMap["Sheet1"]
        if (sheet1Id != null && updatedSheets.size > 1) {
            val deleteRequest = JSONArray().apply {
                put(JSONObject().apply {
                    put("deleteSheet", JSONObject().apply {
                        put("sheetId", sheet1Id)
                    })
                })
            }
            sheetsClient.batchUpdate(spreadsheetId, deleteRequest)
            actions.add("Menghapus tab Sheet1 kosong")
        }

        if (formatRequests.length() > 0) {
            sheetsClient.batchUpdate(spreadsheetId, formatRequests)
        }

        if (tabsCreated > 0) actions.add("Membuat $tabsCreated tab baru")
        if (columnsAdded > 0) actions.add("Menambahkan $columnsAdded kolom kanonik")

        if (actions.isEmpty()) {
            "Struktur spreadsheet sesuai dan siap digunakan."
        } else {
            actions.joinToString(", ")
        }
    }

    // Melakukan sinkronisasi dua arah untuk seluruh entitas
    suspend fun syncAll(spreadsheetId: String) = withContext(Dispatchers.IO) {
        // Jalankan sinkronisasi sekuensial untuk semua entitas
        syncTable(spreadsheetId, "Accounts", { database.accountDao().getAllAccounts().map { AccountEntity.fromDomain(it.toDomain(BigDecimal.ZERO)) } }, { list ->
            list.forEach { database.accountDao().insertAccount(it) }
        }, ::mapToAccount, ::accountToMap)

        syncTable(spreadsheetId, "Categories", { database.categoryDao().getAllCategories() }, { list ->
            list.forEach { database.categoryDao().insertCategory(it) }
        }, ::mapToCategory, ::categoryToMap)

        syncTable(spreadsheetId, "Transactions", { database.transactionDao().getAllTransactions() }, { list ->
            list.forEach { database.transactionDao().insertTransaction(it) }
        }, ::mapToTransaction, ::transactionToMap)

        syncTable(spreadsheetId, "Holdings", { database.assetHoldingDao().getAllAssetHoldings() }, { list ->
            list.forEach { database.assetHoldingDao().insertAssetHolding(it) }
        }, ::mapToHolding, ::holdingToMap)

        syncTable(spreadsheetId, "Budgets", { database.budgetDao().getAllBudgets() }, { list ->
            list.forEach { database.budgetDao().insertBudget(it) }
        }, ::mapToBudget, ::budgetToMap)

        syncTable(spreadsheetId, "Goals", { database.goalDao().getAllGoals() }, { list ->
            list.forEach { database.goalDao().insertGoal(it) }
        }, ::mapToGoal, ::goalToMap)

        syncTable(spreadsheetId, "RecurringRules", { database.recurringRuleDao().getAllRecurringRules() }, { list ->
            list.forEach { database.recurringRuleDao().insertRecurringRule(it) }
        }, ::mapToRecurringRule, ::recurringRuleToMap)

        syncTable(spreadsheetId, "ParsingRules", { database.parsingRuleDao().getAllParsingRules() }, { list ->
            list.forEach { database.parsingRuleDao().insertParsingRule(it) }
        }, ::mapToParsingRule, ::parsingRuleToMap)

        syncTable(spreadsheetId, "ImportTemplates", { database.importTemplateDao().getAllImportTemplates() }, { list ->
            list.forEach { database.importTemplateDao().insertImportTemplate(it) }
        }, ::mapToImportTemplate, ::importTemplateToMap)

        syncTable(spreadsheetId, "Corrections", { database.parserCorrectionDao().getAllCorrections() }, { list ->
            list.forEach { database.parserCorrectionDao().insertCorrection(it) }
        }, ::mapToCorrection, ::correctionToMap)
    }

    private suspend fun <T> syncTable(
        spreadsheetId: String,
        tabName: String,
        getLocal: suspend () -> List<T>,
        saveLocal: suspend (List<T>) -> Unit,
        mapToEntity: (Map<String, String>) -> T,
        entityToMap: (T) -> Map<String, String>
    ) {
        val canonicalKeys = canonicalColumns[tabName] ?: return
        
        // 1. Baca data yang ada dari spreadsheet
        val currentRows = sheetsClient.readValues(spreadsheetId, "$tabName!A:Z")
        if (currentRows.isEmpty()) return

        val headers = currentRows[0]
        val normalizedHeaders = headers.map { normalize(it) }
        
        val sheetEntitiesMap = mutableMapOf<String, Map<String, String>>()
        for (i in 1 until currentRows.size) {
            val row = currentRows[i]
            val rowMap = mutableMapOf<String, String>()
            for (j in 0 until headers.size) {
                val valueStr = if (j < row.size) row[j] else ""
                val canonicalKey = normalizedHeaders[j]
                if (canonicalKey.isNotEmpty()) {
                    rowMap[canonicalKey] = valueStr
                }
            }
            val id = rowMap["id"]
            if (!id.isNullOrBlank()) {
                sheetEntitiesMap[id] = rowMap
            }
        }

        // 2. Baca data lokal
        val localList = getLocal()
        val localEntitiesMap = localList.associateBy {
            val map = entityToMap(it)
            map["id"] ?: ""
        }

        val toUpdateLocal = mutableListOf<T>()
        val toUpdateSheet = mutableMapOf<String, Map<String, String>>()

        // Satukan kedua map menggunakan ID
        val allIds = localEntitiesMap.keys + sheetEntitiesMap.keys

        allIds.forEach { id ->
            if (id.isBlank()) return@forEach
            val local = localEntitiesMap[id]
            val sheetMap = sheetEntitiesMap[id]

            if (local != null && sheetMap != null) {
                val localMap = entityToMap(local)
                val localUpdatedAt = localMap["updatedAt"]?.toLongOrNull() ?: 0L
                val sheetUpdatedAt = sheetMap["updatedAt"]?.toLongOrNull() ?: 0L

                if (localUpdatedAt > sheetUpdatedAt) {
                    // Update sheet dengan data lokal terbaru
                    val mergedMap = sheetMap.toMutableMap()
                    localMap.forEach { (k, v) -> mergedMap[k] = v }
                    toUpdateSheet[id] = mergedMap
                } else if (sheetUpdatedAt > localUpdatedAt) {
                    // Update lokal dengan data sheet terbaru
                    toUpdateLocal.add(mapToEntity(sheetMap))
                }
            } else if (local != null) {
                // Hanya ada secara lokal -> tambahkan ke sheet
                toUpdateSheet[id] = entityToMap(local)
            } else if (sheetMap != null) {
                // Hanya ada di sheet -> tambahkan secara lokal
                toUpdateLocal.add(mapToEntity(sheetMap))
            }
        }

        // 3. Simpan perubahan lokal jika ada
        if (toUpdateLocal.isNotEmpty()) {
            saveLocal(toUpdateLocal)
        }

        // 4. Update data di lembar kerja
        val newSheetRows = mutableListOf<List<String>>()
        newSheetRows.add(headers) // Header tetap baris pertama

        // Gabungkan seluruh data terkini yang siap ditulis
        val mergedList = mutableMapOf<String, Map<String, String>>()
        sheetEntitiesMap.forEach { (id, map) -> mergedList[id] = map }
        toUpdateSheet.forEach { (id, map) -> mergedList[id] = map }
        toUpdateLocal.forEach { entity ->
            val map = entityToMap(entity)
            val id = map["id"] ?: ""
            if (id.isNotBlank()) {
                mergedList[id] = map
            }
        }

        // Tulis ulang seluruh baris
        mergedList.values.forEach { rowMap ->
            val rowValues = headers.map { header ->
                val canonicalKey = normalize(header)
                rowMap[canonicalKey] ?: ""
            }
            newSheetRows.add(rowValues)
        }

        // Tulis kembali ke spreadsheet
        sheetsClient.writeValues(spreadsheetId, "$tabName!A1:${columnName(headers.size)}${newSheetRows.size}", newSheetRows)
    }

    private fun columnName(index: Int): String {
        var temp = index
        var colName = ""
        while (temp > 0) {
            val remain = (temp - 1) % 26
            colName = (('A'.code + remain).toChar()) + colName
            temp = (temp - 1) / 26
        }
        return colName.ifEmpty { "A" }
    }

    // Pemeta untuk AccountEntity
    private fun mapToAccount(m: Map<String, String>) = AccountEntity(
        id = m["id"] ?: "",
        name = m["name"] ?: "",
        type = m["type"] ?: "TUNAI",
        icon = m["icon"] ?: "wallet",
        color = m["color"] ?: "#4CAF50",
        currency = m["currency"] ?: "IDR",
        initialBalance = m["initialBalance"] ?: "0",
        isArchived = m["isArchived"] == "1",
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        deleted = m["deleted"] == "1"
    )

    private fun accountToMap(e: AccountEntity) = mapOf(
        "id" to e.id,
        "name" to e.name,
        "type" to e.type,
        "icon" to e.icon,
        "color" to e.color,
        "currency" to e.currency,
        "initialBalance" to e.initialBalance,
        "isArchived" to if (e.isArchived) "1" else "0",
        "updatedAt" to e.updatedAt.toString(),
        "deleted" to if (e.deleted) "1" else "0"
    )

    // Pemeta untuk CategoryEntity
    private fun mapToCategory(m: Map<String, String>) = CategoryEntity(
        id = m["id"] ?: "",
        name = m["name"] ?: "",
        type = m["type"] ?: "EXPENSE",
        icon = m["icon"] ?: "category",
        color = m["color"] ?: "#9C27B0",
        isFavorite = m["isFavorite"] == "1",
        sortOrder = m["order"]?.toIntOrNull() ?: 0,
        parentId = m["parentId"]?.takeIf { it != "null" && it.isNotBlank() },
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        deleted = m["deleted"] == "1"
    )

    private fun categoryToMap(e: CategoryEntity) = mapOf(
        "id" to e.id,
        "name" to e.name,
        "type" to e.type,
        "icon" to e.icon,
        "color" to e.color,
        "isFavorite" to if (e.isFavorite) "1" else "0",
        "order" to e.sortOrder.toString(),
        "parentId" to (e.parentId ?: ""),
        "updatedAt" to e.updatedAt.toString(),
        "deleted" to if (e.deleted) "1" else "0"
    )

    // Pemeta untuk TransactionEntity
    private fun mapToTransaction(m: Map<String, String>) = TransactionEntity(
        id = m["id"] ?: "",
        amount = m["amount"] ?: "0",
        type = m["type"] ?: "PENGELUARAN",
        categoryId = m["categoryId"]?.takeIf { it != "null" && it.isNotBlank() },
        subCategoryId = m["subCategoryId"]?.takeIf { it != "null" && it.isNotBlank() },
        accountId = m["accountId"] ?: "",
        toAccountId = m["toAccountId"]?.takeIf { it != "null" && it.isNotBlank() },
        adminFee = m["adminFee"]?.takeIf { it != "null" && it.isNotBlank() },
        note = m["note"] ?: "",
        dateTime = m["dateTime"]?.toLongOrNull() ?: System.currentTimeMillis(),
        attachmentPath = m["attachmentPath"]?.takeIf { it != "null" && it.isNotBlank() },
        tags = m["tags"] ?: "",
        goalId = m["goalId"]?.takeIf { it != "null" && it.isNotBlank() },
        recurringRuleId = m["recurringRuleId"]?.takeIf { it != "null" && it.isNotBlank() },
        sourceInput = m["sourceInput"] ?: "MANUAL",
        transactionHash = m["transactionHash"]?.takeIf { it != "null" && it.isNotBlank() },
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        deleted = m["deleted"] == "1"
    )

    private fun transactionToMap(e: TransactionEntity) = mapOf(
        "id" to e.id,
        "amount" to e.amount,
        "type" to e.type,
        "categoryId" to (e.categoryId ?: ""),
        "subCategoryId" to (e.subCategoryId ?: ""),
        "accountId" to e.accountId,
        "toAccountId" to (e.toAccountId ?: ""),
        "adminFee" to (e.adminFee ?: ""),
        "note" to e.note,
        "dateTime" to e.dateTime.toString(),
        "attachmentPath" to (e.attachmentPath ?: ""),
        "tags" to e.tags,
        "goalId" to (e.goalId ?: ""),
        "recurringRuleId" to (e.recurringRuleId ?: ""),
        "sourceInput" to e.sourceInput,
        "transactionHash" to (e.transactionHash ?: ""),
        "updatedAt" to e.updatedAt.toString(),
        "deleted" to if (e.deleted) "1" else "0"
    )

    // Pemeta untuk AssetHoldingEntity
    private fun mapToHolding(m: Map<String, String>) = AssetHoldingEntity(
        id = m["id"] ?: "",
        name = m["name"] ?: "",
        category = m["category"] ?: "SAHAM",
        quantity = m["quantity"] ?: "0",
        buyPrice = m["buyPrice"] ?: "0",
        currentPrice = m["currentPrice"] ?: "0",
        nominal = m["nominal"] ?: "0",
        interestRate = m["interestRate"] ?: "0",
        maturityDate = m["maturityDate"]?.toLongOrNull(),
        cicilan = m["cicilan"]?.takeIf { it != "null" && it.isNotBlank() },
        isCompleted = m["isCompleted"] == "1",
        linkedAccountId = m["linkedAccountId"]?.takeIf { it != "null" && it.isNotBlank() },
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        deleted = m["deleted"] == "1"
    )

    private fun holdingToMap(e: AssetHoldingEntity) = mapOf(
        "id" to e.id,
        "name" to e.name,
        "category" to e.category,
        "quantity" to e.quantity,
        "buyPrice" to e.buyPrice,
        "currentPrice" to e.currentPrice,
        "nominal" to e.nominal,
        "interestRate" to e.interestRate,
        "maturityDate" to (e.maturityDate?.toString() ?: ""),
        "cicilan" to (e.cicilan ?: ""),
        "isCompleted" to if (e.isCompleted) "1" else "0",
        "linkedAccountId" to (e.linkedAccountId ?: ""),
        "updatedAt" to e.updatedAt.toString(),
        "deleted" to if (e.deleted) "1" else "0"
    )

    // Pemeta untuk BudgetEntity
    private fun mapToBudget(m: Map<String, String>) = BudgetEntity(
        id = m["id"] ?: "",
        name = m["name"] ?: "",
        categoryId = m["categoryId"]?.takeIf { it != "null" && it.isNotBlank() },
        amount = m["amount"] ?: "0",
        period = m["period"] ?: "BULANAN",
        customStartDate = m["customStartDate"]?.toLongOrNull(),
        customEndDate = m["customEndDate"]?.toLongOrNull(),
        carryOver = m["carryOver"] == "1",
        alertThreshold = m["alertThreshold"]?.toFloatOrNull() ?: 0.8f,
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        deleted = m["deleted"] == "1"
    )

    private fun budgetToMap(e: BudgetEntity) = mapOf(
        "id" to e.id,
        "name" to e.name,
        "categoryId" to (e.categoryId ?: ""),
        "amount" to e.amount,
        "period" to e.period,
        "customStartDate" to (e.customStartDate?.toString() ?: ""),
        "customEndDate" to (e.customEndDate?.toString() ?: ""),
        "carryOver" to if (e.carryOver) "1" else "0",
        "alertThreshold" to e.alertThreshold.toString(),
        "updatedAt" to e.updatedAt.toString(),
        "deleted" to if (e.deleted) "1" else "0"
    )

    // Pemeta untuk GoalEntity
    private fun mapToGoal(m: Map<String, String>) = GoalEntity(
        id = m["id"] ?: "",
        name = m["name"] ?: "",
        targetAmount = m["targetAmount"] ?: "0",
        targetDate = m["targetDate"]?.toLongOrNull(),
        linkedAccountId = m["linkedAccountId"]?.takeIf { it != "null" && it.isNotBlank() },
        currentAmount = m["currentAmount"] ?: "0",
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        deleted = m["deleted"] == "1"
    )

    private fun goalToMap(e: GoalEntity) = mapOf(
        "id" to e.id,
        "name" to e.name,
        "targetAmount" to e.targetAmount,
        "targetDate" to (e.targetDate?.toString() ?: ""),
        "linkedAccountId" to (e.linkedAccountId ?: ""),
        "currentAmount" to e.currentAmount,
        "updatedAt" to e.updatedAt.toString(),
        "deleted" to if (e.deleted) "1" else "0"
    )

    // Pemeta untuk RecurringRuleEntity
    private fun mapToRecurringRule(m: Map<String, String>) = RecurringRuleEntity(
        id = m["id"] ?: "",
        name = m["name"] ?: "",
        amount = m["amount"] ?: "0",
        type = m["type"] ?: "PENGELUARAN",
        categoryId = m["categoryId"]?.takeIf { it != "null" && it.isNotBlank() },
        accountId = m["accountId"] ?: "",
        toAccountId = m["toAccountId"]?.takeIf { it != "null" && it.isNotBlank() },
        note = m["note"] ?: "",
        frequency = m["frequency"] ?: "MONTHLY",
        interval = m["interval"]?.toIntOrNull() ?: 1,
        customDays = m["customDays"]?.toIntOrNull(),
        nextExecutionDate = m["nextExecutionDate"]?.toLongOrNull() ?: System.currentTimeMillis(),
        isAutoExecute = m["isAutoExecute"] == "1",
        isEnabled = m["isEnabled"] == "1",
        goalId = m["goalId"]?.takeIf { it != "null" && it.isNotBlank() },
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        deleted = m["deleted"] == "1"
    )

    private fun recurringRuleToMap(e: RecurringRuleEntity) = mapOf(
        "id" to e.id,
        "name" to e.name,
        "amount" to e.amount,
        "type" to e.type,
        "categoryId" to (e.categoryId ?: ""),
        "accountId" to e.accountId,
        "toAccountId" to (e.toAccountId ?: ""),
        "note" to e.note,
        "frequency" to e.frequency,
        "interval" to e.interval.toString(),
        "customDays" to (e.customDays?.toString() ?: ""),
        "nextExecutionDate" to e.nextExecutionDate.toString(),
        "isAutoExecute" to if (e.isAutoExecute) "1" else "0",
        "isEnabled" to if (e.isEnabled) "1" else "0",
        "goalId" to (e.goalId ?: ""),
        "updatedAt" to e.updatedAt.toString(),
        "deleted" to if (e.deleted) "1" else "0"
    )

    // Pemeta untuk ParsingRuleEntity
    private fun mapToParsingRule(m: Map<String, String>) = ParsingRuleEntity(
        id = m["id"] ?: "",
        appPackage = m["appPackage"] ?: "",
        name = m["name"] ?: "",
        regexPattern = m["regexPattern"] ?: "",
        type = m["type"] ?: "PENGELUARAN",
        accountId = m["accountId"] ?: "",
        categoryId = m["categoryId"]?.takeIf { it != "null" && it.isNotBlank() },
        amountGroupIndex = m["amountGroupIndex"]?.toIntOrNull() ?: 1,
        noteGroupIndex = m["noteGroupIndex"]?.toIntOrNull(),
        deleted = m["deleted"] == "1",
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
    )

    private fun parsingRuleToMap(e: ParsingRuleEntity) = mapOf(
        "id" to e.id,
        "appPackage" to e.appPackage,
        "name" to e.name,
        "regexPattern" to e.regexPattern,
        "type" to e.type,
        "accountId" to e.accountId,
        "categoryId" to (e.categoryId ?: ""),
        "amountGroupIndex" to e.amountGroupIndex.toString(),
        "noteGroupIndex" to (e.noteGroupIndex?.toString() ?: ""),
        "deleted" to if (e.deleted) "1" else "0",
        "updatedAt" to e.updatedAt.toString()
    )

    // Pemeta untuk ImportTemplateEntity
    private fun mapToImportTemplate(m: Map<String, String>) = ImportTemplateEntity(
        id = m["id"] ?: "",
        sourceName = m["sourceName"] ?: "",
        dateColumnIndex = m["dateColumnIndex"]?.toIntOrNull() ?: 0,
        amountColumnIndex = m["amountColumnIndex"]?.toIntOrNull() ?: 1,
        noteColumnIndex = m["noteColumnIndex"]?.toIntOrNull() ?: 2,
        typeColumnIndex = m["typeColumnIndex"]?.toIntOrNull(),
        categoryColumnIndex = m["categoryColumnIndex"]?.toIntOrNull(),
        delimiter = m["delimiter"] ?: ",",
        deleted = m["deleted"] == "1",
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
    )

    private fun importTemplateToMap(e: ImportTemplateEntity) = mapOf(
        "id" to e.id,
        "sourceName" to e.sourceName,
        "dateColumnIndex" to e.dateColumnIndex.toString(),
        "amountColumnIndex" to e.amountColumnIndex.toString(),
        "noteColumnIndex" to e.noteColumnIndex.toString(),
        "typeColumnIndex" to (e.typeColumnIndex?.toString() ?: ""),
        "categoryColumnIndex" to (e.categoryColumnIndex?.toString() ?: ""),
        "delimiter" to e.delimiter,
        "deleted" to if (e.deleted) "1" else "0",
        "updatedAt" to e.updatedAt.toString()
    )

    // Pemeta untuk ParserCorrectionEntity
    private fun mapToCorrection(m: Map<String, String>) = ParserCorrectionEntity(
        id = m["id"] ?: "",
        keyword = m["keyword"] ?: "",
        mappedCategoryId = m["mappedCategoryId"]?.takeIf { it != "null" && it.isNotBlank() },
        mappedAccountId = m["mappedAccountId"]?.takeIf { it != "null" && it.isNotBlank() },
        deleted = m["deleted"] == "1",
        updatedAt = m["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
    )

    private fun correctionToMap(e: ParserCorrectionEntity) = mapOf(
        "id" to e.id,
        "keyword" to e.keyword,
        "mappedCategoryId" to (e.mappedCategoryId ?: ""),
        "mappedAccountId" to (e.mappedAccountId ?: ""),
        "deleted" to if (e.deleted) "1" else "0",
        "updatedAt" to e.updatedAt.toString()
    )
}
