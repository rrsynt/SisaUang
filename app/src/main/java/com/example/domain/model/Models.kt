package com.example.domain.model

import java.math.BigDecimal

enum class AccountType {
    BANK, E_WALLET, BROKER, DEPOSITO, TUNAI, KARTU_KREDIT, CUSTOM
}

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val icon: String,
    val color: String, // String representation of Color hex or identifier (e.g. "#4CAF50")
    val currency: String = "IDR",
    val initialBalance: BigDecimal,
    val isArchived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    // Calculated field, not saved directly to Account table if using recalculation
    val balance: BigDecimal = BigDecimal.ZERO
)

enum class CategoryType {
    INCOME, EXPENSE
}

data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: String,
    val isFavorite: Boolean = false,
    val order: Int = 0,
    val parentId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

enum class TransactionType {
    PEMASUKAN, PENGELUARAN, TRANSFER, RECONCILE
}

data class Transaction(
    val id: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: String?,
    val subCategoryId: String? = null,
    val accountId: String,          // Source account or reconciled account
    val toAccountId: String? = null, // Destination account for TRANSFER
    val adminFee: BigDecimal? = null, // Admin fee for TRANSFER
    val note: String = "",
    val dateTime: Long = System.currentTimeMillis(),
    val attachmentPath: String? = null,
    val tags: List<String> = emptyList(),
    val goalId: String? = null,
    val recurringRuleId: String? = null,
    val sourceInput: String = "MANUAL", // MANUAL, SUARA, PARSER, NOTIF, IMPORT, OCR
    val transactionHash: String? = null, // Used for deduplication
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

enum class AssetCategory {
    SAHAM, REKSA_DANA, OBLIGASI, EMAS, KRIPTO, DEPOSITO, UTANG, PIUTANG
}

data class AssetHolding(
    val id: String,
    val name: String,
    val category: AssetCategory,
    val quantity: BigDecimal,
    val buyPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val nominal: BigDecimal,
    val interestRate: BigDecimal,
    val maturityDate: Long?,
    val cicilan: BigDecimal?,
    val isCompleted: Boolean = false,
    val linkedAccountId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    val currentValuation: BigDecimal
        get() = when (category) {
            AssetCategory.SAHAM, AssetCategory.REKSA_DANA, AssetCategory.OBLIGASI,
            AssetCategory.EMAS, AssetCategory.KRIPTO -> quantity.multiply(currentPrice)
            AssetCategory.DEPOSITO, AssetCategory.PIUTANG, AssetCategory.UTANG -> nominal
        }

    val profitLossAmount: BigDecimal
        get() = when (category) {
            AssetCategory.SAHAM, AssetCategory.REKSA_DANA, AssetCategory.OBLIGASI,
            AssetCategory.EMAS, AssetCategory.KRIPTO -> {
                val totalCost = quantity.multiply(buyPrice)
                currentValuation.subtract(totalCost)
            }
            else -> BigDecimal.ZERO
        }

    val profitLossPercentage: BigDecimal
        get() = when (category) {
            AssetCategory.SAHAM, AssetCategory.REKSA_DANA, AssetCategory.OBLIGASI,
            AssetCategory.EMAS, AssetCategory.KRIPTO -> {
                val totalCost = quantity.multiply(buyPrice)
                if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                    profitLossAmount.multiply(BigDecimal("100")).divide(totalCost, 2, java.math.RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }
            }
            else -> BigDecimal.ZERO
        }
}

enum class BudgetPeriod {
    MINGGUAN, BULANAN, KUSTOM
}

data class Budget(
    val id: String,
    val name: String,
    val categoryId: String?,
    val amount: BigDecimal,
    val period: BudgetPeriod,
    val customStartDate: Long? = null,
    val customEndDate: Long? = null,
    val carryOver: Boolean = false,
    val alertThreshold: Float = 0.8f,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

data class Goal(
    val id: String,
    val name: String,
    val targetAmount: BigDecimal,
    val targetDate: Long? = null,
    val linkedAccountId: String? = null,
    val currentAmount: BigDecimal = BigDecimal.ZERO,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

enum class RecurringFrequency {
    DAILY, WEEKLY, MONTHLY, CUSTOM
}

data class RecurringRule(
    val id: String,
    val name: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String? = null,
    val note: String = "",
    val frequency: RecurringFrequency,
    val interval: Int = 1,
    val customDays: Int? = null,
    val nextExecutionDate: Long,
    val isAutoExecute: Boolean,
    val isEnabled: Boolean = true,
    val goalId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

data class DraftTransaction(
    val id: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String?,
    val toAccountId: String? = null,
    val adminFee: BigDecimal? = null,
    val note: String = "",
    val dateTime: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val sourceInput: String, // SUARA, PARSER, NOTIF, IMPORT, OCR
    val transactionHash: String?,
    val isConfirmed: Boolean = false,
    val itemsJson: String? = null
)

data class ParsingRule(
    val id: String,
    val appPackage: String,
    val name: String,
    val regexPattern: String,
    val type: TransactionType,
    val accountId: String,
    val categoryId: String?,
    val amountGroupIndex: Int = 1,
    val noteGroupIndex: Int? = null,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ImportTemplate(
    val id: String,
    val sourceName: String,
    val dateColumnIndex: Int,
    val amountColumnIndex: Int,
    val noteColumnIndex: Int,
    val typeColumnIndex: Int? = null,
    val categoryColumnIndex: Int? = null,
    val delimiter: String = ",",
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ParserCorrection(
    val id: String,
    val keyword: String,
    val mappedCategoryId: String?,
    val mappedAccountId: String?,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class SyncMeta(
    val googleSpreadsheetId: String?,
    val schemaVersion: Int,
    val lastSyncAt: Long,
    val status: String // SUCCESS, FAILED, RUNNING, IDLE
)
