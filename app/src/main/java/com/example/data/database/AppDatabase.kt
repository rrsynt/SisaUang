package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.converter.Converters
import com.example.data.dao.AccountDao
import com.example.data.dao.CategoryDao
import com.example.data.dao.TransactionDao
import com.example.data.dao.AssetHoldingDao
import com.example.data.dao.BudgetDao
import com.example.data.dao.GoalDao
import com.example.data.dao.RecurringRuleDao
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
import com.example.data.entity.SyncMetaEntity
import com.example.data.dao.DraftTransactionDao
import com.example.data.dao.ParsingRuleDao
import com.example.data.dao.ImportTemplateDao
import com.example.data.dao.ParserCorrectionDao
import com.example.data.dao.SyncMetaDao
import com.example.security.SecurityHelper

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        AssetHoldingEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        RecurringRuleEntity::class,
        DraftTransactionEntity::class,
        ParsingRuleEntity::class,
        ImportTemplateEntity::class,
        ParserCorrectionEntity::class,
        SyncMetaEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun assetHoldingDao(): AssetHoldingDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun draftTransactionDao(): DraftTransactionDao
    abstract fun parsingRuleDao(): ParsingRuleDao
    abstract fun importTemplateDao(): ImportTemplateDao
    abstract fun parserCorrectionDao(): ParserCorrectionDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        var isEncrypted: Boolean = false
            private set

        fun getInstance(context: Context, passphraseBytes: ByteArray? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dompetku_secure.db"
                )

                // Securely load SQLCipher via Reflection for compile-safe, test-ready environments.
                // The passphrase is derived from a hardware-backed Android Keystore key plus a
                // per-install random salt -- there is deliberately no hardcoded fallback secret.
                // Callers that pass nothing (i.e. production) get the Keystore-derived key;
                // tests may inject a fixed byte array explicitly.
                val finalPassphrase = passphraseBytes
                    ?: SecurityHelper.deriveDatabasePassphrase(context.applicationContext)
                try {
                    // Load native SQLite library
                    System.loadLibrary("sqlcipher")

                    // Instantiate SupportFactory reflectively
                    val helperFactoryClass = Class.forName("net.zetetic.database.sqlcipher.SupportFactory")
                    val constructor = helperFactoryClass.getConstructor(ByteArray::class.java)
                    val factoryInstance = constructor.newInstance(finalPassphrase) as androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
                    builder.openHelperFactory(factoryInstance)
                    isEncrypted = true
                } catch (e: Exception) {
                    // Fail loudly. Falling back to plain SQLite would silently write the
                    // user's financial data unencrypted while the UI still claims the
                    // database is encrypted -- a far worse outcome than refusing to start.
                    throw IllegalStateException(
                        "SQLCipher tidak dapat diinisialisasi. Database keuangan tidak boleh " +
                            "dibuka tanpa enkripsi. (SQLCipher failed to initialise; refusing " +
                            "to open the financial database unencrypted.)",
                        e
                    )
                }

                builder.fallbackToDestructiveMigration()
                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
