package com.example.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.DompetKuApplication
import com.example.domain.model.*
import com.example.domain.repository.*
import com.example.domain.utils.DatabaseSeeder
import com.example.domain.utils.FinanceCalculator
import com.example.domain.utils.MonthlyFlowResult
import com.example.security.SecurityHelper
import com.example.data.sync.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar

class MainViewModel(
    application: Application,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val preferenceRepository: PreferenceRepository,
    private val assetHoldingRepository: AssetHoldingRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val draftTransactionRepository: DraftTransactionRepository,
    private val parsingRuleRepository: ParsingRuleRepository,
    private val importTemplateRepository: ImportTemplateRepository,
    private val parserCorrectionRepository: ParserCorrectionRepository,
    private val syncMetaRepository: SyncMetaRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val googleSheetsSyncClient: GoogleSheetsSyncClient,
    private val cloudSyncManager: CloudSyncManager,
    private val driveBackupManager: DriveBackupManager
) : AndroidViewModel(application) {

    // Preferences & Settings
    val themeState: StateFlow<String> = preferenceRepository.themeFlow
    val languageState: StateFlow<String> = preferenceRepository.languageFlow
    val currencyState: StateFlow<String> = preferenceRepository.currencyFlow
    val isBiometricEnabled: StateFlow<Boolean> = preferenceRepository.biometricFlow
    val isOnboardingCompleted: StateFlow<Boolean> = preferenceRepository.onboardingCompletedFlow
    val isDashboardTourCompleted: StateFlow<Boolean> = preferenceRepository.dashboardTourCompletedFlow
    val securePinHash: StateFlow<String?> = preferenceRepository.pinFlow
    val notificationListenerEnabled: StateFlow<Boolean> = preferenceRepository.notificationListenerEnabledFlow
    val isDatabaseEncrypted: Boolean
        get() = com.example.data.database.AppDatabase.isEncrypted

    // Lock & Security active screen state
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun lockApp() {
        if (securePinHash.value != null) {
            _isLocked.value = true
        }
    }

    fun unlockApp() {
        _isLocked.value = false
    }

    // Core Data flows
    val accountsState: StateFlow<List<Account>> = accountRepository.getAccountsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoriesState: StateFlow<List<Category>> = categoryRepository.getCategoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsState: StateFlow<List<Transaction>> = transactionRepository.getTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 2 Data flows
    val assetHoldingsState: StateFlow<List<AssetHolding>> = assetHoldingRepository.getAssetHoldingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetsState: StateFlow<List<Budget>> = budgetRepository.getBudgetsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goalsState: StateFlow<List<Goal>> = goalRepository.getGoalsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringRulesState: StateFlow<List<RecurringRule>> = recurringRuleRepository.getRecurringRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftsState: StateFlow<List<DraftTransaction>> = draftTransactionRepository.getAllUnconfirmedDraftsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parsingRulesState: StateFlow<List<ParsingRule>> = parsingRuleRepository.getAllParsingRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val importTemplatesState: StateFlow<List<ImportTemplate>> = importTemplateRepository.getAllImportTemplatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncMetaState: StateFlow<SyncMeta?> = syncMetaRepository.getSyncMetaFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Financial calculations
    val netWorthState: StateFlow<BigDecimal> = combine(
        accountsState,
        assetHoldingsState
    ) { accounts, holdings -> 
        FinanceCalculator.calculateNetWorth(accounts, holdings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    val safeToSpendState: StateFlow<BigDecimal> = combine(
        budgetsState,
        transactionsState
    ) { budgets, transactions ->
        FinanceCalculator.calculateDailySafeToSpend(budgets, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    val forecastState: StateFlow<com.example.domain.utils.ForecastResult> = combine(
        accountsState,
        recurringRulesState,
        transactionsState
    ) { accounts, rules, transactions ->
        FinanceCalculator.calculateCashFlowForecast(accounts, rules, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.domain.utils.ForecastResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO))

    private val _monthlyFlowState = MutableStateFlow(MonthlyFlowResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false))
    val monthlyFlowState: StateFlow<MonthlyFlowResult> = _monthlyFlowState.asStateFlow()

    val financialHealthState: StateFlow<com.example.domain.utils.FinancialHealthResult> = combine(
        accountsState,
        transactionsState,
        monthlyFlowState
    ) { accounts, transactions, monthlyFlow ->
        FinanceCalculator.calculateFinancialHealth(accounts, transactions, monthlyFlow)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.domain.utils.FinancialHealthResult(0, "WASPADA", 0f, 0f, ""))

    val detectedRecurringSuggestionsState: StateFlow<List<com.example.domain.utils.RecurringSuggestion>> = combine(
        transactionsState,
        accountsState
    ) { transactions, _ ->
        FinanceCalculator.detectRecurringSuggestions(transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _correctionsState = MutableStateFlow<List<ParserCorrection>>(emptyList())
    val correctionsState: StateFlow<List<ParserCorrection>> = _correctionsState.asStateFlow()

    init {
        // Initialize default categories automatically
        viewModelScope.launch {
            DatabaseSeeder.seedDefaultCategories(categoryRepository)
            _correctionsState.value = parserCorrectionRepository.getAllCorrections()
            
            // Check if PIN lock is established
            val hasPin = preferenceRepository.pinFlow.first()
            val completedOnboarding = preferenceRepository.onboardingCompletedFlow.first()
            if (hasPin != null && completedOnboarding) {
                _isLocked.value = true
            }
        }

        // Fetch exchange rate USD to IDR dynamically
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = java.net.URL("https://open.er-api.com/v6/latest/USD").openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.inputStream.bufferedReader().use { reader ->
                    val response = reader.readText()
                    val matcher = java.util.regex.Pattern.compile("\"IDR\"\\s*:\\s*([0-9.]+)").matcher(response)
                    if (matcher.find()) {
                        val rateStr = matcher.group(1)
                        if (rateStr != null) {
                            val rate = BigDecimal(rateStr)
                            if (rate > BigDecimal.ZERO) {
                                com.example.domain.utils.LocalizationUtils.usdToIdrRate = rate
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Recalculate monthly flows reactively when transactions change
        viewModelScope.launch {
            transactionsState.collect { txs ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val monthStart = cal.timeInMillis

                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val monthEnd = cal.timeInMillis

                _monthlyFlowState.value = FinanceCalculator.calculateMonthlyFlows(txs, monthStart, monthEnd)
            }
        }
    }

    // Settings actions
    fun setAppTheme(theme: String) = viewModelScope.launch {
        preferenceRepository.setTheme(theme)
    }

    fun setNotificationListenerEnabled(enabled: Boolean) = viewModelScope.launch {
        preferenceRepository.setNotificationListenerEnabled(enabled)
    }

    fun isNotificationAppEnabled(packageName: String): Boolean {
        return preferenceRepository.isNotificationAppEnabled(packageName)
    }

    fun setNotificationAppEnabled(packageName: String, enabled: Boolean) = viewModelScope.launch {
        preferenceRepository.setNotificationAppEnabled(packageName, enabled)
    }

    fun setAppLanguage(lang: String) = viewModelScope.launch {
        preferenceRepository.setLanguage(lang)
    }

    fun setAppCurrency(currency: String) = viewModelScope.launch {
        preferenceRepository.setCurrency(currency)
    }

    fun createOrUpdatePin(pin: String?) = viewModelScope.launch {
        val hashed = pin?.let { SecurityHelper.hashPin(it) }
        preferenceRepository.setPin(hashed)
    }

    fun setBiometricLock(enabled: Boolean) = viewModelScope.launch {
        preferenceRepository.setBiometricEnabled(enabled)
    }

    fun completeOnboarding(seedDemo: Boolean) = viewModelScope.launch {
        if (seedDemo) {
            DatabaseSeeder.seedSampleData(accountRepository, categoryRepository, transactionRepository)
        } else {
            DatabaseSeeder.seedDefaultCategories(categoryRepository)
        }
        preferenceRepository.setOnboardingCompleted(true)
        preferenceRepository.setDashboardTourCompleted(false) // Trigger tour on next start
    }

    fun completeDashboardTour() = viewModelScope.launch {
        preferenceRepository.setDashboardTourCompleted(true)
    }

    fun restartDashboardTour() = viewModelScope.launch {
        preferenceRepository.setDashboardTourCompleted(false)
    }

    fun seedDemoData() = viewModelScope.launch {
        DatabaseSeeder.seedSampleData(accountRepository, categoryRepository, transactionRepository)
    }

    fun clearAllUserData() = viewModelScope.launch {
        DatabaseSeeder.clearAllFinancialData(accountRepository, categoryRepository, transactionRepository)
        preferenceRepository.setPin(null)
        preferenceRepository.setBiometricEnabled(false)
        preferenceRepository.setOnboardingCompleted(false)
        preferenceRepository.setDashboardTourCompleted(false)
        _isLocked.value = false
    }

    // DB Operations called securely from Screens
    fun insertAccount(account: Account) = viewModelScope.launch {
        accountRepository.insertAccount(account)
    }

    fun deleteAccount(accountId: String) = viewModelScope.launch {
        accountRepository.softDeleteAccount(accountId)
    }

    fun archiveAccount(accountId: String, archive: Boolean) = viewModelScope.launch {
        accountRepository.archiveAccount(accountId, archive)
    }

    fun insertTransaction(transaction: Transaction) = viewModelScope.launch {
        transactionRepository.insertTransaction(transaction)
        checkBudgetsAndNotifyRealTime(transaction)
    }

    private fun checkBudgetsAndNotifyRealTime(tx: Transaction) {
        if (tx.type != TransactionType.PENGELUARAN) return
        val currentBudgets = budgetsState.value
        val allTxs = transactionsState.value + tx
        
        for (budget in currentBudgets) {
            if (budget.deleted) continue
            if (budget.categoryId == null || budget.categoryId == tx.categoryId) {
                val currentRange = getPeriodDateRangeForBudget(budget.period, budget.customStartDate, budget.customEndDate)
                val previousRange = getPreviousPeriodDateRangeForBudget(budget.period, budget.customStartDate, budget.customEndDate)
                
                val currentSpent = allTxs.filter {
                    !it.deleted &&
                    it.dateTime in currentRange.first..currentRange.second &&
                    it.type == TransactionType.PENGELUARAN &&
                    (budget.categoryId == null || it.categoryId == budget.categoryId)
                }.fold(BigDecimal.ZERO) { sum, t -> sum.add(t.amount) }
                
                var availableBudget = budget.amount
                if (budget.carryOver) {
                    val prevSpent = allTxs.filter {
                        !it.deleted &&
                        it.dateTime in previousRange.first..previousRange.second &&
                        it.type == TransactionType.PENGELUARAN &&
                        (budget.categoryId == null || it.categoryId == budget.categoryId)
                    }.fold(BigDecimal.ZERO) { sum, t -> sum.add(t.amount) }
                    val prevRemaining = budget.amount.subtract(prevSpent)
                    if (prevRemaining > BigDecimal.ZERO) {
                        availableBudget = availableBudget.add(prevRemaining)
                    }
                }
                
                val format = java.text.NumberFormat.getNumberInstance(java.util.Locale("in", "ID"))
                
                if (currentSpent >= availableBudget) {
                    com.example.domain.utils.NotificationHelper.showNotification(
                        getApplication(),
                        budget.id.hashCode() + 1,
                        "Anggaran Terlampaui!",
                        "Anggaran \"${budget.name}\" terlampaui. Terpakai: Rp ${format.format(currentSpent)} / Limit: Rp ${format.format(availableBudget)}."
                    )
                } else if (currentSpent >= availableBudget.multiply(BigDecimal("0.8"))) {
                    com.example.domain.utils.NotificationHelper.showNotification(
                        getApplication(),
                        budget.id.hashCode() + 2,
                        "Anggaran Hampir Habis",
                        "Anggaran \"${budget.name}\" telah terpakai 80%+. Terpakai: Rp ${format.format(currentSpent)} / Limit: Rp ${format.format(availableBudget)}."
                    )
                }
            }
        }
    }

    private fun getPeriodDateRangeForBudget(period: BudgetPeriod, customStart: Long?, customEnd: Long?): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (period) {
            BudgetPeriod.BULANAN -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.MINGGUAN -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.KUSTOM -> {
                Pair(customStart ?: 0L, customEnd ?: System.currentTimeMillis())
            }
        }
    }

    private fun getPreviousPeriodDateRangeForBudget(period: BudgetPeriod, customStart: Long?, customEnd: Long?): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (period) {
            BudgetPeriod.BULANAN -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.MINGGUAN -> {
                cal.add(Calendar.WEEK_OF_YEAR, -1)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            else -> {
                val duration = (customEnd ?: 0L) - (customStart ?: 0L)
                Pair((customStart ?: 0L) - duration, (customStart ?: 0L) - 1000L)
            }
        }
    }

    fun deleteTransaction(transactionId: String) = viewModelScope.launch {
        transactionRepository.softDeleteTransaction(transactionId)
    }

    fun insertCategory(category: Category) = viewModelScope.launch {
        categoryRepository.insertCategory(category)
    }

    fun deleteCategory(categoryId: String) = viewModelScope.launch {
        categoryRepository.softDeleteCategory(categoryId)
    }

    // Phase 2 DB Operations
    fun insertAssetHolding(holding: AssetHolding) = viewModelScope.launch {
        assetHoldingRepository.insertAssetHolding(holding)
    }

    fun deleteAssetHolding(id: String) = viewModelScope.launch {
        assetHoldingRepository.softDeleteAssetHolding(id)
    }

    fun insertBudget(budget: Budget) = viewModelScope.launch {
        budgetRepository.insertBudget(budget)
    }

    fun deleteBudget(id: String) = viewModelScope.launch {
        budgetRepository.softDeleteBudget(id)
    }

    fun insertGoal(goal: Goal) = viewModelScope.launch {
        goalRepository.insertGoal(goal)
    }

    fun deleteGoal(id: String) = viewModelScope.launch {
        goalRepository.softDeleteGoal(id)
    }

    fun addGoalContribution(goalId: String, amount: BigDecimal, sourceAccountId: String?) = viewModelScope.launch {
        val goal = goalRepository.getGoalById(goalId) ?: return@launch
        val updatedGoal = goal.copy(
            currentAmount = goal.currentAmount.add(amount),
            updatedAt = System.currentTimeMillis()
        )
        goalRepository.insertGoal(updatedGoal)

        if (sourceAccountId != null) {
            val tx = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                amount = amount,
                type = TransactionType.PENGELUARAN,
                categoryId = null,
                accountId = sourceAccountId,
                note = "Setoran target: ${goal.name}",
                dateTime = System.currentTimeMillis(),
                goalId = goalId,
                updatedAt = System.currentTimeMillis()
            )
            transactionRepository.insertTransaction(tx)
        }
    }

    fun insertRecurringRule(rule: RecurringRule) = viewModelScope.launch {
        recurringRuleRepository.insertRecurringRule(rule)
    }

    fun deleteRecurringRule(id: String) = viewModelScope.launch {
        recurringRuleRepository.softDeleteRecurringRule(id)
    }

    fun executeRecurringRuleManual(ruleId: String) = viewModelScope.launch {
        val rule = recurringRuleRepository.getRecurringRuleById(ruleId) ?: return@launch
        val tx = Transaction(
            id = java.util.UUID.randomUUID().toString(),
            amount = rule.amount,
            type = rule.type,
            categoryId = rule.categoryId,
            accountId = rule.accountId,
            toAccountId = rule.toAccountId,
            note = rule.note.ifEmpty { "Rutin: ${rule.name}" },
            dateTime = System.currentTimeMillis(),
            recurringRuleId = rule.id,
            goalId = rule.goalId,
            updatedAt = System.currentTimeMillis()
        )
        transactionRepository.insertTransaction(tx)

        if (rule.goalId != null) {
            val goal = goalRepository.getGoalById(rule.goalId)
            if (goal != null) {
                goalRepository.insertGoal(goal.copy(
                    currentAmount = goal.currentAmount.add(rule.amount),
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }

        val nextDate = calculateNextOccurrence(rule.nextExecutionDate, rule.frequency, rule.interval, rule.customDays)
        recurringRuleRepository.insertRecurringRule(rule.copy(
            nextExecutionDate = nextDate,
            updatedAt = System.currentTimeMillis()
        ))
    }

    private fun calculateNextOccurrence(current: Long, frequency: RecurringFrequency, interval: Int, customDays: Int?): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = current
        when (frequency) {
            RecurringFrequency.DAILY -> cal.add(Calendar.DAY_OF_YEAR, interval)
            RecurringFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, interval)
            RecurringFrequency.MONTHLY -> cal.add(Calendar.MONTH, interval)
            RecurringFrequency.CUSTOM -> {
                val days = customDays ?: 1
                cal.add(Calendar.DAY_OF_YEAR, days * interval)
            }
        }
        return cal.timeInMillis
    }

    // Phase 3 Operations
    fun insertDraft(draft: DraftTransaction) = viewModelScope.launch {
        draftTransactionRepository.insertDraft(draft)
    }

    fun deleteDraft(id: String) = viewModelScope.launch {
        draftTransactionRepository.deleteDraft(id)
    }

    fun confirmDraft(draftId: String, finalTransaction: Transaction) = viewModelScope.launch {
        insertTransaction(finalTransaction)
        draftTransactionRepository.confirmDraft(draftId)
    }

    fun bulkConfirmDrafts(draftsWithTransactions: List<Pair<String, Transaction>>) = viewModelScope.launch {
        draftsWithTransactions.forEach { (draftId, tx) ->
            insertTransaction(tx)
            draftTransactionRepository.confirmDraft(draftId)
        }
    }

    fun bulkDeleteDrafts(draftIds: List<String>) = viewModelScope.launch {
        draftIds.forEach { id ->
            draftTransactionRepository.deleteDraft(id)
        }
    }

    fun insertParsingRule(rule: ParsingRule) = viewModelScope.launch {
        parsingRuleRepository.insertParsingRule(rule)
    }

    fun deleteParsingRule(id: String) = viewModelScope.launch {
        parsingRuleRepository.softDeleteParsingRule(id)
    }

    fun insertImportTemplate(template: ImportTemplate) = viewModelScope.launch {
        importTemplateRepository.insertImportTemplate(template)
    }

    fun deleteImportTemplate(id: String) = viewModelScope.launch {
        importTemplateRepository.softDeleteImportTemplate(id)
    }

    fun learnCorrection(keyword: String, categoryId: String?, accountId: String?) = viewModelScope.launch {
        if (keyword.isBlank()) return@launch
        val normalizedKeyword = keyword.trim().lowercase()
        val existing = parserCorrectionRepository.getCorrectionByKeyword(normalizedKeyword)
        val correction = ParserCorrection(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            keyword = normalizedKeyword,
            mappedCategoryId = categoryId,
            mappedAccountId = accountId,
            updatedAt = System.currentTimeMillis()
        )
        parserCorrectionRepository.insertCorrection(correction)
        _correctionsState.value = parserCorrectionRepository.getAllCorrections()
    }

    suspend fun getCorrectionForKeyword(keyword: String): ParserCorrection? {
        val normalizedKeyword = keyword.trim().lowercase()
        return parserCorrectionRepository.getCorrectionByKeyword(normalizedKeyword)
    }

    // Sync Metadata Flow
    val syncMetaFlow: StateFlow<SyncMeta?> = syncMetaRepository.getSyncMetaFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun isGoogleSignIn(): Boolean {
        return googleAuthManager.isSignIn()
    }

    fun getGoogleEmail(): String? {
        return googleAuthManager.getSignedInAccount()?.email
    }

    fun getGoogleSignInIntent() = googleAuthManager.getSignInIntent()

    fun updateSpreadsheetId(id: String) {
        viewModelScope.launch {
            val current = syncMetaRepository.getSyncMeta()
            val nextMeta = SyncMeta(
                googleSpreadsheetId = id.trim(),
                schemaVersion = current?.schemaVersion ?: 4,
                lastSyncAt = current?.lastSyncAt ?: 0L,
                status = current?.status ?: "IDLE"
            )
            syncMetaRepository.saveSyncMeta(nextMeta)
        }
    }

    private val _syncRunningState = MutableStateFlow<String?>(null)
    val syncRunningState: StateFlow<String?> = _syncRunningState.asStateFlow()

    fun runManualSync() {
        viewModelScope.launch {
            val currentMeta = syncMetaRepository.getSyncMeta()
            val spreadsheetId = currentMeta?.googleSpreadsheetId
            if (spreadsheetId.isNullOrBlank()) {
                _syncRunningState.value = "Error: ID Spreadsheet belum diatur."
                return@launch
            }

            _syncRunningState.value = "Menghubungkan ke Google Sheets..."
            try {
                syncMetaRepository.saveSyncMeta(currentMeta.copy(status = "RUNNING"))

                _syncRunningState.value = "Memvalidasi & Migrasi Struktur Skema..."
                val schemaAction = cloudSyncManager.bootstrapOrMigrate(spreadsheetId)

                _syncRunningState.value = "Menyinkronkan data keuangan (dua arah)..."
                cloudSyncManager.syncAll(spreadsheetId)

                syncMetaRepository.saveSyncMeta(
                    SyncMeta(
                        googleSpreadsheetId = spreadsheetId,
                        schemaVersion = 4,
                        lastSyncAt = System.currentTimeMillis(),
                        status = "SUCCESS"
                    )
                )
                _syncRunningState.value = "Berhasil disinkronkan! ($schemaAction)"
            } catch (e: Exception) {
                e.printStackTrace()
                syncMetaRepository.saveSyncMeta(
                    SyncMeta(
                        googleSpreadsheetId = spreadsheetId,
                        schemaVersion = currentMeta.schemaVersion,
                        lastSyncAt = currentMeta.lastSyncAt,
                        status = "FAILED"
                    )
                )
                _syncRunningState.value = "Gagal sinkronisasi: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun clearSyncRunningMessage() {
        _syncRunningState.value = null
    }

    fun createSpreadsheet(title: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val newId = googleSheetsSyncClient.createSpreadsheet(title)
                onResult(newId)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    fun runBackupToDrive(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                driveBackupManager.backupToDrive()
                onResult(true, "Backup berhasil disimpan di Google Drive!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Gagal backup: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun runRestoreFromDrive(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                driveBackupManager.restoreFromDrive()
                onResult(true, "Data keuangan berhasil dipulihkan dari Google Drive!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Gagal memulihkan data: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    suspend fun exportTransactionsToCsv(): String {
        // Mengekspor semua transaksi aktif (tidak terhapus) ke format CSV
        val header = "Tanggal,Tipe,Nominal,AkunSumber,AkunTujuan,Kategori,BiayaAdmin,Catatan,Tag\n"
        val activeTransactions = transactionRepository.getTransactions().filter { !it.deleted }
        val accountsMap = accountRepository.getAccounts().associateBy { it.id }
        val categoriesMap = categoryRepository.getCategories().associateBy { it.id }
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)

        val csvBuilder = StringBuilder(header)
        activeTransactions.forEach { tx ->
            val dateStr = dateFormat.format(java.util.Date(tx.dateTime))
            val typeStr = tx.type.name
            val amountStr = tx.amount.toPlainString()
            val accountSrc = accountsMap[tx.accountId]?.name ?: ""
            val accountDst = tx.toAccountId?.let { accountsMap[it]?.name } ?: ""
            val categoryStr = tx.categoryId?.let { categoriesMap[it]?.name } ?: ""
            val feeStr = tx.adminFee?.toPlainString() ?: ""
            val escapedNote = tx.note.replace("\"", "\"\"").let { "\"$it\"" }
            val tagsStr = tx.tags.joinToString(";").replace("\"", "\"\"").let { "\"$it\"" }

            csvBuilder.append("$dateStr,$typeStr,$amountStr,$accountSrc,$accountDst,$categoryStr,$feeStr,$escapedNote,$tagsStr\n")
        }
        return csvBuilder.toString()
    }

    suspend fun importTransactionsFromCsv(csvContent: String) {
        // Mengimpor dan memetakan transaksi kembali dari data format CSV
        val lines = csvContent.split(Regex("\\r?\\n"))
        if (lines.size <= 1) return

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val existingAccounts = accountRepository.getAccounts().toMutableList()
        val existingCategories = categoryRepository.getCategories().toMutableList()

        suspend fun getOrCreateAccount(name: String): String {
            if (name.isBlank()) return ""
            val found = existingAccounts.find { it.name.equals(name, ignoreCase = true) && !it.deleted }
            if (found != null) return found.id

            val newId = java.util.UUID.randomUUID().toString()
            val newAcc = Account(
                id = newId,
                name = name,
                type = AccountType.TUNAI,
                icon = "account_balance_wallet",
                color = "#4CAF50",
                initialBalance = BigDecimal.ZERO
            )
            accountRepository.insertAccount(newAcc)
            existingAccounts.add(newAcc)
            return newId
        }

        suspend fun getOrCreateCategory(name: String, type: CategoryType): String {
            if (name.isBlank()) return ""
            val found = existingCategories.find { it.name.equals(name, ignoreCase = true) && !it.deleted }
            if (found != null) return found.id

            val newId = java.util.UUID.randomUUID().toString()
            val newCat = Category(
                id = newId,
                name = name,
                type = type,
                icon = "category",
                color = "#FF9800"
            )
            categoryRepository.insertCategory(newCat)
            existingCategories.add(newCat)
            return newId
        }

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue

            val tokens = mutableListOf<String>()
            var currentToken = java.lang.StringBuilder()
            var inQuotes = false
            var j = 0
            while (j < line.length) {
                val c = line[j]
                if (c == '\"') {
                    inQuotes = !inQuotes
                } else if (c == ',' && !inQuotes) {
                    tokens.add(currentToken.toString().trim())
                    currentToken = java.lang.StringBuilder()
                } else {
                    currentToken.append(c)
                }
                j++
            }
            tokens.add(currentToken.toString().trim())

            if (tokens.size < 4) continue

            try {
                val dateVal = dateFormat.parse(tokens[0])?.time ?: System.currentTimeMillis()
                val typeVal = TransactionType.valueOf(tokens[1].uppercase())
                val amountVal = BigDecimal(tokens[2])
                val accountSrcName = tokens[3]
                val accountDstName = tokens.getOrNull(4) ?: ""
                val categoryName = tokens.getOrNull(5) ?: ""
                val feeVal = tokens.getOrNull(6)?.let { if (it.isEmpty()) null else BigDecimal(it) }
                val noteVal = tokens.getOrNull(7)?.removeSurrounding("\"")?.replace("\"\"", "\"") ?: ""
                val tagsVal = tokens.getOrNull(8)?.removeSurrounding("\"")?.replace("\"\"", "\"")?.split(";")?.filter { it.isNotEmpty() } ?: emptyList()

                val accountSrcId = getOrCreateAccount(accountSrcName)
                val accountDstId = if (accountDstName.isNotEmpty()) getOrCreateAccount(accountDstName) else null

                val catType = if (typeVal == TransactionType.PEMASUKAN) CategoryType.INCOME else CategoryType.EXPENSE
                val categoryId = if (categoryName.isNotEmpty()) getOrCreateCategory(categoryName, catType) else null

                val tx = Transaction(
                    id = java.util.UUID.randomUUID().toString(),
                    amount = amountVal,
                    type = typeVal,
                    categoryId = categoryId,
                    accountId = accountSrcId,
                    toAccountId = accountDstId,
                    adminFee = feeVal,
                    note = noteVal,
                    dateTime = dateVal,
                    tags = tagsVal,
                    sourceInput = "IMPORT"
                )
                transactionRepository.insertTransaction(tx)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun signOutGoogle(onComplete: () -> Unit) {
        viewModelScope.launch {
            googleAuthManager.signOut()
            onComplete()
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                val container = (app as DompetKuApplication).container
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    application = app,
                    accountRepository = container.accountRepository,
                    categoryRepository = container.categoryRepository,
                    transactionRepository = container.transactionRepository,
                    preferenceRepository = container.preferenceRepository,
                    assetHoldingRepository = container.assetHoldingRepository,
                    budgetRepository = container.budgetRepository,
                    goalRepository = container.goalRepository,
                    recurringRuleRepository = container.recurringRuleRepository,
                    draftTransactionRepository = container.draftTransactionRepository,
                    parsingRuleRepository = container.parsingRuleRepository,
                    importTemplateRepository = container.importTemplateRepository,
                    parserCorrectionRepository = container.parserCorrectionRepository,
                    syncMetaRepository = container.syncMetaRepository,
                    googleAuthManager = container.googleAuthManager,
                    googleSheetsSyncClient = container.googleSheetsSyncClient,
                    cloudSyncManager = container.cloudSyncManager,
                    driveBackupManager = container.driveBackupManager
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
