package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.DompetKuApplication
import com.example.data.entity.TransactionEntity
import com.example.domain.model.*
import com.example.domain.utils.NotificationHelper
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

class RecurringTransactionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as DompetKuApplication
        val container = app.container

        // 1. Process Recurring Rules
        val rules = container.recurringRuleRepository.getRecurringRules()
        val now = System.currentTimeMillis()

        for (rule in rules) {
            if (rule.isEnabled && !rule.deleted && rule.nextExecutionDate <= now) {
                if (rule.isAutoExecute) {
                    // Record transaction automatically
                    val txId = UUID.randomUUID().toString()
                    val newTx = Transaction(
                        id = txId,
                        amount = rule.amount,
                        type = rule.type,
                        categoryId = rule.categoryId,
                        accountId = rule.accountId,
                        toAccountId = rule.toAccountId,
                        note = rule.note.ifEmpty { "Rutin Otomatis: ${rule.name}" },
                        dateTime = now,
                        recurringRuleId = rule.id,
                        goalId = rule.goalId,
                        updatedAt = now
                    )
                    container.transactionRepository.insertTransaction(newTx)

                    // Update linked goal progress
                    if (rule.goalId != null) {
                        val goal = container.goalRepository.getGoalById(rule.goalId)
                        if (goal != null) {
                            container.goalRepository.insertGoal(
                                goal.copy(
                                    currentAmount = goal.currentAmount.add(rule.amount),
                                    updatedAt = now
                                )
                            )
                        }
                    }

                    // Schedule next run
                    val nextDate = calculateNextOccurrence(rule.nextExecutionDate, rule.frequency, rule.interval, rule.customDays)
                    container.recurringRuleRepository.insertRecurringRule(
                        rule.copy(
                            nextExecutionDate = nextDate,
                            updatedAt = now
                        )
                    )

                    // Show success notification
                    NotificationHelper.showNotification(
                        context,
                        rule.id.hashCode(),
                        "Transaksi Otomatis Dicatat",
                        "Transaksi \"${rule.name}\" sebesar ${com.example.domain.utils.LocalizationUtils.formatCurrency(rule.amount, com.example.domain.utils.LocalizationUtils.activeCurrency, com.example.domain.utils.LocalizationUtils.activeLanguage)} berhasil dicatat."
                    )
                } else {
                    // Bill Reminder mode (INGATKAN)
                    // Show reminder notification
                    NotificationHelper.showNotification(
                        context,
                        rule.id.hashCode(),
                        "Pengingat Tagihan",
                        "Tagihan \"${rule.name}\" sebesar ${com.example.domain.utils.LocalizationUtils.formatCurrency(rule.amount, com.example.domain.utils.LocalizationUtils.activeCurrency, com.example.domain.utils.LocalizationUtils.activeLanguage)} jatuh tempo hari ini."
                    )

                    // Schedule next run so it doesn't alert repeatedly
                    val nextDate = calculateNextOccurrence(rule.nextExecutionDate, rule.frequency, rule.interval, rule.customDays)
                    container.recurringRuleRepository.insertRecurringRule(
                        rule.copy(
                            nextExecutionDate = nextDate,
                            updatedAt = now
                        )
                    )
                }
            }
        }

        // 2. Process Budgets (Envelope warning & overflow)
        val budgets = container.budgetRepository.getBudgets()
        val transactions = container.transactionRepository.getTransactions()

        for (budget in budgets) {
            if (budget.deleted) continue

            // Determine active and previous periods
            val currentRange = getPeriodDateRange(budget.period, budget.customStartDate, budget.customEndDate)
            val previousRange = getPreviousPeriodDateRange(budget.period, budget.customStartDate, budget.customEndDate)

            // Current spent
            val currentSpent = calculateSpentForCategory(transactions, budget.categoryId, currentRange.first, currentRange.second)

            // Previous carry over calculation
            var availableBudget = budget.amount
            if (budget.carryOver) {
                val prevSpent = calculateSpentForCategory(transactions, budget.categoryId, previousRange.first, previousRange.second)
                val prevRemaining = budget.amount.subtract(prevSpent)
                if (prevRemaining > BigDecimal.ZERO) {
                    availableBudget = availableBudget.add(prevRemaining)
                }
            }

            // Exceeds check
            if (currentSpent >= availableBudget) {
                NotificationHelper.showNotification(
                    context,
                    budget.id.hashCode() + 1,
                    "Anggaran Terlampaui!",
                    "Anggaran \"${budget.name}\" terlampaui. Terpakai: ${com.example.domain.utils.LocalizationUtils.formatCurrency(currentSpent, com.example.domain.utils.LocalizationUtils.activeCurrency, com.example.domain.utils.LocalizationUtils.activeLanguage)} / Limit: ${com.example.domain.utils.LocalizationUtils.formatCurrency(availableBudget, com.example.domain.utils.LocalizationUtils.activeCurrency, com.example.domain.utils.LocalizationUtils.activeLanguage)}."
                )
            } else if (currentSpent >= availableBudget.multiply(BigDecimal("0.8"))) {
                NotificationHelper.showNotification(
                    context,
                    budget.id.hashCode() + 2,
                    "Anggaran Hampir Habis",
                    "Anggaran \"${budget.name}\" telah terpakai 80%+. Terpakai: ${com.example.domain.utils.LocalizationUtils.formatCurrency(currentSpent, com.example.domain.utils.LocalizationUtils.activeCurrency, com.example.domain.utils.LocalizationUtils.activeLanguage)} / Limit: ${com.example.domain.utils.LocalizationUtils.formatCurrency(availableBudget, com.example.domain.utils.LocalizationUtils.activeCurrency, com.example.domain.utils.LocalizationUtils.activeLanguage)}."
                )
            }
        }

        // 3. Process Deposito maturity date reminders
        val holdings = container.assetHoldingRepository.getAssetHoldings()
        for (holding in holdings) {
            if (!holding.deleted && !holding.isCompleted && holding.category == AssetCategory.DEPOSITO && holding.maturityDate != null) {
                val daysRemaining = (holding.maturityDate - now) / (24L * 60 * 60 * 1000)
                if (daysRemaining in 0..7) {
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))
                    NotificationHelper.showNotification(
                        context,
                        holding.id.hashCode() + 500,
                        "Deposito Jatuh Tempo",
                        "Deposito \"${holding.name}\" sebesar ${com.example.domain.utils.LocalizationUtils.formatCurrency(holding.nominal, com.example.domain.utils.LocalizationUtils.activeCurrency, com.example.domain.utils.LocalizationUtils.activeLanguage)} akan/telah jatuh tempo pada ${sdf.format(Date(holding.maturityDate))}."
                    )
                }
            }
        }

        // 4. Daily reminder to record transactions
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Default reminder at 20:00 (8 PM)
        if (currentHour >= 20) {
            val pref = container.preferenceRepository
            val pin = pref.pinFlow.value
            // Only remind if the user is active (already set up pin/onboarding)
            if (pin != null) {
                NotificationHelper.showNotification(
                    context,
                    9999,
                    "Catat Keuangan Anda",
                    "Jangan lupa mencatat pengeluaran Anda hari ini di Sisa Uang!"
                )
            }
        }

        return Result.success()
    }

    private fun calculateSpentForCategory(
        txs: List<Transaction>,
        categoryId: String?,
        start: Long,
        end: Long
    ): BigDecimal {
        return txs.filter {
            !it.deleted &&
            it.dateTime in start..end &&
            it.type == TransactionType.PENGELUARAN &&
            (categoryId == null || it.categoryId == categoryId)
        }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
    }

    private fun calculateNextOccurrence(
        current: Long,
        frequency: RecurringFrequency,
        interval: Int,
        customDays: Int?
    ): Long {
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

    private fun getPeriodDateRange(period: BudgetPeriod, customStart: Long?, customEnd: Long?): Pair<Long, Long> {
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

    private fun getPreviousPeriodDateRange(period: BudgetPeriod, customStart: Long?, customEnd: Long?): Pair<Long, Long> {
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
            BudgetPeriod.KUSTOM -> {
                val duration = (customEnd ?: 0L) - (customStart ?: 0L)
                Pair((customStart ?: 0L) - duration, (customStart ?: 0L) - 1000L)
            }
        }
    }

    private fun formatRupiah(amount: BigDecimal): String {
        val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        return format.format(amount)
    }
}
