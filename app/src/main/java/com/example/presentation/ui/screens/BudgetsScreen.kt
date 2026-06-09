package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriod
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.ui.components.IconMapper
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val budgets by viewModel.budgetsState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val safeToSpend by viewModel.safeToSpendState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    // Form states for creating a new budget
    var name by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(BudgetPeriod.BULANAN) }
    var carryOver by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (languageActive == "en") "Budgets & Envelopes" else "Anggaran & Amplop", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = if (languageActive == "en") "Back" else "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = if (languageActive == "en") "Add Budget" else "Tambah Anggaran")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
        ) {
            // Summary Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (languageActive == "en") "SAFE DAILY SPEND LIMIT" else "SISA HARIAN AMAN DIBELANJAKAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = LocalizationUtils.formatCurrency(safeToSpend, currencyActive, languageActive) + (if (languageActive == "en") " / day" else " / hari"),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (languageActive == "en") "Calculated from remaining monthly budget divided by remaining days in this month." else "Dihitung dari sisa anggaran bulanan dibagi sisa hari bulan ini.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            Text(
                text = if (languageActive == "en") "Budget Envelopes List" else "Daftar Amplop Anggaran",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (budgets.filter { !it.deleted }.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (languageActive == "en") "No budgets created yet." else "Belum ada anggaran dibuat.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(budgets.filter { !it.deleted }) { budget ->
                        // Calculate spent in budget period
                        val spent = remember(transactions, budget) {
                            val range = getPeriodDateRange(budget.period, budget.customStartDate, budget.customEndDate)
                            transactions.filter {
                                !it.deleted &&
                                it.dateTime in range.first..range.second &&
                                it.type == com.example.domain.model.TransactionType.PENGELUARAN &&
                                (budget.categoryId == null || it.categoryId == budget.categoryId)
                            }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
                        }

                        // Determine available limits incorporating carry-over
                        val available = remember(transactions, budget) {
                            var limit = budget.amount
                            if (budget.carryOver) {
                                val prevRange = getPreviousPeriodDateRange(budget.period, budget.customStartDate, budget.customEndDate)
                                val prevSpent = transactions.filter {
                                    !it.deleted &&
                                    it.dateTime in prevRange.first..prevRange.second &&
                                    it.type == com.example.domain.model.TransactionType.PENGELUARAN &&
                                    (budget.categoryId == null || it.categoryId == budget.categoryId)
                                }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
                                val remaining = budget.amount.subtract(prevSpent)
                                if (remaining > BigDecimal.ZERO) {
                                    limit = limit.add(remaining)
                                }
                            }
                            limit
                        }

                        val progress = if (available > BigDecimal.ZERO) {
                            spent.divide(available, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                        } else 0f

                        val thresholdExceeded = spent >= available
                        val thresholdWarning = spent >= available.multiply(BigDecimal("0.8"))

                        val progressColor = when {
                            thresholdExceeded -> MaterialTheme.colorScheme.error
                            thresholdWarning -> Color(0xFFFF9800) // Orange Warning
                            else -> Color(0xFF00C853) // Green Normal
                        }

                        ElevatedCard(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val budgetCat = categories.find { it.id == budget.categoryId }
                                        val iconName = budgetCat?.icon ?: "folder"
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconMapper.getIconByName(iconName),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = budget.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                            val periodName = when(budget.period) {
                                                com.example.domain.model.BudgetPeriod.MINGGUAN -> if (languageActive == "en") "Weekly" else "Mingguan"
                                                com.example.domain.model.BudgetPeriod.BULANAN -> if (languageActive == "en") "Monthly" else "Bulanan"
                                                com.example.domain.model.BudgetPeriod.KUSTOM -> if (languageActive == "en") "Custom" else "Kustom"
                                            }
                                            Text(
                                                text = "$periodName • ${budgetCat?.name ?: (if (languageActive == "en") "All Categories" else "Semua Kategori")}",
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }

                                    IconButton(onClick = { viewModel.deleteBudget(budget.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = if (languageActive == "en") "Delete" else "Hapus", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = (if (languageActive == "en") "Spent: " else "Terpakai: ") + LocalizationUtils.formatCurrency(spent, currencyActive, languageActive),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = (if (languageActive == "en") "Limit: " else "Limit: ") + LocalizationUtils.formatCurrency(available, currencyActive, languageActive),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = progressColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )

                                if (thresholdExceeded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (languageActive == "en") "⚠ Budget exceeded!" else "⚠ Anggaran terlampaui!",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                } else if (thresholdWarning) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (languageActive == "en") "⚠ Spent exceeded 80% of limit." else "⚠ Terpakai melebihi 80% ambang batas.",
                                        color = Color(0xFFFF9800),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (languageActive == "en") "Add New Budget" else "Tambah Anggaran Baru") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (languageActive == "en") "Budget Name" else "Nama Anggaran") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Selector
                    var catExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = categories.find { it.id == selectedCategoryId }?.name ?: (if (languageActive == "en") "All Categories" else "Semua Kategori"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (languageActive == "en") "Category" else "Kategori") },
                            trailingIcon = { IconButton(onClick = { catExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (languageActive == "en") "All Categories" else "Semua Kategori") },
                                onClick = {
                                    selectedCategoryId = null
                                    catExpanded = false
                                }
                            )
                            categories.filter { it.type == com.example.domain.model.CategoryType.EXPENSE }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(if (languageActive == "en") "Budget Amount" else "Nominal Anggaran") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Period Selector
                    var periodExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = when (period) {
                                BudgetPeriod.BULANAN -> if (languageActive == "en") "Monthly" else "Bulanan"
                                BudgetPeriod.MINGGUAN -> if (languageActive == "en") "Weekly" else "Mingguan"
                                BudgetPeriod.KUSTOM -> if (languageActive == "en") "Custom" else "Kustom"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (languageActive == "en") "Period" else "Periode") },
                            trailingIcon = { IconButton(onClick = { periodExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "Monthly" else "Bulanan") }, onClick = { period = BudgetPeriod.BULANAN; periodExpanded = false })
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "Weekly" else "Mingguan") }, onClick = { period = BudgetPeriod.MINGGUAN; periodExpanded = false })
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = carryOver, onCheckedChange = { carryOver = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (languageActive == "en") "Enable Carry-over (Unused budget rolls over)" else "Aktifkan Carry-over (Sisa bulan lalu masuk limit)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        if (name.isNotEmpty() && numAmount > BigDecimal.ZERO) {
                            val newBudget = Budget(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                categoryId = selectedCategoryId,
                                amount = numAmount,
                                period = period,
                                carryOver = carryOver,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.insertBudget(newBudget)
                            showCreateDialog = false
                            // reset
                            name = ""
                            selectedCategoryId = null
                            amount = ""
                            period = BudgetPeriod.BULANAN
                            carryOver = false
                        }
                    }
                ) {
                    Text(if (languageActive == "en") "Save" else "Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(if (languageActive == "en") "Cancel" else "Batal")
                }
            }
        )
    }
}

// Period range calculators
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
        BudgetPeriod.KUSTOM -> Pair(customStart ?: 0L, customEnd ?: System.currentTimeMillis())
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
