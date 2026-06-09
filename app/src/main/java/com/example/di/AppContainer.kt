package com.example.di

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.sync.*
import okhttp3.OkHttpClient
import com.example.data.repository.AccountRepositoryImpl
import com.example.data.repository.CategoryRepositoryImpl
import com.example.data.repository.PreferenceRepositoryImpl
import com.example.data.repository.TransactionRepositoryImpl
import com.example.data.repository.AssetHoldingRepositoryImpl
import com.example.data.repository.BudgetRepositoryImpl
import com.example.data.repository.GoalRepositoryImpl
import com.example.data.repository.RecurringRuleRepositoryImpl
import com.example.data.repository.DraftTransactionRepositoryImpl
import com.example.data.repository.ParsingRuleRepositoryImpl
import com.example.data.repository.ImportTemplateRepositoryImpl
import com.example.data.repository.ParserCorrectionRepositoryImpl
import com.example.data.repository.SyncMetaRepositoryImpl
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.PreferenceRepository
import com.example.domain.repository.TransactionRepository
import com.example.domain.repository.AssetHoldingRepository
import com.example.domain.repository.BudgetRepository
import com.example.domain.repository.GoalRepository
import com.example.domain.repository.RecurringRuleRepository
import com.example.domain.repository.DraftTransactionRepository
import com.example.domain.repository.ParsingRuleRepository
import com.example.domain.repository.ImportTemplateRepository
import com.example.domain.repository.ParserCorrectionRepository
import com.example.domain.repository.SyncMetaRepository

interface AppContainer {
    val accountRepository: AccountRepository
    val categoryRepository: CategoryRepository
    val transactionRepository: TransactionRepository
    val preferenceRepository: PreferenceRepository
    val assetHoldingRepository: AssetHoldingRepository
    val budgetRepository: BudgetRepository
    val goalRepository: GoalRepository
    val recurringRuleRepository: RecurringRuleRepository
    val draftTransactionRepository: DraftTransactionRepository
    val parsingRuleRepository: ParsingRuleRepository
    val importTemplateRepository: ImportTemplateRepository
    val parserCorrectionRepository: ParserCorrectionRepository
    val syncMetaRepository: SyncMetaRepository
    val googleAuthManager: GoogleAuthManager
    val googleSheetsSyncClient: GoogleSheetsSyncClient
    val cloudSyncManager: CloudSyncManager
    val driveBackupManager: DriveBackupManager
}

class AppContainerImpl(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val accountRepository: AccountRepository by lazy {
        AccountRepositoryImpl(
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao()
        )
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(
            categoryDao = database.categoryDao()
        )
    }

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(
            transactionDao = database.transactionDao()
        )
    }

    override val preferenceRepository: PreferenceRepository by lazy {
        PreferenceRepositoryImpl(context)
    }

    override val assetHoldingRepository: AssetHoldingRepository by lazy {
        AssetHoldingRepositoryImpl(
            assetHoldingDao = database.assetHoldingDao()
        )
    }

    override val budgetRepository: BudgetRepository by lazy {
        BudgetRepositoryImpl(
            budgetDao = database.budgetDao()
        )
    }

    override val goalRepository: GoalRepository by lazy {
        GoalRepositoryImpl(
            goalDao = database.goalDao()
        )
    }

    override val recurringRuleRepository: RecurringRuleRepository by lazy {
        RecurringRuleRepositoryImpl(
            recurringRuleDao = database.recurringRuleDao()
        )
    }

    override val draftTransactionRepository: DraftTransactionRepository by lazy {
        DraftTransactionRepositoryImpl(
            draftTransactionDao = database.draftTransactionDao()
        )
    }

    override val parsingRuleRepository: ParsingRuleRepository by lazy {
        ParsingRuleRepositoryImpl(
            parsingRuleDao = database.parsingRuleDao()
        )
    }

    override val importTemplateRepository: ImportTemplateRepository by lazy {
        ImportTemplateRepositoryImpl(
            importTemplateDao = database.importTemplateDao()
        )
    }

    override val parserCorrectionRepository: ParserCorrectionRepository by lazy {
        ParserCorrectionRepositoryImpl(
            parserCorrectionDao = database.parserCorrectionDao()
        )
    }

    override val syncMetaRepository: SyncMetaRepository by lazy {
        SyncMetaRepositoryImpl(
            syncMetaDao = database.syncMetaDao()
        )
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    override val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager(context)
    }

    override val googleSheetsSyncClient: GoogleSheetsSyncClient by lazy {
        GoogleSheetsSyncClient(context, googleAuthManager, okHttpClient)
    }

    override val cloudSyncManager: CloudSyncManager by lazy {
        CloudSyncManager(context, googleSheetsSyncClient, database)
    }

    override val driveBackupManager: DriveBackupManager by lazy {
        DriveBackupManager(context, googleAuthManager, okHttpClient, database)
    }
}
