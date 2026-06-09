package com.example.presentation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.utils.ExportUtils
import com.example.domain.utils.FinanceCalculator
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

enum class ReportPeriod {
    SEMUA, BULAN_INI, BULAN_LALU, TAHUN_INI
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.SEMUA) }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    val allTags = remember(transactions) {
        transactions.flatMap { it.tags }.filter { it.isNotEmpty() }.distinct()
    }

    val periodStartAndEnd = remember(selectedPeriod) {
        val cal = Calendar.getInstance()
        when (selectedPeriod) {
            ReportPeriod.SEMUA -> Pair(0L, Long.MAX_VALUE)
            ReportPeriod.BULAN_INI -> {
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
            ReportPeriod.BULAN_LALU -> {
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
            ReportPeriod.TAHUN_INI -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
        }
    }

    // Filter transactions
    val filteredTransactions = remember(transactions, selectedCategoryId, selectedAccountId, selectedTag, periodStartAndEnd) {
        transactions.filter {
            !it.deleted &&
            (selectedCategoryId == null || it.categoryId == selectedCategoryId) &&
            (selectedAccountId == null || it.accountId == selectedAccountId) &&
            (selectedTag == null || it.tags.contains(selectedTag)) &&
            it.dateTime in periodStartAndEnd.first..periodStartAndEnd.second
        }
    }

    // Calculations
    val totalIncome = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == TransactionType.PEMASUKAN }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
    }
    val totalExpense = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == TransactionType.PENGELUARAN }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
    }

    // Habit analysis: top expense category
    val categoryExpenses = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == TransactionType.PENGELUARAN && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { entry -> entry.value.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) } }
            .toList()
            .sortedByDescending { it.second }
    }

    val topCategory = remember(categoryExpenses, categories) {
        categoryExpenses.firstOrNull()?.let { (catId, amt) ->
            Pair(categories.find { it.id == catId }?.name ?: (if (languageActive == "en") "Others" else "Lainnya"), amt)
        }
    }

    // MoM comparison: Compare this month vs last month
    val cal = Calendar.getInstance()
    val thisMonthStart = cal.apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis

    val lastMonthTransactions = remember(transactions) {
        val lastMonthCalStart = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val lastMonthCalEnd = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.MILLISECOND, -1)
        }.timeInMillis

        transactions.filter { !it.deleted && it.dateTime in lastMonthCalStart..lastMonthCalEnd }
    }

    val lastMonthExpenses = remember(lastMonthTransactions) {
        lastMonthTransactions.filter { it.type == TransactionType.PENGELUARAN }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
    }

    val thisMonthExpenses = remember(transactions, thisMonthStart) {
        transactions.filter { !it.deleted && it.dateTime >= thisMonthStart && it.type == TransactionType.PENGELUARAN }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
    }

    val momChange = thisMonthExpenses.subtract(lastMonthExpenses)
    val momChangePct = if (lastMonthExpenses > BigDecimal.ZERO) {
        momChange.multiply(BigDecimal("100")).divide(lastMonthExpenses, 2, java.math.RoundingMode.HALF_UP)
    } else BigDecimal.ZERO

    // YoY comparison: Compare this year vs last year
    val thisYearStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis

    val lastYearStart = Calendar.getInstance().apply {
        add(Calendar.YEAR, -1)
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis

    val lastYearEnd = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        add(Calendar.MILLISECOND, -1)
    }.timeInMillis

    val lastYearExpenses = remember(transactions, lastYearStart, lastYearEnd) {
        transactions.filter { !it.deleted && it.dateTime in lastYearStart..lastYearEnd && it.type == TransactionType.PENGELUARAN }
    }

    val lastYearExpensesSum = remember(lastYearExpenses) {
        lastYearExpenses.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
    }

    val thisYearExpensesSum = remember(transactions, thisYearStart) {
        transactions.filter { !it.deleted && it.dateTime >= thisYearStart && it.type == TransactionType.PENGELUARAN }.fold(BigDecimal.ZERO) { sum, tx -> sum.add(tx.amount) }
    }

    val yoyChange = thisYearExpensesSum.subtract(lastYearExpensesSum)
    val yoyChangePct = if (lastYearExpensesSum > BigDecimal.ZERO) {
        yoyChange.multiply(BigDecimal("100")).divide(lastYearExpensesSum, 2, java.math.RoundingMode.HALF_UP)
    } else BigDecimal.ZERO

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (languageActive == "en") "Reports & Analytics" else "Laporan & Analitik", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = if (languageActive == "en") "Back" else "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val file = ExportUtils.exportToCsv(context, filteredTransactions)
                        if (file != null) {
                            ExportUtils.shareFile(context, file, "text/csv")
                        } else {
                            val msg = if (languageActive == "en") "Failed to export CSV" else "Gagal ekspor CSV"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = if (languageActive == "en") "Share CSV" else "Bagikan CSV")
                    }
                    IconButton(onClick = {
                        val file = ExportUtils.exportToPdf(context, filteredTransactions)
                        if (file != null) {
                            ExportUtils.shareFile(context, file, "application/pdf")
                        } else {
                            val msg = if (languageActive == "en") "Failed to export PDF" else "Gagal ekspor PDF"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = if (languageActive == "en") "Export PDF" else "Ekspor PDF")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filters row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category filter dropdown
                    var catExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { catExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = selectedCategoryId?.let { id -> categories.find { it.id == id }?.name } ?: (if (languageActive == "en") "All Categories" else "Semua Kategori"),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "All Categories" else "Semua Kategori") }, onClick = { selectedCategoryId = null; catExpanded = false })
                            categories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { selectedCategoryId = cat.id; catExpanded = false })
                            }
                        }
                    }

                    // Account filter dropdown
                    var accExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { accExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = selectedAccountId?.let { id -> accounts.find { it.id == id }?.name } ?: (if (languageActive == "en") "All Accounts" else "Semua Dompet"),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "All Accounts" else "Semua Dompet") }, onClick = { selectedAccountId = null; accExpanded = false })
                            accounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedAccountId = acc.id; accExpanded = false })
                            }
                        }
                    }
                }
            }

            // Period and Tag filters row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Period filter dropdown
                    var periodExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { periodExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = when (selectedPeriod) {
                                    ReportPeriod.SEMUA -> if (languageActive == "en") "All Time" else "Semua Waktu"
                                    ReportPeriod.BULAN_INI -> if (languageActive == "en") "This Month" else "Bulan Ini"
                                    ReportPeriod.BULAN_LALU -> if (languageActive == "en") "Last Month" else "Bulan Lalu"
                                    ReportPeriod.TAHUN_INI -> if (languageActive == "en") "This Year" else "Tahun Ini"
                                },
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "All Time" else "Semua Waktu") }, onClick = { selectedPeriod = ReportPeriod.SEMUA; periodExpanded = false })
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "This Month" else "Bulan Ini") }, onClick = { selectedPeriod = ReportPeriod.BULAN_INI; periodExpanded = false })
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "Last Month" else "Bulan Lalu") }, onClick = { selectedPeriod = ReportPeriod.BULAN_LALU; periodExpanded = false })
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "This Year" else "Tahun Ini") }, onClick = { selectedPeriod = ReportPeriod.TAHUN_INI; periodExpanded = false })
                        }
                    }

                    // Tag filter dropdown
                    var tagExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { tagExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = selectedTag ?: (if (languageActive == "en") "All Tags" else "Semua Tag"),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(expanded = tagExpanded, onDismissRequest = { tagExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "All Tags" else "Semua Tag") }, onClick = { selectedTag = null; tagExpanded = false })
                            allTags.forEach { tag ->
                                DropdownMenuItem(text = { Text(tag) }, onClick = { selectedTag = tag; tagExpanded = false })
                            }
                        }
                    }
                }
            }

            // Summary Card
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = if (languageActive == "en") "Cash Flow Summary" else "Ringkasan Arus Kas", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(if (languageActive == "en") "Total Income" else "Total Pemasukan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = formatAmount(totalIncome, currencyActive, languageActive),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF00C853), fontFamily = FontFamily.Monospace)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (languageActive == "en") "Total Expense" else "Total Pengeluaran", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = formatAmount(totalExpense, currencyActive, languageActive),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }

            // Habit Analysis Card
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = if (languageActive == "en") "Spending Habit Analysis" else "Analisis Kebiasaan Belanja", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(12.dp))

                        if (topCategory != null) {
                            Text(
                                text = if (languageActive == "en") "• Highest Spending Category: ${topCategory.first} (${formatAmount(topCategory.second, currencyActive, languageActive)})" else "• Kategori Terboros: ${topCategory.first} (${formatAmount(topCategory.second, currencyActive, languageActive)})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(text = if (languageActive == "en") "• No expense data yet." else "• Belum ada data pengeluaran.", style = MaterialTheme.typography.bodyMedium)
                        }

                        // MoM summary
                        val sign = if (momChange >= BigDecimal.ZERO) "+" else ""
                        val momColor = if (momChange >= BigDecimal.ZERO) MaterialTheme.colorScheme.error else Color(0xFF00C853)
                        val trendWord = if (languageActive == "en") {
                            if (momChange >= BigDecimal.ZERO) "increased" else "decreased"
                        } else {
                            if (momChange >= BigDecimal.ZERO) "meningkat" else "menurun"
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val momText = if (languageActive == "en") {
                            "• Expenses this month $trendWord by ${formatAmount(momChange.abs(), currencyActive, languageActive)} ($sign$momChangePct%) compared to last month."
                        } else {
                            "• Pengeluaran bulan ini $trendWord ${formatAmount(momChange.abs(), currencyActive, languageActive)} ($sign$momChangePct%) dibandingkan bulan lalu."
                        }
                        Text(
                            text = momText,
                            style = MaterialTheme.typography.bodyMedium.copy(color = momColor, fontWeight = FontWeight.SemiBold)
                        )

                        // YoY summary
                        val yoySign = if (yoyChange >= BigDecimal.ZERO) "+" else ""
                        val yoyColor = if (yoyChange >= BigDecimal.ZERO) MaterialTheme.colorScheme.error else Color(0xFF00C853)
                        val yoyTrendWord = if (languageActive == "en") {
                            if (yoyChange >= BigDecimal.ZERO) "increased" else "decreased"
                        } else {
                            if (yoyChange >= BigDecimal.ZERO) "meningkat" else "menurun"
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val yoyText = if (languageActive == "en") {
                            "• Expenses this year $yoyTrendWord by ${formatAmount(yoyChange.abs(), currencyActive, languageActive)} ($yoySign$yoyChangePct%) compared to last year."
                        } else {
                            "• Pengeluaran tahun ini $yoyTrendWord ${formatAmount(yoyChange.abs(), currencyActive, languageActive)} ($yoySign$yoyChangePct%) dibandingkan tahun lalu."
                        }
                        Text(
                            text = yoyText,
                            style = MaterialTheme.typography.bodyMedium.copy(color = yoyColor, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // Donut Chart Segment
            if (categoryExpenses.isNotEmpty()) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (languageActive == "en") "Expense Distribution by Category" else "Distribusi Pengeluaran Per Kategori",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Box(
                                modifier = Modifier.size(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val chartColors = listOf(
                                    Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
                                    Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
                                    Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
                                    Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F)
                                )
                                val totalExpensesFloat = categoryExpenses.fold(BigDecimal.ZERO) { acc, d -> acc.add(d.second) }.toFloat()

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    var startAngle = -90f
                                    categoryExpenses.forEachIndexed { idx, item ->
                                        val valRatio = item.second.toFloat()
                                        val sweepAngle = if (totalExpensesFloat > 0f) (valRatio / totalExpensesFloat) * 360f else 0f
                                        
                                        drawArc(
                                            color = chartColors[idx % chartColors.size],
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round),
                                            size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
                                            topLeft = Offset(12.dp.toPx(), 12.dp.toPx())
                                        )
                                        startAngle += sweepAngle
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Legend
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val chartColors = listOf(
                                    Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
                                    Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
                                    Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
                                    Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F)
                                )
                                categoryExpenses.forEachIndexed { idx, item ->
                                    val catName = categories.find { it.id == item.first }?.name ?: (if (languageActive == "en") "Others" else "Lainnya")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(chartColors[idx % chartColors.size], RoundedCornerShape(2.dp))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = catName, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Text(
                                            text = formatAmount(item.second, currencyActive, languageActive),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatAmount(amount: BigDecimal, currencyCode: String, languageActive: String): String {
    return LocalizationUtils.formatCurrency(amount, currencyCode, languageActive)
}
