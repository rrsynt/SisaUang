package com.example

import com.example.domain.model.*
import com.example.domain.utils.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar

class FinanceCalculatorTest {

    @Test
    fun testCalculateNetWorthWithHoldings() {
        val accounts = listOf(
            Account(
                id = "1",
                name = "Dompet",
                type = AccountType.TUNAI,
                icon = "wallet",
                color = "#000000",
                initialBalance = BigDecimal("1000000"),
                balance = BigDecimal("1200000") // balance after transactions
            ),
            Account(
                id = "2",
                name = "Bank",
                type = AccountType.BANK,
                icon = "bank",
                color = "#000000",
                initialBalance = BigDecimal("5000000"),
                balance = BigDecimal("5000000")
            )
        )

        val holdings = listOf(
            AssetHolding(
                id = "a1",
                name = "Saham BBRI",
                category = AssetCategory.SAHAM,
                quantity = BigDecimal("10"),
                buyPrice = BigDecimal("4000"),
                currentPrice = BigDecimal("4500"),
                nominal = BigDecimal.ZERO,
                interestRate = BigDecimal.ZERO,
                maturityDate = null,
                cicilan = null
            ), // current valuation = 45000
            AssetHolding(
                id = "a2",
                name = "Deposito Mandiri",
                category = AssetCategory.DEPOSITO,
                quantity = BigDecimal.ZERO,
                buyPrice = BigDecimal.ZERO,
                currentPrice = BigDecimal.ZERO,
                nominal = BigDecimal("10000000"),
                interestRate = BigDecimal("5"),
                maturityDate = null,
                cicilan = null
            ), // nominal = 10000000
            AssetHolding(
                id = "a3",
                name = "Utang Teman",
                category = AssetCategory.UTANG,
                quantity = BigDecimal.ZERO,
                buyPrice = BigDecimal.ZERO,
                currentPrice = BigDecimal.ZERO,
                nominal = BigDecimal("500000"),
                interestRate = BigDecimal.ZERO,
                maturityDate = null,
                cicilan = null
            ) // reduces net worth by 500000
        )

        val netWorth = FinanceCalculator.calculateNetWorth(accounts, holdings)
        // accounts total = 1200000 + 5000000 = 6200000
        // holdings total = 45000 (saham) + 10000000 (deposito) - 500000 (utang) = 9545000
        // netWorth = 6200000 + 9545000 = 15745000
        assertEquals(BigDecimal("15745000"), netWorth)
    }

    @Test
    fun testCalculateDailySafeToSpend() {
        val budgets = listOf(
            Budget(
                id = "b1",
                name = "Makan",
                categoryId = "cat1",
                amount = BigDecimal("3000000"),
                period = BudgetPeriod.BULANAN,
                carryOver = false
            )
        )

        // Transactions in this month
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 5)
        val txTime = cal.timeInMillis

        val transactions = listOf(
            Transaction(
                id = "t1",
                amount = BigDecimal("500000"),
                type = TransactionType.PENGELUARAN,
                categoryId = "cat1",
                accountId = "1",
                dateTime = txTime
            )
        )

        // Safe to spend = (3000000 - 500000) / remainingDays
        val freshCal = Calendar.getInstance()
        val totalDays = freshCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = freshCal.get(Calendar.DAY_OF_MONTH)
        val remainingDays = (totalDays - currentDay + 1).coerceAtLeast(1)

        val safe = FinanceCalculator.calculateDailySafeToSpend(budgets, transactions)
        val expected = BigDecimal("2500000").divide(BigDecimal(remainingDays), 2, java.math.RoundingMode.HALF_UP)
        assertEquals(expected, safe)
    }

    @Test
    fun testCalculateCashFlowForecast() {
        val accounts = listOf(
            Account(
                id = "1",
                name = "Bank",
                type = AccountType.BANK,
                icon = "bank",
                color = "#000000",
                initialBalance = BigDecimal("1000000"),
                balance = BigDecimal("1000000")
            )
        )

        // Current time + 10 seconds to ensure it is in the future relative to the start of calculation
        val executionTime = System.currentTimeMillis() + 10000L

        val rules = listOf(
            RecurringRule(
                id = "r1",
                name = "Gaji",
                amount = BigDecimal("5000000"),
                type = TransactionType.PEMASUKAN,
                categoryId = null,
                accountId = "1",
                frequency = RecurringFrequency.MONTHLY,
                interval = 1,
                nextExecutionDate = executionTime,
                isAutoExecute = true
            ),
            RecurringRule(
                id = "r2",
                name = "Kost",
                amount = BigDecimal("1500000"),
                type = TransactionType.PENGELUARAN,
                categoryId = null,
                accountId = "1",
                frequency = RecurringFrequency.MONTHLY,
                interval = 1,
                nextExecutionDate = executionTime,
                isAutoExecute = true
            )
        )

        val transactions = emptyList<Transaction>()

        val result = FinanceCalculator.calculateCashFlowForecast(accounts, rules, transactions)

        // current liquid = 1000000
        // expected income = 5000000 (runs once tomorrow before end of month)
        // expected expense = 1500000 (runs once tomorrow)
        // average daily expense = 0
        // projected balance = 1000000 + 5000000 - 1500000 = 4500000
        assertEquals(BigDecimal("1000000"), result.currentLiquidBalance)
        assertEquals(BigDecimal("5000000"), result.expectedIncome)
        assertEquals(BigDecimal("1500000"), result.expectedExpense)
        assertTrue(BigDecimal("4500000").compareTo(result.projectedBalance) == 0)
        assertTrue(!result.isDeficitProjected)
    }
}
