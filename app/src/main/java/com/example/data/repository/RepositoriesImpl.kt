package com.example.data.repository

import com.example.data.dao.*
import com.example.data.entity.*
import com.example.domain.model.*
import com.example.domain.repository.*
import com.example.domain.utils.FinanceCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) : AccountRepository {
    override fun getAccountsFlow(): Flow<List<Account>> {
        return combine(
            accountDao.getAllAccountsFlow(),
            transactionDao.getAllTransactionsFlow()
        ) { accountEntities, transactionEntities ->
            val domainAccounts = accountEntities.map { it.toDomain(BigDecimal.ZERO) }
            val domainTransactions = transactionEntities.map { it.toDomain() }
            FinanceCalculator.calculateAccountBalances(domainAccounts, domainTransactions)
        }
    }

    override suspend fun getAccounts(): List<Account> {
        val accountEntities = accountDao.getAllAccounts()
        val transactionEntities = transactionDao.getAllTransactions()
        val domainAccounts = accountEntities.map { it.toDomain(BigDecimal.ZERO) }
        val domainTransactions = transactionEntities.map { it.toDomain() }
        return FinanceCalculator.calculateAccountBalances(domainAccounts, domainTransactions)
    }

    override suspend fun getAccountById(id: String): Account? {
        val entity = accountDao.getAccountById(id) ?: return null
        val accounts = listOf(entity.toDomain(BigDecimal.ZERO))
        val transactions = transactionDao.getAllTransactions().map { it.toDomain() }
        return FinanceCalculator.calculateAccountBalances(accounts, transactions).firstOrNull()
    }

    override suspend fun insertAccount(account: Account) {
        accountDao.insertAccount(AccountEntity.fromDomain(account))
    }

    override suspend fun softDeleteAccount(id: String) {
        accountDao.softDeleteAccount(id)
    }

    override suspend fun archiveAccount(id: String, archive: Boolean) {
        accountDao.updateArchivedStatus(id, archive)
    }
}

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {
    override fun getCategoriesFlow(): Flow<List<Category>> {
        return categoryDao.getAllCategoriesFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getCategories(): List<Category> {
        return categoryDao.getAllCategories().map { it.toDomain() }
    }

    override suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(CategoryEntity.fromDomain(category))
    }

    override suspend fun softDeleteCategory(id: String) {
        categoryDao.softDeleteCategory(id)
    }
}

class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao
) : TransactionRepository {
    override fun getTransactionsFlow(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactionsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getTransactions(): List<Transaction> {
        return transactionDao.getAllTransactions().map { it.toDomain() }
    }

    override suspend fun getTransactionById(id: String): Transaction? {
        return transactionDao.getTransactionById(id)?.toDomain()
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(TransactionEntity.fromDomain(transaction))
    }

    override suspend fun softDeleteTransaction(id: String) {
        transactionDao.softDeleteTransaction(id)
    }
}

class AssetHoldingRepositoryImpl(
    private val assetHoldingDao: AssetHoldingDao
) : AssetHoldingRepository {
    override fun getAssetHoldingsFlow(): Flow<List<AssetHolding>> {
        return assetHoldingDao.getAllAssetHoldingsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAssetHoldings(): List<AssetHolding> {
        return assetHoldingDao.getAllAssetHoldings().map { it.toDomain() }
    }

    override suspend fun getAssetHoldingById(id: String): AssetHolding? {
        return assetHoldingDao.getAssetHoldingById(id)?.toDomain()
    }

    override suspend fun insertAssetHolding(assetHolding: AssetHolding) {
        assetHoldingDao.insertAssetHolding(AssetHoldingEntity.fromDomain(assetHolding))
    }

    override suspend fun softDeleteAssetHolding(id: String) {
        assetHoldingDao.softDeleteAssetHolding(id)
    }
}

class BudgetRepositoryImpl(
    private val budgetDao: BudgetDao
) : BudgetRepository {
    override fun getBudgetsFlow(): Flow<List<Budget>> {
        return budgetDao.getAllBudgetsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getBudgets(): List<Budget> {
        return budgetDao.getAllBudgets().map { it.toDomain() }
    }

    override suspend fun getBudgetById(id: String): Budget? {
        return budgetDao.getBudgetById(id)?.toDomain()
    }

    override suspend fun insertBudget(budget: Budget) {
        budgetDao.insertBudget(BudgetEntity.fromDomain(budget))
    }

    override suspend fun softDeleteBudget(id: String) {
        budgetDao.softDeleteBudget(id)
    }
}

class GoalRepositoryImpl(
    private val goalDao: GoalDao
) : GoalRepository {
    override fun getGoalsFlow(): Flow<List<Goal>> {
        return goalDao.getAllGoalsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getGoals(): List<Goal> {
        return goalDao.getAllGoals().map { it.toDomain() }
    }

    override suspend fun getGoalById(id: String): Goal? {
        return goalDao.getGoalById(id)?.toDomain()
    }

    override suspend fun insertGoal(goal: Goal) {
        goalDao.insertGoal(GoalEntity.fromDomain(goal))
    }

    override suspend fun softDeleteGoal(id: String) {
        goalDao.softDeleteGoal(id)
    }
}

class RecurringRuleRepositoryImpl(
    private val recurringRuleDao: RecurringRuleDao
) : RecurringRuleRepository {
    override fun getRecurringRulesFlow(): Flow<List<RecurringRule>> {
        return recurringRuleDao.getAllRecurringRulesFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getRecurringRules(): List<RecurringRule> {
        return recurringRuleDao.getAllRecurringRules().map { it.toDomain() }
    }

    override suspend fun getRecurringRuleById(id: String): RecurringRule? {
        return recurringRuleDao.getRecurringRuleById(id)?.toDomain()
    }

    override suspend fun insertRecurringRule(recurringRule: RecurringRule) {
        recurringRuleDao.insertRecurringRule(RecurringRuleEntity.fromDomain(recurringRule))
    }

    override suspend fun softDeleteRecurringRule(id: String) {
        recurringRuleDao.softDeleteRecurringRule(id)
    }
}

class DraftTransactionRepositoryImpl(
    private val draftTransactionDao: DraftTransactionDao
) : DraftTransactionRepository {
    override fun getAllUnconfirmedDraftsFlow(): Flow<List<DraftTransaction>> {
        return draftTransactionDao.getAllUnconfirmedDraftsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllUnconfirmedDrafts(): List<DraftTransaction> {
        return draftTransactionDao.getAllUnconfirmedDrafts().map { it.toDomain() }
    }

    override suspend fun getDraftByHash(hash: String): DraftTransaction? {
        return draftTransactionDao.getDraftByHash(hash)?.toDomain()
    }

    override suspend fun insertDraft(draft: DraftTransaction) {
        draftTransactionDao.insertDraft(DraftTransactionEntity.fromDomain(draft))
    }

    override suspend fun deleteDraft(id: String) {
        draftTransactionDao.deleteDraft(id)
    }

    override suspend fun confirmDraft(id: String) {
        draftTransactionDao.confirmDraft(id)
    }
}

class ParsingRuleRepositoryImpl(
    private val parsingRuleDao: ParsingRuleDao
) : ParsingRuleRepository {
    override fun getAllParsingRulesFlow(): Flow<List<ParsingRule>> {
        return parsingRuleDao.getAllParsingRulesFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllParsingRules(): List<ParsingRule> {
        return parsingRuleDao.getAllParsingRules().map { it.toDomain() }
    }

    override suspend fun getParsingRuleById(id: String): ParsingRule? {
        return parsingRuleDao.getParsingRuleById(id)?.toDomain()
    }

    override suspend fun insertParsingRule(rule: ParsingRule) {
        parsingRuleDao.insertParsingRule(ParsingRuleEntity.fromDomain(rule))
    }

    override suspend fun softDeleteParsingRule(id: String) {
        parsingRuleDao.softDeleteParsingRule(id)
    }
}

class ImportTemplateRepositoryImpl(
    private val importTemplateDao: ImportTemplateDao
) : ImportTemplateRepository {
    override fun getAllImportTemplatesFlow(): Flow<List<ImportTemplate>> {
        return importTemplateDao.getAllImportTemplatesFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllImportTemplates(): List<ImportTemplate> {
        return importTemplateDao.getAllImportTemplates().map { it.toDomain() }
    }

    override suspend fun insertImportTemplate(template: ImportTemplate) {
        importTemplateDao.insertImportTemplate(ImportTemplateEntity.fromDomain(template))
    }

    override suspend fun softDeleteImportTemplate(id: String) {
        importTemplateDao.softDeleteImportTemplate(id)
    }
}

class ParserCorrectionRepositoryImpl(
    private val parserCorrectionDao: ParserCorrectionDao
) : ParserCorrectionRepository {
    override suspend fun getAllCorrections(): List<ParserCorrection> {
        return parserCorrectionDao.getAllCorrections().map { it.toDomain() }
    }

    override suspend fun getCorrectionByKeyword(keyword: String): ParserCorrection? {
        return parserCorrectionDao.getCorrectionByKeyword(keyword)?.toDomain()
    }

    override suspend fun insertCorrection(correction: ParserCorrection) {
        parserCorrectionDao.insertCorrection(ParserCorrectionEntity.fromDomain(correction))
    }
}

class SyncMetaRepositoryImpl(
    private val syncMetaDao: SyncMetaDao
) : SyncMetaRepository {
    override suspend fun getSyncMeta(): SyncMeta? {
        return syncMetaDao.getSyncMeta()?.toDomain()
    }

    override fun getSyncMetaFlow(): Flow<SyncMeta?> {
        return syncMetaDao.getSyncMetaFlow().map { it?.toDomain() }
    }

    override suspend fun saveSyncMeta(syncMeta: SyncMeta) {
        syncMetaDao.insertSyncMeta(SyncMetaEntity.fromDomain(syncMeta))
    }
}


