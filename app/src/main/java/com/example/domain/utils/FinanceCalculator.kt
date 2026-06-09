package com.example.domain.utils

import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.model.AssetHolding
import com.example.domain.model.AssetCategory
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale

object FinanceCalculator {

    /**
     * Recalculates up-to-date current balances for all accounts by combining
     * initial base balances with active (non-deleted) transactions.
     */
    fun calculateAccountBalances(
        accounts: List<Account>,
        transactions: List<Transaction>
    ): List<Account> {
        val nonDeletedTransactions = transactions.filter { !it.deleted }
        
        return accounts.map { account ->
            var currentBalance = account.initialBalance
            
            nonDeletedTransactions.forEach { tx ->
                when (tx.type) {
                    TransactionType.PEMASUKAN -> {
                        if (tx.accountId == account.id) {
                            currentBalance = currentBalance.add(tx.amount)
                        }
                    }
                    TransactionType.PENGELUARAN -> {
                        if (tx.accountId == account.id) {
                            currentBalance = currentBalance.subtract(tx.amount)
                        }
                    }
                    TransactionType.RECONCILE -> {
                        if (tx.accountId == account.id) {
                            currentBalance = currentBalance.add(tx.amount)
                        }
                    }
                    TransactionType.TRANSFER -> {
                        if (tx.accountId == account.id) {
                            // Source account: subtract transfer amount + optional admin fee
                            currentBalance = currentBalance.subtract(tx.amount)
                            tx.adminFee?.let { fee ->
                                currentBalance = currentBalance.subtract(fee)
                            }
                        }
                        if (tx.toAccountId == account.id) {
                            // Destination account: add transfer amount
                            currentBalance = currentBalance.add(tx.amount)
                        }
                    }
                }
            }
            
            account.copy(balance = currentBalance)
        }
    }

    /**
     * Net worth is the sum of all calculated account balances plus the net value of
     * asset holdings (investments, deposits, receivables) minus debts.
     */
    fun calculateNetWorth(accounts: List<Account>, holdings: List<AssetHolding>): BigDecimal {
        val accountsSum = accounts.fold(BigDecimal.ZERO) { acc, account ->
            acc.add(account.balance)
        }
        val holdingsSum = holdings.fold(BigDecimal.ZERO) { acc, holding ->
            if (holding.deleted) {
                acc
            } else {
                when (holding.category) {
                    AssetCategory.SAHAM,
                    AssetCategory.REKSA_DANA,
                    AssetCategory.OBLIGASI,
                    AssetCategory.EMAS,
                    AssetCategory.KRIPTO -> {
                        acc.add(holding.currentValuation)
                    }
                    AssetCategory.DEPOSITO -> {
                        acc.add(holding.nominal)
                    }
                    AssetCategory.PIUTANG -> {
                        acc.add(holding.nominal)
                    }
                    AssetCategory.UTANG -> {
                        acc.subtract(holding.nominal)
                    }
                }
            }
        }
        return accountsSum.add(holdingsSum)
    }

    /**
     * Calculates Pemasukan vs Pengeluaran and surplus/deficit for the current month.
     * Note: Transfers do not impact surplus/deficit, but their associated admin fees are recorded as expenses.
     */
    fun calculateMonthlyFlows(
        transactions: List<Transaction>,
        monthStartMs: Long,
        monthEndMs: Long
    ): MonthlyFlowResult {
        var totalIncome = BigDecimal.ZERO
        var totalExpense = BigDecimal.ZERO

        transactions.filter { !it.deleted && it.dateTime in monthStartMs..monthEndMs }.forEach { tx ->
            when (tx.type) {
                TransactionType.PEMASUKAN -> {
                    totalIncome = totalIncome.add(tx.amount)
                }
                TransactionType.PENGELUARAN -> {
                    totalExpense = totalExpense.add(tx.amount)
                }
                TransactionType.RECONCILE -> {
                    // Reconciliation adjustment can increase or decrease
                    if (tx.amount >= BigDecimal.ZERO) {
                        totalIncome = totalIncome.add(tx.amount)
                    } else {
                        totalExpense = totalExpense.add(tx.amount.abs())
                    }
                }
                TransactionType.TRANSFER -> {
                    // Transfer amount is net-neutral, but admin fee counts as a real expense
                    tx.adminFee?.let { fee ->
                        totalExpense = totalExpense.add(fee)
                    }
                }
            }
        }

        val surplus = totalIncome.subtract(totalExpense)
        return MonthlyFlowResult(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            surplusValue = surplus,
            isDeficit = surplus < BigDecimal.ZERO
        )
    }

    /**
     * Group positive accounts as Assets to describe composing distribution.
     */
    fun calculateAssetComposition(accounts: List<Account>): Map<AccountType, BigDecimal> {
        return accounts
            .filter { it.balance > BigDecimal.ZERO && it.type != AccountType.KARTU_KREDIT }
            .groupBy { it.type }
            .mapValues { (_, acs) -> 
                acs.fold(BigDecimal.ZERO) { sum, a -> sum.add(a.balance) }
            }
    }

    fun calculateDailySafeToSpend(
        budgets: List<com.example.domain.model.Budget>,
        transactions: List<Transaction>
    ): BigDecimal {
        val cal = Calendar.getInstance()
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val remainingDays = (totalDays - currentDay + 1).coerceAtLeast(1)

        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, totalDays)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfMonth = cal.timeInMillis

        var totalAvailable = BigDecimal.ZERO
        var totalSpent = BigDecimal.ZERO

        budgets.filter { !it.deleted && it.period == com.example.domain.model.BudgetPeriod.BULANAN }.forEach { budget ->
            totalAvailable = totalAvailable.add(budget.amount)
            val spent = transactions.filter {
                !it.deleted &&
                it.dateTime in startOfMonth..endOfMonth &&
                it.type == TransactionType.PENGELUARAN &&
                (budget.categoryId == null || it.categoryId == budget.categoryId)
            }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
            totalSpent = totalSpent.add(spent)
        }

        val remaining = totalAvailable.subtract(totalSpent)
        return if (remaining > BigDecimal.ZERO) {
            remaining.divide(BigDecimal(remainingDays), 2, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    fun calculateCashFlowForecast(
        accounts: List<Account>,
        recurringRules: List<com.example.domain.model.RecurringRule>,
        transactions: List<Transaction>
    ): ForecastResult {
        val currentLiquid = accounts.filter {
            !it.isArchived && !it.deleted &&
            (it.type == AccountType.BANK || it.type == AccountType.E_WALLET || it.type == AccountType.TUNAI)
        }.fold(BigDecimal.ZERO) { sum, a -> sum.add(a.balance) }

        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val endOfMonthDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val remainingDays = (endOfMonthDays - currentDay + 1).coerceAtLeast(1)

        cal.set(Calendar.DAY_OF_MONTH, endOfMonthDays)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfMonthMs = cal.timeInMillis

        var expectedIncome = BigDecimal.ZERO
        var expectedExpense = BigDecimal.ZERO

        recurringRules.filter { it.isEnabled && !it.deleted }.forEach { rule ->
            var tempExecution = rule.nextExecutionDate
            var count = 0
            while (tempExecution in now..endOfMonthMs) {
                count++
                tempExecution = when (rule.frequency) {
                    com.example.domain.model.RecurringFrequency.DAILY -> tempExecution + (rule.interval * 24L * 60 * 60 * 1000)
                    com.example.domain.model.RecurringFrequency.WEEKLY -> tempExecution + (rule.interval * 7L * 24 * 60 * 60 * 1000)
                    com.example.domain.model.RecurringFrequency.MONTHLY -> {
                        val c = Calendar.getInstance().apply { timeInMillis = tempExecution }
                        c.add(Calendar.MONTH, rule.interval)
                        c.timeInMillis
                    }
                    com.example.domain.model.RecurringFrequency.CUSTOM -> tempExecution + ((rule.customDays ?: 1) * rule.interval * 24L * 60 * 60 * 1000)
                }
            }
            val ruleTotal = rule.amount.multiply(BigDecimal(count))
            if (rule.type == TransactionType.PEMASUKAN) {
                expectedIncome = expectedIncome.add(ruleTotal)
            } else {
                expectedExpense = expectedExpense.add(ruleTotal)
            }
        }

        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
        val pastExpenses = transactions.filter {
            !it.deleted && it.dateTime in thirtyDaysAgo..now && it.type == TransactionType.PENGELUARAN
        }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }

        val avgDailyExpense = if (pastExpenses > BigDecimal.ZERO) {
            pastExpenses.divide(BigDecimal("30"), 2, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val estimatedDailyTotal = avgDailyExpense.multiply(BigDecimal(remainingDays))
        val projected = currentLiquid.add(expectedIncome).subtract(expectedExpense).subtract(estimatedDailyTotal)
        val isDeficit = projected < BigDecimal.ZERO

        return ForecastResult(
            currentLiquidBalance = currentLiquid,
            expectedIncome = expectedIncome,
            expectedExpense = expectedExpense,
            averageDailyExpense = avgDailyExpense,
            projectedBalance = projected,
            isDeficitProjected = isDeficit,
            deficitAmount = if (isDeficit) projected.abs() else BigDecimal.ZERO
        )
    }

    /**
     * Calculates the overall financial health rating based on savings rate and emergency fund coverage.
     */
    fun calculateFinancialHealth(
        accounts: List<Account>,
        transactions: List<Transaction>,
        monthlyFlow: MonthlyFlowResult
    ): FinancialHealthResult {
        val liquidBalance = accounts.filter {
            !it.isArchived && !it.deleted &&
            (it.type == AccountType.BANK || it.type == AccountType.E_WALLET || it.type == AccountType.TUNAI)
        }.fold(BigDecimal.ZERO) { sum, a -> sum.add(a.balance) }

        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
        val pastExpenses = transactions.filter {
            !it.deleted && it.dateTime in thirtyDaysAgo..now && it.type == TransactionType.PENGELUARAN
        }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }

        // 1. Savings Rate points (up to 50)
        val income = monthlyFlow.totalIncome
        val expense = monthlyFlow.totalExpense
        val savingsRate = if (income > BigDecimal.ZERO) {
            val surplus = income.subtract(expense)
            surplus.divide(income, 4, java.math.RoundingMode.HALF_UP).toFloat()
        } else {
            0f
        }

        val savingsPoints = when {
            savingsRate >= 0.20f -> 50f
            savingsRate > 0f -> (savingsRate / 0.20f) * 50f
            else -> 0f
        }

        // 2. Emergency Fund points (up to 50)
        // Assume monthly need is pastExpenses, if 0, then we use a minimum of Rp 1.000.000 for calculation to avoid division by zero
        val monthlyExpenseNeed = if (pastExpenses > BigDecimal.ZERO) pastExpenses else BigDecimal("1000000")
        val emergencyFundMonths = if (liquidBalance > BigDecimal.ZERO) {
            liquidBalance.divide(monthlyExpenseNeed, 2, java.math.RoundingMode.HALF_UP).toFloat()
        } else {
            0f
        }

        val emergencyPoints = when {
            emergencyFundMonths >= 3f -> 50f
            emergencyFundMonths > 0f -> (emergencyFundMonths / 3f) * 50f
            else -> 0f
        }

        val totalScore = (savingsPoints + emergencyPoints).toInt().coerceIn(0, 100)

        val (rating, recommendation) = when {
            totalScore >= 75 -> Pair(
                "SEHAT",
                "Keuangan Anda sehat. Pertahankan kebiasaan menabung dan dana darurat Anda saat ini."
            )
            totalScore >= 40 -> Pair(
                "WASPADA",
                "Keuangan cukup stabil, namun tingkatkan porsi tabungan bulanan atau perbesar dana cadangan Anda."
            )
            else -> Pair(
                "BAHAYA",
                "Kondisi keuangan kritis. Segera batasi pengeluaran non-primer dan fokus membangun dana darurat."
            )
        }

        return FinancialHealthResult(
            score = totalScore,
            rating = rating,
            savingsRate = savingsRate,
            emergencyFundMonths = emergencyFundMonths,
            recommendation = recommendation
        )
    }

    /**
     * Analyzes past transaction history to automatically detect recurring expenditure patterns.
     */
    fun detectRecurringSuggestions(
        transactions: List<Transaction>
    ): List<RecurringSuggestion> {
        val expenses = transactions.filter { !it.deleted && it.type == TransactionType.PENGELUARAN }
        if (expenses.size < 2) return emptyList()

        val groups = expenses.groupBy { it.note.trim().lowercase() }
        val suggestions = mutableListOf<RecurringSuggestion>()

        groups.forEach { (_, txs) ->
            if (txs.size >= 2) {
                val sortedTxs = txs.sortedBy { it.dateTime }
                
                var monthlyCount = 0
                var weeklyCount = 0

                for (i in 0 until sortedTxs.size - 1) {
                    val tx1 = sortedTxs[i]
                    val tx2 = sortedTxs[i + 1]
                    val diffMs = tx2.dateTime - tx1.dateTime
                    val diffDays = diffMs / (24L * 60 * 60 * 1000)

                    if (diffDays in 25L..35L) {
                        monthlyCount++
                    } else if (diffDays in 6L..8L) {
                        weeklyCount++
                    }
                }

                if (monthlyCount > 0 || weeklyCount > 0) {
                    val latest = sortedTxs.last()
                    val firstAmt = sortedTxs[0].amount
                    val lastAmt = latest.amount
                    val pctDiff = if (firstAmt > BigDecimal.ZERO) {
                        lastAmt.subtract(firstAmt).abs().divide(firstAmt, 2, java.math.RoundingMode.HALF_UP).toFloat()
                    } else {
                        0f
                    }

                    if (pctDiff <= 0.15f) {
                        val freq = if (monthlyCount >= weeklyCount) {
                            com.example.domain.model.RecurringFrequency.MONTHLY
                        } else {
                            com.example.domain.model.RecurringFrequency.WEEKLY
                        }
                        
                        val confidence = if (freq == com.example.domain.model.RecurringFrequency.MONTHLY) {
                            "Pola bulanan terdeteksi dari riwayat (${monthlyCount + 1}x transaksi)."
                        } else {
                            "Pola mingguan terdeteksi dari riwayat (${weeklyCount + 1}x transaksi)."
                        }

                        val suggestedName = latest.note.split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        }

                        suggestions.add(
                            RecurringSuggestion(
                                name = suggestedName,
                                amount = latest.amount,
                                categoryId = latest.categoryId,
                                accountId = latest.accountId,
                                frequency = freq,
                                interval = 1,
                                confidence = confidence
                            )
                        )
                    }
                }
            }
        }

        return suggestions
    }
}

data class MonthlyFlowResult(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val surplusValue: BigDecimal,
    val isDeficit: Boolean
)

data class ForecastResult(
    val currentLiquidBalance: BigDecimal,
    val expectedIncome: BigDecimal,
    val expectedExpense: BigDecimal,
    val averageDailyExpense: BigDecimal,
    val projectedBalance: BigDecimal,
    val isDeficitProjected: Boolean,
    val deficitAmount: BigDecimal
)

data class FinancialHealthResult(
    val score: Int,
    val rating: String, // "SEHAT", "WASPADA", "BAHAYA"
    val savingsRate: Float,
    val emergencyFundMonths: Float,
    val recommendation: String
)

data class RecurringSuggestion(
    val name: String,
    val amount: BigDecimal,
    val categoryId: String?,
    val accountId: String,
    val frequency: com.example.domain.model.RecurringFrequency,
    val interval: Int,
    val confidence: String
)
