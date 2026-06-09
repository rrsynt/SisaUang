package com.example.domain.utils

import com.example.domain.model.*
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.util.UUID

object DatabaseSeeder {
    
    val DEFAULT_CATEGORIES = listOf(
        // INCOME CATEGORIES
        Category(id = "cat-in-gaji", name = "Gaji", type = CategoryType.INCOME, icon = "payments", color = "#4CAF50", isFavorite = true, order = 1),
        Category(id = "cat-in-invest", name = "Investasi", type = CategoryType.INCOME, icon = "trending_up", color = "#2196F3", isFavorite = true, order = 2),
        Category(id = "cat-in-bisnis", name = "Bisnis", type = CategoryType.INCOME, icon = "storefront", color = "#FF9800", isFavorite = false, order = 3),
        Category(id = "cat-in-lain", name = "Lain-lain", type = CategoryType.INCOME, icon = "category", color = "#9E9E9E", isFavorite = false, order = 4),

        // EXPENSE CATEGORIES
        Category(id = "cat-ex-makan", name = "Makanan", type = CategoryType.EXPENSE, icon = "restaurant", color = "#E91E63", isFavorite = true, order = 1),
        Category(id = "cat-ex-trans", name = "Transportasi", type = CategoryType.EXPENSE, icon = "directions_car", color = "#00BCD4", isFavorite = true, order = 2),
        Category(id = "cat-ex-belanja", name = "Belanja", type = CategoryType.EXPENSE, icon = "shopping_bag", color = "#3F51B5", isFavorite = true, order = 3),
        Category(id = "cat-ex-hibur", name = "Hiburan", type = CategoryType.EXPENSE, icon = "sports_esports", color = "#9C27B0", isFavorite = false, order = 4),
        Category(id = "cat-ex-tagih", name = "Tagihan & Utilitas", type = CategoryType.EXPENSE, icon = "receipt_long", color = "#FFC107", isFavorite = true, order = 5),
        Category(id = "cat-ex-sehat", name = "Kesehatan", type = CategoryType.EXPENSE, icon = "medical_services", color = "#F44336", isFavorite = false, order = 6),
        Category(id = "cat-ex-lain", name = "Lain-lain", type = CategoryType.EXPENSE, icon = "category", color = "#607D8B", isFavorite = false, order = 7)
    )

    suspend fun seedDefaultCategories(categoryRepo: CategoryRepository) {
        val existing = categoryRepo.getCategories()
        if (existing.isEmpty()) {
            DEFAULT_CATEGORIES.forEach { categoryRepo.insertCategory(it) }
        }
    }

    suspend fun seedSampleData(
        accountRepo: AccountRepository,
        categoryRepo: CategoryRepository,
        transactionRepo: TransactionRepository
    ) {
        // Remove existing items first
        clearAllFinancialData(accountRepo, categoryRepo, transactionRepo)
        
        // Reseed categories
        DEFAULT_CATEGORIES.forEach { categoryRepo.insertCategory(it) }

        // Bank, E-wallet, cash & Credit Card accounts
        val bca = Account(id = "acc-bca", name = "BCA Rekening", type = AccountType.BANK, icon = "account_balance", color = "#0D47A1", initialBalance = BigDecimal("10000000"))
        val gopay = Account(id = "acc-gopay", name = "GoPay Wallet", type = AccountType.E_WALLET, icon = "wallet", color = "#0288D1", initialBalance = BigDecimal("1500000"))
        val cash = Account(id = "acc-cash", name = "Uang Tunai", type = AccountType.TUNAI, icon = "payments", color = "#4CAF50", initialBalance = BigDecimal("350000"))
        val ccBni = Account(id = "acc-cc-bni", name = "CC BNI", type = AccountType.KARTU_KREDIT, icon = "credit_card", color = "#F44336", initialBalance = BigDecimal("0"))

        accountRepo.insertAccount(bca)
        accountRepo.insertAccount(gopay)
        accountRepo.insertAccount(cash)
        accountRepo.insertAccount(ccBni)

        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        // Standard transaction log
        val txs = listOf(
            Transaction(id = UUID.randomUUID().toString(), amount = BigDecimal("6500000"), type = TransactionType.PEMASUKAN, categoryId = "cat-in-gaji", accountId = "acc-bca", note = "Gaji Bulanan Utama", dateTime = now - 5 * oneDay),
            Transaction(id = UUID.randomUUID().toString(), amount = BigDecimal("125000"), type = TransactionType.PENGELUARAN, categoryId = "cat-ex-makan", accountId = "acc-gopay", note = "Makan Siang Sushi Tei", dateTime = now - 4 * oneDay),
            Transaction(id = UUID.randomUUID().toString(), amount = BigDecimal("35000"), type = TransactionType.PENGELUARAN, categoryId = "cat-ex-trans", accountId = "acc-gopay", note = "Grab Ride PP", dateTime = now - 3 * oneDay),
            Transaction(id = UUID.randomUUID().toString(), amount = BigDecimal("500000"), type = TransactionType.TRANSFER, categoryId = null, accountId = "acc-bca", toAccountId = "acc-gopay", adminFee = BigDecimal("2500"), note = "Isi saldo GoPay bulanan", dateTime = now - 2 * oneDay),
            Transaction(id = UUID.randomUUID().toString(), amount = BigDecimal("450000"), type = TransactionType.PENGELUARAN, categoryId = "cat-ex-belanja", accountId = "acc-cc-bni", note = "Sepatu Baru Uniqlo", dateTime = now - 1 * oneDay),
            Transaction(id = UUID.randomUUID().toString(), amount = BigDecimal("150000"), type = TransactionType.PEMASUKAN, categoryId = "cat-in-invest", accountId = "acc-bca", note = "Dividen Reksa Dana", dateTime = now - 6 * oneDay),
            Transaction(id = UUID.randomUUID().toString(), amount = BigDecimal("85000"), type = TransactionType.PENGELUARAN, categoryId = "cat-ex-tagih", accountId = "acc-bca", note = "Tagihan Wifi", dateTime = now - 7 * oneDay)
        )

        txs.forEach { transactionRepo.insertTransaction(it) }
    }

    suspend fun clearAllFinancialData(
        accountRepo: AccountRepository,
        categoryRepo: CategoryRepository,
        transactionRepo: TransactionRepository
    ) {
        // Soft delete all transactions
        transactionRepo.getTransactions().forEach { transactionRepo.softDeleteTransaction(it.id) }
        // Soft delete all accounts
        accountRepo.getAccounts().forEach { accountRepo.softDeleteAccount(it.id) }
        // Soft delete all categories
        categoryRepo.getCategories().forEach { categoryRepo.softDeleteCategory(it.id) }
    }
}
