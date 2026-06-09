package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.AccountEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.AssetHoldingEntity
import com.example.data.entity.BudgetEntity
import com.example.data.entity.GoalEntity
import com.example.data.entity.RecurringRuleEntity
import com.example.data.entity.DraftTransactionEntity
import com.example.data.entity.ParsingRuleEntity
import com.example.data.entity.ImportTemplateEntity
import com.example.data.entity.ParserCorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY isArchived ASC, name ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deleted = 0")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Query("UPDATE accounts SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteAccount(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateArchivedStatus(id: String, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sortOrder ASC, name ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Query("UPDATE categories SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteCategory(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY dateTime DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY dateTime DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteTransaction(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface AssetHoldingDao {
    @Query("SELECT * FROM asset_holdings WHERE deleted = 0 ORDER BY name ASC")
    fun getAllAssetHoldingsFlow(): Flow<List<AssetHoldingEntity>>

    @Query("SELECT * FROM asset_holdings WHERE deleted = 0")
    suspend fun getAllAssetHoldings(): List<AssetHoldingEntity>

    @Query("SELECT * FROM asset_holdings WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getAssetHoldingById(id: String): AssetHoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssetHolding(assetHolding: AssetHoldingEntity)

    @Query("UPDATE asset_holdings SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteAssetHolding(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    fun getAllBudgetsFlow(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE deleted = 0")
    suspend fun getAllBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getBudgetById(id: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Query("UPDATE budgets SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteBudget(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE deleted = 0")
    fun getAllGoalsFlow(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE deleted = 0")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getGoalById(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Query("UPDATE goals SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteGoal(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules WHERE deleted = 0")
    fun getAllRecurringRulesFlow(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE deleted = 0")
    suspend fun getAllRecurringRules(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getRecurringRuleById(id: String): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringRule(recurringRule: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteRecurringRule(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface DraftTransactionDao {
    @Query("SELECT * FROM draft_transactions WHERE isConfirmed = 0 ORDER BY dateTime DESC")
    fun getAllUnconfirmedDraftsFlow(): Flow<List<DraftTransactionEntity>>

    @Query("SELECT * FROM draft_transactions WHERE isConfirmed = 0 ORDER BY dateTime DESC")
    suspend fun getAllUnconfirmedDrafts(): List<DraftTransactionEntity>

    @Query("SELECT * FROM draft_transactions WHERE transactionHash = :hash LIMIT 1")
    suspend fun getDraftByHash(hash: String): DraftTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: DraftTransactionEntity)

    @Query("DELETE FROM draft_transactions WHERE id = :id")
    suspend fun deleteDraft(id: String)

    @Query("UPDATE draft_transactions SET isConfirmed = 1 WHERE id = :id")
    suspend fun confirmDraft(id: String)
}

@Dao
interface ParsingRuleDao {
    @Query("SELECT * FROM parsing_rules WHERE deleted = 0")
    fun getAllParsingRulesFlow(): Flow<List<ParsingRuleEntity>>

    @Query("SELECT * FROM parsing_rules WHERE deleted = 0")
    suspend fun getAllParsingRules(): List<ParsingRuleEntity>

    @Query("SELECT * FROM parsing_rules WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getParsingRuleById(id: String): ParsingRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParsingRule(rule: ParsingRuleEntity)

    @Query("UPDATE parsing_rules SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteParsingRule(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ImportTemplateDao {
    @Query("SELECT * FROM import_templates WHERE deleted = 0")
    fun getAllImportTemplatesFlow(): Flow<List<ImportTemplateEntity>>

    @Query("SELECT * FROM import_templates WHERE deleted = 0")
    suspend fun getAllImportTemplates(): List<ImportTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportTemplate(template: ImportTemplateEntity)

    @Query("UPDATE import_templates SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteImportTemplate(id: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ParserCorrectionDao {
    @Query("SELECT * FROM parser_corrections")
    suspend fun getAllCorrections(): List<ParserCorrectionEntity>

    @Query("SELECT * FROM parser_corrections WHERE keyword = :keyword LIMIT 1")
    suspend fun getCorrectionByKeyword(keyword: String): ParserCorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: ParserCorrectionEntity)
}

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE id = 'SINGLETON' LIMIT 1")
    suspend fun getSyncMeta(): com.example.data.entity.SyncMetaEntity?

    @Query("SELECT * FROM sync_meta WHERE id = 'SINGLETON' LIMIT 1")
    fun getSyncMetaFlow(): Flow<com.example.data.entity.SyncMetaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMeta(syncMeta: com.example.data.entity.SyncMetaEntity)
}

