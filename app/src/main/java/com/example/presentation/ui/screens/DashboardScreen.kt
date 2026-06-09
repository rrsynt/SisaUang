package com.example.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.domain.model.SyncMeta
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import java.text.NumberFormat
import java.util.Locale
import com.example.domain.model.AccountType
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.utils.FinanceCalculator
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.ui.components.IconMapper
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToNewTx: (Boolean) -> Unit,
    onNavigateToEditTx: (String) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToForecast: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToDraftsInbox: () -> Unit,
    onNavigateToNotifSettings: () -> Unit,
    onNavigateToCsvImport: () -> Unit,
    onNavigateToOcrReceipt: () -> Unit
) {
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val netWorth by viewModel.netWorthState.collectAsState()
    val monthlyFlow by viewModel.monthlyFlowState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val safeToSpend by viewModel.safeToSpendState.collectAsState()
    val forecast by viewModel.forecastState.collectAsState()
    val drafts by viewModel.draftsState.collectAsState()
    val syncMeta by viewModel.syncMetaState.collectAsState()
    val syncRunningMsg by viewModel.syncRunningState.collectAsState()
    val financialHealth by viewModel.financialHealthState.collectAsState()
    var isFabExpanded by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    val recentTransactions = remember(transactions) {
        transactions.filter { !it.deleted }.take(5)
    }

    val composition = remember(accounts) {
        FinanceCalculator.calculateAssetComposition(accounts)
    }

    val chartAnimationProgress = remember { Animatable(0f) }
    LaunchedEffect(composition) {
        chartAnimationProgress.snapTo(0f)
        chartAnimationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            )
        )
    }

    val isTourCompleted by viewModel.isDashboardTourCompleted.collectAsState()
    var currentTourStep by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onNavigateToAccounts) {
                        Icon(imageVector = Icons.Default.AccountBalance, contentDescription = "Manage Accounts")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isFabExpanded) {
                    val unconfirmedDraftsCount = drafts.filter { !it.isConfirmed }.size
                    
                    // Drafts Inbox Fab Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = (if (languageActive == "en") "Draft Inbox" else "Draft Masuk") + if (unconfirmedDraftsCount > 0) " ($unconfirmedDraftsCount)" else "",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isFabExpanded = false
                                onNavigateToDraftsInbox()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unconfirmedDraftsCount > 0) {
                                        Badge { Text(unconfirmedDraftsCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // OCR Receipt Fab Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (languageActive == "en") "Scan Receipt OCR" else "Pindai Struk OCR",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isFabExpanded = false
                                onNavigateToOcrReceipt()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }

                    // CSV Import Fab Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (languageActive == "en") "Import CSV" else "Impor CSV",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isFabExpanded = false
                                onNavigateToCsvImport()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Bicara Langsung Fab Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (languageActive == "en") "Voice Entry" else "Bicara Langsung",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isFabExpanded = false
                                onNavigateToNewTx(true)
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Catat Manual Fab Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (languageActive == "en") "Manual Entry" else "Catat Manual",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isFabExpanded = false
                                onNavigateToNewTx(false)
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isFabExpanded = !isFabExpanded
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Add Menu",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Google Sheets Sync Status Bar
            if (syncMeta != null || syncRunningMsg != null) {
                item {
                    SyncStatusBar(
                        syncMeta = syncMeta,
                        syncRunningMsg = syncRunningMsg,
                        languageActive = languageActive,
                        onSyncClick = { viewModel.runManualSync() }
                    )
                }
            }

            // Net Worth Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.dashboard_net_worth),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = LocalizationUtils.formatCurrency(netWorth, currencyActive, languageActive),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Financial Health Card
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (languageActive == "en") "Financial Health" else "Kesehatan Finansial",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val statusColor = when (financialHealth.rating) {
                                "SEHAT" -> Color(0xFF00C853)
                                "WASPADA" -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                            val ratingText = if (languageActive == "en") {
                                when (financialHealth.rating) {
                                    "SEHAT" -> "HEALTHY"
                                    "WASPADA" -> "WARNING"
                                    else -> "CRITICAL"
                                }
                            } else {
                                financialHealth.rating
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = ratingText,
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Health Score Progress Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (languageActive == "en") "Score" else "Skor"}: ${financialHealth.score}/100",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.width(95.dp)
                            )
                            LinearProgressIndicator(
                                progress = { financialHealth.score / 100f },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp),
                                color = when (financialHealth.rating) {
                                    "SEHAT" -> Color(0xFF00C853)
                                    "WASPADA" -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Savings Rate
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (languageActive == "en") "Savings Rate" else "Rasio Menabung",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val ratePct = (financialHealth.savingsRate * 100).toInt()
                                Text(
                                    text = "$ratePct%",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Target >= 20%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            
                            // Emergency Fund
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (languageActive == "en") "Emergency Fund" else "Dana Darurat",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val efMonths = String.format(Locale.US, "%.1f", financialHealth.emergencyFundMonths)
                                Text(
                                    text = if (languageActive == "en") "$efMonths months" else "$efMonths bulan",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (languageActive == "en") "Target >= 3 mos" else "Target >= 3 bln",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Recommendation
                        val displayRec = if (languageActive == "en") {
                            when {
                                financialHealth.score >= 75 -> "Your finances are healthy. Maintain your current saving habits and emergency fund."
                                financialHealth.score >= 40 -> "Your finances are stable, but try to increase monthly savings or expand your emergency fund."
                                else -> "Critical financial condition. Restrict non-essential expenses and focus on building an emergency fund immediately."
                            }
                        } else {
                            financialHealth.recommendation
                        }
                        Text(
                            text = displayRec,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Safe to Spend Card
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(if (languageActive == "en") "Safe Daily Spend Limit" else "Sisa Harian Aman Dibelanjakan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${formatAmount(safeToSpend, currencyActive, languageActive)} ${if (languageActive == "en") "/ day" else "/ hari"}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF00C853))
                            )
                        }
                        Icon(Icons.Default.Savings, contentDescription = null, tint = Color(0xFF00C853))
                    }
                }
            }

            // Forecast Deficit Warning Card
            if (forecast.isDeficitProjected) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (languageActive == "en") "Deficit Projection: You are projected to deficit ${formatAmount(forecast.deficitAmount, currencyActive, languageActive)} before payday!" else "Proyeksi Defisit: Anda diperkirakan defisit ${formatAmount(forecast.deficitAmount, currencyActive, languageActive)} sebelum gajian!",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Financial Services Section
            item {
                Text(
                    text = if (languageActive == "en") "Financial Services" else "Layanan Keuangan",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(vertical = 16.dp, horizontal = 12.dp)
                ) {
                    val services = listOf(
                        ServiceItem(if (languageActive == "en") "Budgets" else "Anggaran", Icons.Default.Category) { onNavigateToBudgets() },
                        ServiceItem(if (languageActive == "en") "Goals" else "Target", Icons.Default.Flag) { onNavigateToGoals() },
                        ServiceItem(if (languageActive == "en") "Portfolio" else "Portofolio", Icons.Default.PieChart) { onNavigateToPortfolio() },
                        ServiceItem(if (languageActive == "en") "Recurring Bills" else "Tagihan Rutin", Icons.Default.Repeat) { onNavigateToRecurring() },
                        ServiceItem(if (languageActive == "en") "Forecast" else "Proyeksi", Icons.Default.TrendingUp) { onNavigateToForecast() },
                        ServiceItem(if (languageActive == "en") "Reports" else "Laporan", Icons.Default.BarChart) { onNavigateToReports() },
                        ServiceItem(if (languageActive == "en") "Auto Notif" else "Otomasi Notif", Icons.Default.NotificationsActive) { onNavigateToNotifSettings() }
                    )
                    
                    val chunkSize = 4
                    val rows = services.chunked(chunkSize)
                    
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowItems.forEach { svc ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { svc.onClick() }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = svc.icon,
                                            contentDescription = svc.title,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = svc.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Pad empty cells if row is not full
                            if (rowItems.size < chunkSize) {
                                repeat(chunkSize - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Defisit warnings
            if (monthlyFlow.isDeficit) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Deficit Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(id = R.string.dashboard_warning_defisit),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            // Cash Flow Trends
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.dashboard_income_vs_expense),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Income details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF00C853), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.txtype_income),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = LocalizationUtils.formatCurrency(monthlyFlow.totalIncome, currencyActive, languageActive),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00C853),
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }

                            // Divider line
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                    .padding(horizontal = 16.dp)
                            )

                            // Expense details
                            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.error, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.txtype_expense),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = LocalizationUtils.formatCurrency(monthlyFlow.totalExpense, currencyActive, languageActive),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress indicator or indicator bar
                        val totalIn = monthlyFlow.totalIncome.toFloat()
                        val totalEx = monthlyFlow.totalExpense.toFloat()
                        val total = totalIn + totalEx
                        val progress = if (total > 0f) totalIn / total else 0.5f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Color(0xFF00C853),
                            trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dynamic Monthly Surplus Indicator card background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (monthlyFlow.isDeficit) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                                    else Color(0x1100C853),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        id = if (monthlyFlow.isDeficit) R.string.dashboard_defisit else R.string.dashboard_surplus
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (monthlyFlow.isDeficit) MaterialTheme.colorScheme.error else Color(0xFF00C853)
                                )
                                Text(
                                    text = LocalizationUtils.formatCurrency(monthlyFlow.surplusValue, currencyActive, languageActive),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (monthlyFlow.isDeficit) MaterialTheme.colorScheme.error else Color(0xFF00C853)
                                )
                            }
                        }
                    }
                }
            }

            // Asset Composition Donut Chart Segment
            if (composition.isNotEmpty()) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(id = R.string.dashboard_composition),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            // Interactive Donut Chart drawing
                            Box(
                                modifier = Modifier.size(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val chartColors = listOf(
                                    Color(0xFF2196F3), // BANK
                                    Color(0xFF00BCD4), // E_WALLET
                                    Color(0xFF4CAF50), // TUNAI
                                    Color(0xFFFF9800), // BROKER
                                    Color(0xFF9E9E9E), // DEPOSITO
                                    Color(0xFF9C27B0)  // CUSTOM
                                )
                                val assetKeys = composition.keys.toList()
                                val totalAssets = composition.values.fold(BigDecimal.ZERO) { acc, d -> acc.add(d) }.toFloat()

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    var startAngle = -90f
                                    assetKeys.forEachIndexed { idx, key ->
                                        val valRatio = composition[key]?.toFloat() ?: 0f
                                        val sweepAngle = if (totalAssets > 0f) (valRatio / totalAssets) * 360f else 0f
                                        
                                        drawArc(
                                            color = chartColors[idx % chartColors.size],
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle * chartAnimationProgress.value,
                                            useCenter = false,
                                            style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round),
                                            size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
                                            topLeft = Offset(12.dp.toPx(), 12.dp.toPx())
                                        )
                                        startAngle += sweepAngle * chartAnimationProgress.value
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Legend grid
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val chartColors = listOf(
                                    Color(0xFF2196F3),
                                    Color(0xFF00BCD4),
                                    Color(0xFF4CAF50),
                                    Color(0xFFFF9800),
                                    Color(0xFF9E9E9E),
                                    Color(0xFF9C27B0)
                                )
                                composition.keys.toList().forEachIndexed { idx, type ->
                                    val assetVal = composition[type] ?: BigDecimal.ZERO
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(chartColors[idx % chartColors.size], CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = when (type) {
                                                    AccountType.BANK -> stringResource(R.string.actype_bank)
                                                    AccountType.E_WALLET -> stringResource(R.string.actype_ewallet)
                                                    AccountType.BROKER -> stringResource(R.string.actype_broker)
                                                    AccountType.DEPOSITO -> stringResource(R.string.actype_deposito)
                                                    AccountType.TUNAI -> stringResource(R.string.actype_tunai)
                                                    AccountType.KARTU_KREDIT -> stringResource(R.string.actype_credit)
                                                    AccountType.CUSTOM -> stringResource(R.string.actype_custom)
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = LocalizationUtils.formatCurrency(assetVal, currencyActive, languageActive),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Transactions List Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.dashboard_recent_transactions),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = onNavigateToTransactions) {
                        Text(
                            text = stringResource(id = R.string.dashboard_view_all),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (recentTransactions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.dashboard_no_transactions),
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(recentTransactions) { tx ->
                    val acc = accounts.find { it.id == tx.accountId }
                    val catName = if (tx.categoryId != null) {
                        viewModel.categoriesState.value.find { it.id == tx.categoryId }?.name ?: ""
                    } else ""
                    
                    val valueColor = when (tx.type) {
                        TransactionType.PEMASUKAN -> Color(0xFF00C853)
                        TransactionType.PENGELUARAN -> MaterialTheme.colorScheme.error
                        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                        TransactionType.RECONCILE -> {
                            if (tx.amount >= BigDecimal.ZERO) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                        }
                    }

                    val prefix = when (tx.type) {
                        TransactionType.PEMASUKAN -> "+"
                        TransactionType.PENGELUARAN -> "-"
                        TransactionType.TRANSFER -> "⇌ "
                        TransactionType.RECONCILE -> if (tx.amount >= BigDecimal.ZERO) "+" else ""
                    }

                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToEditTx(tx.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val categoryIcon = if (tx.categoryId != null) {
                                    viewModel.categoriesState.value.find { it.id == tx.categoryId }?.icon ?: "category"
                                } else "swap_horiz"

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIconByName(categoryIcon),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = if (tx.note.isNotEmpty()) tx.note else (if (catName.isNotEmpty()) catName else stringResource(R.string.txtype_transfer)),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${acc?.name ?: ""} • ${LocalizationUtils.formatDateByLocale(tx.dateTime, languageActive)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Text(
                                text = "$prefix${LocalizationUtils.formatCurrency(tx.amount, currencyActive, languageActive)}",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = valueColor
                                ),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (!isTourCompleted) {
        DashboardTourOverlay(
            step = currentTourStep,
            onNext = { currentTourStep++ },
            onPrev = { currentTourStep-- },
            onSkip = { viewModel.completeDashboardTour() },
            onFinish = {
                viewModel.completeDashboardTour()
            },
            netWorth = netWorth,
            currencyActive = currencyActive,
            languageActive = languageActive
        )
    }
}
}

@Composable
fun ShortcutCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurface)
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

private fun formatAmount(amount: BigDecimal, currencyCode: String, languageActive: String): String {
    return LocalizationUtils.formatCurrency(amount, currencyCode, languageActive)
}

private class ServiceItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun DashboardTourOverlay(
    step: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    netWorth: BigDecimal,
    currencyActive: String,
    languageActive: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (languageActive == "en") "Home Feature Walkthrough" else "Panduan Fitur Beranda",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onSkip) {
                    Text(if (languageActive == "en") "Skip" else "Lewati", color = Color.White.copy(alpha = 0.6f))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    0 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF00C853))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.dashboard_net_worth),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = LocalizationUtils.formatCurrency(netWorth, currencyActive, languageActive),
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            TourTooltipCard(
                                title = if (languageActive == "en") "Total Net Worth" else "Total Kekayaan Bersih",
                                desc = if (languageActive == "en") "Displays the realtime accumulation of all bank account balances, digital wallets, and investment portfolios, minus liabilities (debts)." else "Menampilkan akumulasi seluruh saldo rekening bank, dompet digital, investasi portofolio, dikurangi kewajiban (utang) secara realtime.",
                                showPrev = false,
                                languageActive = languageActive,
                                onNext = onNext,
                                onPrev = onPrev
                            )
                        }
                    }
                    1 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF00C853))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (languageActive == "en") "Financial Services" else "Layanan Keuangan",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        MockServiceItem(title = if (languageActive == "en") "Budgets" else "Anggaran", icon = Icons.Default.Category)
                                        MockServiceItem(title = if (languageActive == "en") "Goals" else "Target", icon = Icons.Default.Flag)
                                        MockServiceItem(title = if (languageActive == "en") "Portfolio" else "Portofolio", icon = Icons.Default.PieChart)
                                        MockServiceItem(title = if (languageActive == "en") "Reports" else "Laporan", icon = Icons.Default.BarChart)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            TourTooltipCard(
                                title = if (languageActive == "en") "Core Financial Services" else "Layanan Finansial Utama",
                                desc = if (languageActive == "en") "Quick access to Budgeting, Savings Goals, Stock/Gold Portfolio, Recurring Bills, Financial Forecast, and Cash Flow Reports." else "Akses cepat ke modul Anggaran Belanja, Target Menabung, Portofolio Saham/Emas, Tagihan Rutin, Proyeksi Keuangan, dan Laporan Arus Kas.",
                                showPrev = true,
                                languageActive = languageActive,
                                onNext = onNext,
                                onPrev = onPrev
                            )
                        }
                    }
                    2 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(Color(0xFF00C853).copy(alpha = 0.2f), CircleShape)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            TourTooltipCard(
                                title = if (languageActive == "en") "Quick Access Button" else "Tombol Akses Cepat",
                                desc = if (languageActive == "en") "Tap the add button (+) to quickly record transactions via Manual entry, CSV Import, OCR Receipt scanning, or to confirm incoming drafts." else "Sentuh tombol tambah (+) untuk mencatat cepat via Manual, Impor CSV, Pindai Struk OCR, atau mengonfirmasi draf transaksi masuk.",
                                showPrev = true,
                                isLast = true,
                                languageActive = languageActive,
                                onNext = onFinish,
                                onPrev = onPrev
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (0..2).forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == step) 10.dp else 6.dp)
                            .background(
                                color = if (index == step) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TourTooltipCard(
    title: String,
    desc: String,
    showPrev: Boolean,
    isLast: Boolean = false,
    languageActive: String,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showPrev) {
                    TextButton(onClick = onPrev, modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (languageActive == "en") "Back" else "Kembali", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isLast) (if (languageActive == "en") "Start!" else "Mulai!") else (if (languageActive == "en") "Next" else "Lanjut"))
                }
            }
        }
    }
}

@Composable
private fun MockServiceItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = Color.White)
    }
}

@Composable
private fun SyncStatusBar(
    syncMeta: SyncMeta?,
    syncRunningMsg: String?,
    languageActive: String,
    onSyncClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSyncClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val isRunning = syncRunningMsg != null && !syncRunningMsg.contains("Berhasil") && !syncRunningMsg.contains("Success") && !syncRunningMsg.contains("Gagal") && !syncRunningMsg.contains("Failed") && !syncRunningMsg.contains("Error")
                val isSuccess = syncRunningMsg?.contains("Berhasil") == true || syncRunningMsg?.contains("Success") == true
                val isFailed = syncRunningMsg?.contains("Gagal") == true || syncRunningMsg?.contains("Failed") == true || syncRunningMsg?.contains("Error") == true

                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isFailed -> Icons.Default.CloudOff
                            isSuccess || syncMeta?.status == "SUCCESS" -> Icons.Default.CloudDone
                            else -> Icons.Default.CloudQueue
                        },
                        contentDescription = null,
                        tint = when {
                            isFailed -> MaterialTheme.colorScheme.error
                            isSuccess || syncMeta?.status == "SUCCESS" -> Color(0xFF00C853)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = when {
                            isRunning -> syncRunningMsg ?: (if (languageActive == "en") "Syncing..." else "Sinkronisasi...")
                            isFailed -> syncRunningMsg ?: (if (languageActive == "en") "Sync failed" else "Gagal sinkronisasi")
                            isSuccess -> syncRunningMsg ?: (if (languageActive == "en") "Sync successful!" else "Sinkronisasi berhasil!")
                            else -> if (languageActive == "en") "Google Sheets Connected" else "Google Sheets Terhubung"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            isFailed -> MaterialTheme.colorScheme.error
                            isSuccess -> Color(0xFF00C853)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    if (!isRunning && !isFailed && !isSuccess && syncMeta?.lastSyncAt != null && syncMeta.lastSyncAt > 0) {
                        val formattedDate = remember(syncMeta.lastSyncAt) {
                            val locale = if (languageActive == "en") java.util.Locale.US else java.util.Locale("in", "ID")
                            val format = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
                            format.format(java.util.Date(syncMeta.lastSyncAt))
                        }
                        Text(
                            text = "${if (languageActive == "en") "Last sync" else "Terakhir"}: $formattedDate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSyncClick()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = if (languageActive == "en") "Manual sync" else "Sinkronisasi manual",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

