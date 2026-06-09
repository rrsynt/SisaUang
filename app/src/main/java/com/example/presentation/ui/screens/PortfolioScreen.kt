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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AssetCategory
import com.example.domain.model.AssetHolding
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val holdings by viewModel.assetHoldingsState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showUpdatePriceDialog by remember { mutableStateOf<AssetHolding?>(null) }
    var showDividendDialog by remember { mutableStateOf(false) }

    // Create Holding form states
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AssetCategory.SAHAM) }
    var quantity by remember { mutableStateOf("") }
    var buyPrice by remember { mutableStateOf("") }
    var currentPrice by remember { mutableStateOf("") }
    var nominal by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var maturityMonthsOffset by remember { mutableStateOf("") }
    var cicilan by remember { mutableStateOf("") }

    // Update current price form state
    var newPrice by remember { mutableStateOf("") }

    // Dividend form states
    var divAmount by remember { mutableStateOf("") }
    var divAccountId by remember { mutableStateOf("") }
    var divNote by remember { mutableStateOf("") }

    // Calculated investment portfolio stats (SAHAM, REKSA_DANA, OBLIGASI, EMAS, KRIPTO)
    val investmentHoldings = remember(holdings) {
        holdings.filter {
            !it.deleted && it.category in listOf(
                AssetCategory.SAHAM, AssetCategory.REKSA_DANA, AssetCategory.OBLIGASI,
                AssetCategory.EMAS, AssetCategory.KRIPTO
            )
        }
    }

    val totalBuyCost = remember(investmentHoldings) {
        investmentHoldings.fold(BigDecimal.ZERO) { sum, h -> sum.add(h.quantity.multiply(h.buyPrice)) }
    }
    val totalCurrentValue = remember(investmentHoldings) {
        investmentHoldings.fold(BigDecimal.ZERO) { sum, h -> sum.add(h.currentValuation) }
    }
    val totalPnL = totalCurrentValue.subtract(totalBuyCost)
    val totalPnLPct = if (totalBuyCost > BigDecimal.ZERO) {
        totalPnL.multiply(BigDecimal("100")).divide(totalBuyCost, 2, java.math.RoundingMode.HALF_UP)
    } else BigDecimal.ZERO

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portofolio & Aset", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showDividendDialog = true }) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = "Catat Dividen/Bunga", tint = Color(0xFFFF9800))
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah Aset")
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
            // Portfolio Summary Card (Investasi)
            if (investmentHoldings.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "TOTAL NILAI INVESTASI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = LocalizationUtils.formatCurrency(totalCurrentValue, currencyActive, languageActive),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val pnlColor = if (totalPnL >= BigDecimal.ZERO) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                            val sign = if (totalPnL >= BigDecimal.ZERO) "+" else ""

                            Text(
                                text = "Keuntungan/Kerugian: ${formatAmount(totalPnL, currencyActive, languageActive)} ($sign$totalPnLPct%)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = pnlColor)
                            )
                        }
                    }
                }
            }

            // Aset holdings category lists
            item {
                Text(
                    text = "Daftar Kepemilikan Aset & Utang",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (holdings.filter { !it.deleted }.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada pencatatan aset.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                items(holdings.filter { !it.deleted }) { holding ->
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
                                    val icon = when (holding.category) {
                                        AssetCategory.SAHAM -> Icons.Default.TrendingUp
                                        AssetCategory.REKSA_DANA -> Icons.Default.PieChart
                                        AssetCategory.OBLIGASI -> Icons.Default.Description
                                        AssetCategory.EMAS -> Icons.Default.WorkspacePremium
                                        AssetCategory.KRIPTO -> Icons.Default.CurrencyBitcoin
                                        AssetCategory.DEPOSITO -> Icons.Default.AccessTime
                                        AssetCategory.UTANG -> Icons.Default.RemoveCircleOutline
                                        AssetCategory.PIUTANG -> Icons.Default.AddCircleOutline
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (holding.category == AssetCategory.UTANG) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = holding.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        val catDisplayName = when(holding.category) {
                                            AssetCategory.SAHAM -> if (languageActive == "en") "Stocks" else "Saham"
                                            AssetCategory.REKSA_DANA -> if (languageActive == "en") "Mutual Funds" else "Reksa Dana"
                                            AssetCategory.OBLIGASI -> if (languageActive == "en") "Bonds" else "Obligasi"
                                            AssetCategory.EMAS -> if (languageActive == "en") "Gold" else "Emas"
                                            AssetCategory.KRIPTO -> if (languageActive == "en") "Crypto" else "Kripto"
                                            AssetCategory.DEPOSITO -> if (languageActive == "en") "Time Deposit" else "Deposito"
                                            AssetCategory.UTANG -> if (languageActive == "en") "Debt / Liability" else "Utang"
                                            AssetCategory.PIUTANG -> if (languageActive == "en") "Receivable" else "Piutang"
                                        }
                                        Text(
                                            text = catDisplayName,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                }

                                Row {
                                    // Update price button for investments
                                    if (holding.category in listOf(
                                            AssetCategory.SAHAM, AssetCategory.REKSA_DANA, AssetCategory.OBLIGASI,
                                            AssetCategory.EMAS, AssetCategory.KRIPTO
                                        )) {
                                        IconButton(onClick = { showUpdatePriceDialog = holding }) {
                                            Icon(Icons.Default.Edit, contentDescription = if (languageActive == "en") "Update Price" else "Update Harga")
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteAssetHolding(holding.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = if (languageActive == "en") "Delete" else "Hapus", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Show category specific properties
                            when (holding.category) {
                                AssetCategory.SAHAM, AssetCategory.REKSA_DANA, AssetCategory.OBLIGASI,
                                AssetCategory.EMAS, AssetCategory.KRIPTO -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text((if (languageActive == "en") "Units: " else "Unit: ") + holding.quantity, style = MaterialTheme.typography.bodyMedium)
                                        Text((if (languageActive == "en") "Value: " else "Nilai: ") + formatAmount(holding.currentValuation, currencyActive, languageActive), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text((if (languageActive == "en") "Avg: " else "Rata-rata: ") + formatAmount(holding.buyPrice, currencyActive, languageActive), style = MaterialTheme.typography.bodySmall)
                                        Text((if (languageActive == "en") "Current: " else "Harga Kini: ") + formatAmount(holding.currentPrice, currencyActive, languageActive), style = MaterialTheme.typography.bodySmall)
                                    }

                                    val pnl = holding.profitLossAmount
                                    val pnlPct = holding.profitLossPercentage
                                    val sign = if (pnl >= BigDecimal.ZERO) "+" else ""
                                    val pnlCol = if (pnl >= BigDecimal.ZERO) Color(0xFF00C853) else MaterialTheme.colorScheme.error

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "PnL: ${formatAmount(pnl, currencyActive, languageActive)} ($sign$pnlPct%)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = pnlCol)
                                    )
                                }
                                AssetCategory.DEPOSITO -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text((if (languageActive == "en") "Nominal: " else "Nominal: ") + formatAmount(holding.nominal, currencyActive, languageActive), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text((if (languageActive == "en") "Interest: " else "Bunga: ") + "${holding.interestRate}%", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    holding.maturityDate?.let {
                                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale(languageActive))
                                        val projectedInterest = holding.nominal.multiply(holding.interestRate).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text((if (languageActive == "en") "Due Date: " else "Jatuh Tempo: ") + sdf.format(Date(it)), style = MaterialTheme.typography.bodySmall)
                                        Text((if (languageActive == "en") "Projected Annual Interest: " else "Proyeksi Bunga Tahunan: ") + formatAmount(projectedInterest, currencyActive, languageActive), style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF00C853), fontWeight = FontWeight.SemiBold))
                                    }
                                }
                                AssetCategory.UTANG, AssetCategory.PIUTANG -> {
                                    val label = if (holding.category == AssetCategory.UTANG) {
                                        if (languageActive == "en") "Debt" else "Utang"
                                    } else {
                                        if (languageActive == "en") "Receivable" else "Piutang"
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text((if (languageActive == "en") "$label Amount: " else "Nominal $label: ") + formatAmount(holding.nominal, currencyActive, languageActive), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        holding.cicilan?.let {
                                            Text((if (languageActive == "en") "Installment: " else "Cicilan: ") + formatAmount(it, currencyActive, languageActive) + (if (languageActive == "en") "/mo" else "/bln"), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    holding.maturityDate?.let {
                                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale(languageActive))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text((if (languageActive == "en") "Due Date: " else "Tenggat Jatuh Tempo: ") + sdf.format(Date(it)), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (languageActive == "en") "Record Asset / Liability" else "Pencatatan Aset / Utang") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(if (languageActive == "en") "Asset/Liability Name" else "Nama Aset/Utang") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        var catExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val catValName = when(category) {
                                AssetCategory.SAHAM -> if (languageActive == "en") "Stocks" else "Saham"
                                AssetCategory.REKSA_DANA -> if (languageActive == "en") "Mutual Funds" else "Reksa Dana"
                                AssetCategory.OBLIGASI -> if (languageActive == "en") "Bonds" else "Obligasi"
                                AssetCategory.EMAS -> if (languageActive == "en") "Gold" else "Emas"
                                AssetCategory.KRIPTO -> if (languageActive == "en") "Crypto" else "Kripto"
                                AssetCategory.DEPOSITO -> if (languageActive == "en") "Time Deposit" else "Deposito"
                                AssetCategory.UTANG -> if (languageActive == "en") "Debt / Liability" else "Utang"
                                AssetCategory.PIUTANG -> if (languageActive == "en") "Receivable" else "Piutang"
                            }
                            OutlinedTextField(
                                value = catValName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (languageActive == "en") "Asset Category" else "Kategori Aset") },
                                trailingIcon = { IconButton(onClick = { catExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                AssetCategory.values().forEach { cat ->
                                    val catLabel = when(cat) {
                                        AssetCategory.SAHAM -> if (languageActive == "en") "Stocks" else "Saham"
                                        AssetCategory.REKSA_DANA -> if (languageActive == "en") "Mutual Funds" else "Reksa Dana"
                                        AssetCategory.OBLIGASI -> if (languageActive == "en") "Bonds" else "Obligasi"
                                        AssetCategory.EMAS -> if (languageActive == "en") "Gold" else "Emas"
                                        AssetCategory.KRIPTO -> if (languageActive == "en") "Crypto" else "Kripto"
                                        AssetCategory.DEPOSITO -> if (languageActive == "en") "Time Deposit" else "Deposito"
                                        AssetCategory.UTANG -> if (languageActive == "en") "Debt / Liability" else "Utang"
                                        AssetCategory.PIUTANG -> if (languageActive == "en") "Receivable" else "Piutang"
                                    }
                                    DropdownMenuItem(
                                        text = { Text(catLabel) },
                                        onClick = {
                                            category = cat
                                            catExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (category in listOf(
                            AssetCategory.SAHAM, AssetCategory.REKSA_DANA, AssetCategory.OBLIGASI,
                            AssetCategory.EMAS, AssetCategory.KRIPTO
                        )) {
                        item {
                            OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = it },
                                label = { Text(if (languageActive == "en") "Units / Quantity" else "Jumlah Unit / Lot") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = buyPrice,
                                onValueChange = { buyPrice = it },
                                label = { Text(if (languageActive == "en") "Average Buy Price" else "Harga Beli Rata-rata") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = currentPrice,
                                onValueChange = { currentPrice = it },
                                label = { Text(if (languageActive == "en") "Current Price" else "Harga Sekarang") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (category == AssetCategory.DEPOSITO) {
                        item {
                            OutlinedTextField(
                                value = nominal,
                                onValueChange = { nominal = it },
                                label = { Text(if (languageActive == "en") "Deposit Principal" else "Nominal Deposito") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = interestRate,
                                onValueChange = { interestRate = it },
                                label = { Text(if (languageActive == "en") "Deposit Interest (% p.a.)" else "Bunga Deposito (% per tahun)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = maturityMonthsOffset,
                                onValueChange = { maturityMonthsOffset = it },
                                label = { Text(if (languageActive == "en") "Duration (Months, e.g., 12)" else "Jangka Waktu (Bulan, contoh: 12)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (category == AssetCategory.UTANG || category == AssetCategory.PIUTANG) {
                        item {
                            OutlinedTextField(
                                value = nominal,
                                onValueChange = { nominal = it },
                                label = { Text(if (languageActive == "en") "Principal Amount" else "Nominal Principal") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = cicilan,
                                onValueChange = { cicilan = it },
                                label = { Text(if (languageActive == "en") "Monthly Installment (Optional)" else "Cicilan Bulanan (Opsional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = maturityMonthsOffset,
                                onValueChange = { maturityMonthsOffset = it },
                                label = { Text(if (languageActive == "en") "Duration/Due (Months, e.g., 6)" else "Tenggat Waktu (Bulan, contoh: 6)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotEmpty()) {
                            val newHolding = AssetHolding(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                category = category,
                                quantity = quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                                buyPrice = buyPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                                currentPrice = currentPrice.toBigDecimalOrNull() ?: currentPrice.toBigDecimalOrNull() ?: buyPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                                nominal = nominal.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                                interestRate = interestRate.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                                maturityDate = maturityMonthsOffset.toLongOrNull()?.let {
                                    System.currentTimeMillis() + (it * 30L * 24 * 60 * 60 * 1000)
                                },
                                cicilan = cicilan.toBigDecimalOrNull(),
                                isCompleted = false,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.insertAssetHolding(newHolding)
                            showCreateDialog = false
                            // reset
                            name = ""
                            quantity = ""
                            buyPrice = ""
                            currentPrice = ""
                            nominal = ""
                            interestRate = ""
                            maturityMonthsOffset = ""
                            cicilan = ""
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

    // Update Price Dialog
    if (showUpdatePriceDialog != null) {
        val target = showUpdatePriceDialog!!
        AlertDialog(
            onDismissRequest = { showUpdatePriceDialog = null },
            title = { Text(if (languageActive == "en") "Update Current Price" else "Update Harga Terkini") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text((if (languageActive == "en") "Asset: " else "Aset: ") + target.name)
                    Text((if (languageActive == "en") "Buy Price: " else "Harga Beli: ") + formatAmount(target.buyPrice, currencyActive, languageActive))
                    OutlinedTextField(
                        value = newPrice,
                        onValueChange = { newPrice = it },
                        label = { Text(if (languageActive == "en") "Current Price" else "Harga Sekarang") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val priceNum = newPrice.toBigDecimalOrNull()
                        if (priceNum != null) {
                            viewModel.insertAssetHolding(target.copy(
                                currentPrice = priceNum,
                                updatedAt = System.currentTimeMillis()
                            ))
                            showUpdatePriceDialog = null
                            newPrice = ""
                        }
                    }
                ) {
                    Text(if (languageActive == "en") "Update" else "Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdatePriceDialog = null }) {
                    Text(if (languageActive == "en") "Cancel" else "Batal")
                }
            }
        )
    }

    // Dividend Dialog
    if (showDividendDialog) {
        AlertDialog(
            onDismissRequest = { showDividendDialog = false },
            title = { Text(if (languageActive == "en") "Record Dividend / Coupon / Interest" else "Catat Dividen / Kupon / Bunga") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = divAmount,
                        onValueChange = { divAmount = it },
                        label = { Text(if (languageActive == "en") "Income Amount" else "Nominal Pendapatan") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = divNote,
                        onValueChange = { divNote = it },
                        label = { Text(if (languageActive == "en") "Note (e.g. BBRI Dividend)" else "Catatan (contoh: Dividen BBRI)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    var accExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = accounts.find { it.id == divAccountId }?.name ?: (if (languageActive == "en") "Select Receiving Wallet" else "Pilih Dompet Penerima"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (languageActive == "en") "Wallet" else "Dompet") },
                            trailingIcon = { IconButton(onClick = { accExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        divAccountId = acc.id
                                        accExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountNum = divAmount.toBigDecimalOrNull()
                        if (amountNum != null && divAccountId.isNotEmpty()) {
                            // Insert a transaction of type PEMASUKAN with Dividend note
                            val tx = com.example.domain.model.Transaction(
                                id = UUID.randomUUID().toString(),
                                amount = amountNum,
                                type = com.example.domain.model.TransactionType.PEMASUKAN,
                                categoryId = null, // Will use default categories or no category
                                accountId = divAccountId,
                                note = divNote.ifEmpty { if (languageActive == "en") "Investment Dividend/Coupon/Interest" else "Dividen/Kupon/Bunga Investasi" },
                                dateTime = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.insertTransaction(tx)
                            showDividendDialog = false
                            divAmount = ""
                            divAccountId = ""
                            divNote = ""
                        }
                    }
                ) {
                    Text(if (languageActive == "en") "Save" else "Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDividendDialog = false }) {
                    Text(if (languageActive == "en") "Cancel" else "Batal")
                }
            }
        )
    }
}

private fun formatAmount(amount: BigDecimal, currencyCode: String, languageActive: String): String {
    return LocalizationUtils.formatCurrency(amount, currencyCode, languageActive)
}
