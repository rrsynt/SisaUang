package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.model.AssetHolding
import com.example.domain.model.Budget
import com.example.domain.model.Goal
import com.example.domain.model.RecurringRule
import com.example.domain.model.ParserCorrection
import java.math.BigDecimal

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // Enum name
    val icon: String,
    val color: String,
    val currency: String,
    val initialBalance: String, // Saved as string
    val isArchived: Boolean,
    val updatedAt: Long,
    val deleted: Boolean
) {
    fun toDomain(balance: BigDecimal): Account {
        return Account(
            id = id,
            name = name,
            type = AccountType.valueOf(type),
            icon = icon,
            color = color,
            currency = currency,
            initialBalance = BigDecimal(initialBalance),
            isArchived = isArchived,
            updatedAt = updatedAt,
            deleted = deleted,
            balance = balance
        )
    }

    companion object {
        fun fromDomain(domain: Account): AccountEntity {
            return AccountEntity(
                id = domain.id,
                name = domain.name,
                type = domain.type.name,
                icon = domain.icon,
                color = domain.color,
                currency = domain.currency,
                initialBalance = domain.initialBalance.toPlainString(),
                isArchived = domain.isArchived,
                updatedAt = domain.updatedAt,
                deleted = domain.deleted
            )
        }
    }
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // Enum name
    val icon: String,
    val color: String,
    val isFavorite: Boolean,
    val sortOrder: Int, // 'order' is sometimes a reserved DB word, using sortOrder
    val parentId: String?,
    val updatedAt: Long,
    val deleted: Boolean
) {
    fun toDomain(): Category {
        return Category(
            id = id,
            name = name,
            type = CategoryType.valueOf(type),
            icon = icon,
            color = color,
            isFavorite = isFavorite,
            order = sortOrder,
            parentId = parentId,
            updatedAt = updatedAt,
            deleted = deleted
        )
    }

    companion object {
        fun fromDomain(domain: Category): CategoryEntity {
            return CategoryEntity(
                id = domain.id,
                name = domain.name,
                type = domain.type.name,
                icon = domain.icon,
                color = domain.color,
                isFavorite = domain.isFavorite,
                sortOrder = domain.order,
                parentId = domain.parentId,
                updatedAt = domain.updatedAt,
                deleted = domain.deleted
            )
        }
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amount: String,
    val type: String, // Enum name
    val categoryId: String?,
    val subCategoryId: String?,
    val accountId: String,
    val toAccountId: String?,
    val adminFee: String?,
    val note: String,
    val dateTime: Long,
    val attachmentPath: String?,
    val tags: String, // Comma-separated or serialized
    val goalId: String?,
    val recurringRuleId: String?,
    val sourceInput: String, // MANUAL, SUARA, PARSER, NOTIF, IMPORT, OCR
    val transactionHash: String?, // Used for deduplication
    val updatedAt: Long,
    val deleted: Boolean
) {
    fun toDomain(): Transaction {
        return Transaction(
            id = id,
            amount = BigDecimal(amount),
            type = TransactionType.valueOf(type),
            categoryId = categoryId,
            subCategoryId = subCategoryId,
            accountId = accountId,
            toAccountId = toAccountId,
            adminFee = adminFee?.let { BigDecimal(it) },
            note = note,
            dateTime = dateTime,
            attachmentPath = attachmentPath,
            tags = if (tags.isEmpty()) emptyList() else tags.split(","),
            goalId = goalId,
            recurringRuleId = recurringRuleId,
            sourceInput = sourceInput,
            transactionHash = transactionHash,
            updatedAt = updatedAt,
            deleted = deleted
        )
    }

    companion object {
        fun fromDomain(domain: Transaction): TransactionEntity {
            return TransactionEntity(
                id = domain.id,
                amount = domain.amount.toPlainString(),
                type = domain.type.name,
                categoryId = domain.categoryId,
                subCategoryId = domain.subCategoryId,
                accountId = domain.accountId,
                toAccountId = domain.toAccountId,
                adminFee = domain.adminFee?.toPlainString(),
                note = domain.note,
                dateTime = domain.dateTime,
                attachmentPath = domain.attachmentPath,
                tags = domain.tags.joinToString(","),
                goalId = domain.goalId,
                recurringRuleId = domain.recurringRuleId,
                sourceInput = domain.sourceInput,
                transactionHash = domain.transactionHash,
                updatedAt = domain.updatedAt,
                deleted = domain.deleted
            )
        }
    }
}

@Entity(tableName = "asset_holdings")
data class AssetHoldingEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // Enum name
    val quantity: String,
    val buyPrice: String,
    val currentPrice: String,
    val nominal: String,
    val interestRate: String,
    val maturityDate: Long?,
    val cicilan: String?,
    val isCompleted: Boolean,
    val linkedAccountId: String?,
    val updatedAt: Long,
    val deleted: Boolean
) {
    fun toDomain(): AssetHolding {
        return AssetHolding(
            id = id,
            name = name,
            category = com.example.domain.model.AssetCategory.valueOf(category),
            quantity = BigDecimal(quantity),
            buyPrice = BigDecimal(buyPrice),
            currentPrice = BigDecimal(currentPrice),
            nominal = BigDecimal(nominal),
            interestRate = BigDecimal(interestRate),
            maturityDate = maturityDate,
            cicilan = cicilan?.let { BigDecimal(it) },
            isCompleted = isCompleted,
            linkedAccountId = linkedAccountId,
            updatedAt = updatedAt,
            deleted = deleted
        )
    }

    companion object {
        fun fromDomain(domain: AssetHolding): AssetHoldingEntity {
            return AssetHoldingEntity(
                id = domain.id,
                name = domain.name,
                category = domain.category.name,
                quantity = domain.quantity.toPlainString(),
                buyPrice = domain.buyPrice.toPlainString(),
                currentPrice = domain.currentPrice.toPlainString(),
                nominal = domain.nominal.toPlainString(),
                interestRate = domain.interestRate.toPlainString(),
                maturityDate = domain.maturityDate,
                cicilan = domain.cicilan?.toPlainString(),
                isCompleted = domain.isCompleted,
                linkedAccountId = domain.linkedAccountId,
                updatedAt = domain.updatedAt,
                deleted = domain.deleted
            )
        }
    }
}

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String?,
    val amount: String,
    val period: String, // Enum name
    val customStartDate: Long?,
    val customEndDate: Long?,
    val carryOver: Boolean,
    val alertThreshold: Float,
    val updatedAt: Long,
    val deleted: Boolean
) {
    fun toDomain(): Budget {
        return Budget(
            id = id,
            name = name,
            categoryId = categoryId,
            amount = BigDecimal(amount),
            period = com.example.domain.model.BudgetPeriod.valueOf(period),
            customStartDate = customStartDate,
            customEndDate = customEndDate,
            carryOver = carryOver,
            alertThreshold = alertThreshold,
            updatedAt = updatedAt,
            deleted = deleted
        )
    }

    companion object {
        fun fromDomain(domain: Budget): BudgetEntity {
            return BudgetEntity(
                id = domain.id,
                name = domain.name,
                categoryId = domain.categoryId,
                amount = domain.amount.toPlainString(),
                period = domain.period.name,
                customStartDate = domain.customStartDate,
                customEndDate = domain.customEndDate,
                carryOver = domain.carryOver,
                alertThreshold = domain.alertThreshold,
                updatedAt = domain.updatedAt,
                deleted = domain.deleted
            )
        }
    }
}

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetAmount: String,
    val targetDate: Long?,
    val linkedAccountId: String?,
    val currentAmount: String,
    val updatedAt: Long,
    val deleted: Boolean
) {
    fun toDomain(): Goal {
        return Goal(
            id = id,
            name = name,
            targetAmount = BigDecimal(targetAmount),
            targetDate = targetDate,
            linkedAccountId = linkedAccountId,
            currentAmount = BigDecimal(currentAmount),
            updatedAt = updatedAt,
            deleted = deleted
        )
    }

    companion object {
        fun fromDomain(domain: Goal): GoalEntity {
            return GoalEntity(
                id = domain.id,
                name = domain.name,
                targetAmount = domain.targetAmount.toPlainString(),
                targetDate = domain.targetDate,
                linkedAccountId = domain.linkedAccountId,
                currentAmount = domain.currentAmount.toPlainString(),
                updatedAt = domain.updatedAt,
                deleted = domain.deleted
            )
        }
    }
}

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amount: String,
    val type: String, // Enum name
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String?,
    val note: String,
    val frequency: String, // Enum name
    val interval: Int,
    val customDays: Int?,
    val nextExecutionDate: Long,
    val isAutoExecute: Boolean,
    val isEnabled: Boolean,
    val goalId: String?,
    val updatedAt: Long,
    val deleted: Boolean
) {
    fun toDomain(): RecurringRule {
        return RecurringRule(
            id = id,
            name = name,
            amount = BigDecimal(amount),
            type = TransactionType.valueOf(type),
            categoryId = categoryId,
            accountId = accountId,
            toAccountId = toAccountId,
            note = note,
            frequency = com.example.domain.model.RecurringFrequency.valueOf(frequency),
            interval = interval,
            customDays = customDays,
            nextExecutionDate = nextExecutionDate,
            isAutoExecute = isAutoExecute,
            isEnabled = isEnabled,
            goalId = goalId,
            updatedAt = updatedAt,
            deleted = deleted
        )
    }

    companion object {
        fun fromDomain(domain: RecurringRule): RecurringRuleEntity {
            return RecurringRuleEntity(
                id = domain.id,
                name = domain.name,
                amount = domain.amount.toPlainString(),
                type = domain.type.name,
                categoryId = domain.categoryId,
                accountId = domain.accountId,
                toAccountId = domain.toAccountId,
                note = domain.note,
                frequency = domain.frequency.name,
                interval = domain.interval,
                customDays = domain.customDays,
                nextExecutionDate = domain.nextExecutionDate,
                isAutoExecute = domain.isAutoExecute,
                isEnabled = domain.isEnabled,
                goalId = domain.goalId,
                updatedAt = domain.updatedAt,
                deleted = domain.deleted
            )
        }
    }
}

@Entity(tableName = "draft_transactions")
data class DraftTransactionEntity(
    @PrimaryKey val id: String,
    val amount: String,
    val type: String,
    val categoryId: String?,
    val accountId: String?,
    val toAccountId: String?,
    val adminFee: String?,
    val note: String,
    val dateTime: Long,
    val tags: String,
    val sourceInput: String,
    val transactionHash: String?,
    val isConfirmed: Boolean,
    val itemsJson: String?
) {
    fun toDomain(): com.example.domain.model.DraftTransaction {
        return com.example.domain.model.DraftTransaction(
            id = id,
            amount = BigDecimal(amount),
            type = TransactionType.valueOf(type),
            categoryId = categoryId,
            accountId = accountId,
            toAccountId = toAccountId,
            adminFee = adminFee?.let { BigDecimal(it) },
            note = note,
            dateTime = dateTime,
            tags = if (tags.isEmpty()) emptyList() else tags.split(","),
            sourceInput = sourceInput,
            transactionHash = transactionHash,
            isConfirmed = isConfirmed,
            itemsJson = itemsJson
        )
    }

    companion object {
        fun fromDomain(domain: com.example.domain.model.DraftTransaction): DraftTransactionEntity {
            return DraftTransactionEntity(
                id = domain.id,
                amount = domain.amount.toPlainString(),
                type = domain.type.name,
                categoryId = domain.categoryId,
                accountId = domain.accountId,
                toAccountId = domain.toAccountId,
                adminFee = domain.adminFee?.toPlainString(),
                note = domain.note,
                dateTime = domain.dateTime,
                tags = domain.tags.joinToString(","),
                sourceInput = domain.sourceInput,
                transactionHash = domain.transactionHash,
                isConfirmed = domain.isConfirmed,
                itemsJson = domain.itemsJson
            )
        }
    }
}

@Entity(tableName = "parsing_rules")
data class ParsingRuleEntity(
    @PrimaryKey val id: String,
    val appPackage: String,
    val name: String,
    val regexPattern: String,
    val type: String,
    val accountId: String,
    val categoryId: String?,
    val amountGroupIndex: Int,
    val noteGroupIndex: Int?,
    val deleted: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): com.example.domain.model.ParsingRule {
        return com.example.domain.model.ParsingRule(
            id = id,
            appPackage = appPackage,
            name = name,
            regexPattern = regexPattern,
            type = TransactionType.valueOf(type),
            accountId = accountId,
            categoryId = categoryId,
            amountGroupIndex = amountGroupIndex,
            noteGroupIndex = noteGroupIndex,
            deleted = deleted,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(domain: com.example.domain.model.ParsingRule): ParsingRuleEntity {
            return ParsingRuleEntity(
                id = domain.id,
                appPackage = domain.appPackage,
                name = domain.name,
                regexPattern = domain.regexPattern,
                type = domain.type.name,
                accountId = domain.accountId,
                categoryId = domain.categoryId,
                amountGroupIndex = domain.amountGroupIndex,
                noteGroupIndex = domain.noteGroupIndex,
                deleted = domain.deleted,
                updatedAt = domain.updatedAt
            )
        }
    }
}

@Entity(tableName = "import_templates")
data class ImportTemplateEntity(
    @PrimaryKey val id: String,
    val sourceName: String,
    val dateColumnIndex: Int,
    val amountColumnIndex: Int,
    val noteColumnIndex: Int,
    val typeColumnIndex: Int?,
    val categoryColumnIndex: Int?,
    val delimiter: String,
    val deleted: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): com.example.domain.model.ImportTemplate {
        return com.example.domain.model.ImportTemplate(
            id = id,
            sourceName = sourceName,
            dateColumnIndex = dateColumnIndex,
            amountColumnIndex = amountColumnIndex,
            noteColumnIndex = noteColumnIndex,
            typeColumnIndex = typeColumnIndex,
            categoryColumnIndex = categoryColumnIndex,
            delimiter = delimiter,
            deleted = deleted,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(domain: com.example.domain.model.ImportTemplate): ImportTemplateEntity {
            return ImportTemplateEntity(
                id = domain.id,
                sourceName = domain.sourceName,
                dateColumnIndex = domain.dateColumnIndex,
                amountColumnIndex = domain.amountColumnIndex,
                noteColumnIndex = domain.noteColumnIndex,
                typeColumnIndex = domain.typeColumnIndex,
                categoryColumnIndex = domain.categoryColumnIndex,
                delimiter = domain.delimiter,
                deleted = domain.deleted,
                updatedAt = domain.updatedAt
            )
        }
    }
}

@Entity(tableName = "parser_corrections")
data class ParserCorrectionEntity(
    @PrimaryKey val id: String,
    val keyword: String,
    val mappedCategoryId: String?,
    val mappedAccountId: String?,
    val deleted: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): com.example.domain.model.ParserCorrection {
        return com.example.domain.model.ParserCorrection(
            id = id,
            keyword = keyword,
            mappedCategoryId = mappedCategoryId,
            mappedAccountId = mappedAccountId,
            deleted = deleted,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(domain: ParserCorrection): ParserCorrectionEntity {
            return ParserCorrectionEntity(
                id = domain.id,
                keyword = domain.keyword,
                mappedCategoryId = domain.mappedCategoryId,
                mappedAccountId = domain.mappedAccountId,
                deleted = domain.deleted,
                updatedAt = domain.updatedAt
            )
        }
    }
}

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: String = "SINGLETON",
    val googleSpreadsheetId: String?,
    val schemaVersion: Int,
    val lastSyncAt: Long,
    val status: String
) {
    fun toDomain(): com.example.domain.model.SyncMeta {
        return com.example.domain.model.SyncMeta(
            googleSpreadsheetId = googleSpreadsheetId,
            schemaVersion = schemaVersion,
            lastSyncAt = lastSyncAt,
            status = status
        )
    }

    companion object {
        fun fromDomain(domain: com.example.domain.model.SyncMeta): SyncMetaEntity {
            return SyncMetaEntity(
                id = "SINGLETON",
                googleSpreadsheetId = domain.googleSpreadsheetId,
                schemaVersion = domain.schemaVersion,
                lastSyncAt = domain.lastSyncAt,
                status = domain.status
            )
        }
    }
}

