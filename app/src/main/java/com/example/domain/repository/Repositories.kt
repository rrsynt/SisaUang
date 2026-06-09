package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccountsFlow(): Flow<List<Account>>
    suspend fun getAccounts(): List<Account>
    suspend fun getAccountById(id: String): Account?
    suspend fun insertAccount(account: Account)
    suspend fun softDeleteAccount(id: String)
    suspend fun archiveAccount(id: String, archive: Boolean)
}

interface CategoryRepository {
    fun getCategoriesFlow(): Flow<List<Category>>
    suspend fun getCategories(): List<Category>
    suspend fun insertCategory(category: Category)
    suspend fun softDeleteCategory(id: String)
}

interface TransactionRepository {
    fun getTransactionsFlow(): Flow<List<Transaction>>
    suspend fun getTransactions(): List<Transaction>
    suspend fun getTransactionById(id: String): Transaction?
    suspend fun insertTransaction(transaction: Transaction)
    suspend fun softDeleteTransaction(id: String)
}

interface AssetHoldingRepository {
    fun getAssetHoldingsFlow(): Flow<List<AssetHolding>>
    suspend fun getAssetHoldings(): List<AssetHolding>
    suspend fun getAssetHoldingById(id: String): AssetHolding?
    suspend fun insertAssetHolding(assetHolding: AssetHolding)
    suspend fun softDeleteAssetHolding(id: String)
}

interface BudgetRepository {
    fun getBudgetsFlow(): Flow<List<Budget>>
    suspend fun getBudgets(): List<Budget>
    suspend fun getBudgetById(id: String): Budget?
    suspend fun insertBudget(budget: Budget)
    suspend fun softDeleteBudget(id: String)
}

interface GoalRepository {
    fun getGoalsFlow(): Flow<List<Goal>>
    suspend fun getGoals(): List<Goal>
    suspend fun getGoalById(id: String): Goal?
    suspend fun insertGoal(goal: Goal)
    suspend fun softDeleteGoal(id: String)
}

interface RecurringRuleRepository {
    fun getRecurringRulesFlow(): Flow<List<RecurringRule>>
    suspend fun getRecurringRules(): List<RecurringRule>
    suspend fun getRecurringRuleById(id: String): RecurringRule?
    suspend fun insertRecurringRule(recurringRule: RecurringRule)
    suspend fun softDeleteRecurringRule(id: String)
}

interface DraftTransactionRepository {
    fun getAllUnconfirmedDraftsFlow(): Flow<List<DraftTransaction>>
    suspend fun getAllUnconfirmedDrafts(): List<DraftTransaction>
    suspend fun getDraftByHash(hash: String): DraftTransaction?
    suspend fun insertDraft(draft: DraftTransaction)
    suspend fun deleteDraft(id: String)
    suspend fun confirmDraft(id: String)
}

interface ParsingRuleRepository {
    fun getAllParsingRulesFlow(): Flow<List<ParsingRule>>
    suspend fun getAllParsingRules(): List<ParsingRule>
    suspend fun getParsingRuleById(id: String): ParsingRule?
    suspend fun insertParsingRule(rule: ParsingRule)
    suspend fun softDeleteParsingRule(id: String)
}

interface ImportTemplateRepository {
    fun getAllImportTemplatesFlow(): Flow<List<ImportTemplate>>
    suspend fun getAllImportTemplates(): List<ImportTemplate>
    suspend fun insertImportTemplate(template: ImportTemplate)
    suspend fun softDeleteImportTemplate(id: String)
}

interface ParserCorrectionRepository {
    suspend fun getAllCorrections(): List<ParserCorrection>
    suspend fun getCorrectionByKeyword(keyword: String): ParserCorrection?
    suspend fun insertCorrection(correction: ParserCorrection)
}

interface SyncMetaRepository {
    suspend fun getSyncMeta(): SyncMeta?
    fun getSyncMetaFlow(): Flow<SyncMeta?>
    suspend fun saveSyncMeta(syncMeta: SyncMeta)
}


