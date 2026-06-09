package com.example

import com.example.domain.model.*
import com.example.domain.utils.FreeTextParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class FreeTextParserTest {

    private val testAccounts = listOf(
        Account(
            id = "gopay_id",
            name = "GoPay",
            type = AccountType.E_WALLET,
            icon = "wallet",
            color = "#000000",
            initialBalance = BigDecimal.ZERO
        ),
        Account(
            id = "seabank_id",
            name = "SeaBank",
            type = AccountType.BANK,
            icon = "bank",
            color = "#000000",
            initialBalance = BigDecimal.ZERO
        ),
        Account(
            id = "bibit_id",
            name = "Bibit",
            type = AccountType.BROKER,
            icon = "broker",
            color = "#000000",
            initialBalance = BigDecimal.ZERO
        )
    )

    private val testCategories = listOf(
        Category(
            id = "makan_id",
            name = "Makan",
            type = CategoryType.EXPENSE,
            icon = "food",
            color = "#000000"
        ),
        Category(
            id = "gaji_id",
            name = "Gaji",
            type = CategoryType.INCOME,
            icon = "salary",
            color = "#000000"
        )
    )

    @Test
    fun testParseExpense() {
        val result = FreeTextParser.parse(
            text = "makan 15rb gopay",
            accounts = testAccounts,
            categories = testCategories,
            corrections = emptyList()
        )

        assertEquals(BigDecimal("15000"), result.amount)
        assertEquals(TransactionType.PENGELUARAN, result.type)
        assertEquals("makan_id", result.category?.id)
        assertEquals("gopay_id", result.account?.id)
        assertNull(result.toAccount)
    }

    @Test
    fun testParseIncome() {
        val result = FreeTextParser.parse(
            text = "gaji 2jt seabank",
            accounts = testAccounts,
            categories = testCategories,
            corrections = emptyList()
        )

        assertEquals(BigDecimal("2000000"), result.amount)
        assertEquals(TransactionType.PEMASUKAN, result.type)
        assertEquals("gaji_id", result.category?.id)
        assertEquals("seabank_id", result.account?.id)
        assertNull(result.toAccount)
    }

    @Test
    fun testParseTransfer() {
        val result = FreeTextParser.parse(
            text = "transfer 14jt seabank ke bibit",
            accounts = testAccounts,
            categories = testCategories,
            corrections = emptyList()
        )

        assertEquals(BigDecimal("14000000"), result.amount)
        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals("seabank_id", result.account?.id)
        assertEquals("bibit_id", result.toAccount?.id)
    }

    @Test
    fun testParseWithCorrections() {
        val corrections = listOf(
            ParserCorrection(
                id = "1",
                keyword = "starbucks",
                mappedCategoryId = "makan_id",
                mappedAccountId = "gopay_id"
            )
        )

        val result = FreeTextParser.parse(
            text = "beli starbucks 50k",
            accounts = testAccounts,
            categories = testCategories,
            corrections = corrections
        )

        assertEquals(BigDecimal("50000"), result.amount)
        assertEquals("makan_id", result.category?.id)
        assertEquals("gopay_id", result.account?.id)
    }
}
