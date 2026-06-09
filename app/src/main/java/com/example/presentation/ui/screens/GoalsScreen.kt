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
import com.example.domain.model.Goal
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val goals by viewModel.goalsState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showContributionDialog by remember { mutableStateOf<Goal?>(null) }

    // Create Goal form state
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var targetDaysOffset by remember { mutableStateOf("") } // e.g. target date in X days
    var selectedAccountId by remember { mutableStateOf<String?>(null) }

    // Contribution form state
    var contributionAmount by remember { mutableStateOf("") }
    var contributionAccountId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (languageActive == "en") "Financial Goals" else "Tujuan Keuangan", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = if (languageActive == "en") "Back" else "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = if (languageActive == "en") "Add Goal" else "Tambah Target")
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
            if (goals.filter { !it.deleted }.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (languageActive == "en") "No financial goals created yet." else "Belum ada tujuan keuangan dibuat.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(goals.filter { !it.deleted }) { goal ->
                        val progress = if (goal.targetAmount > BigDecimal.ZERO) {
                            goal.currentAmount.divide(goal.targetAmount, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                        } else 0f

                        val pctText = "${(progress * 100).toInt()}%"

                        // Calculate suggestion monthly contribution & estimated completion date
                        val remainingAmount = goal.targetAmount.subtract(goal.currentAmount)
                        val now = System.currentTimeMillis()
                        
                        val monthsRemaining = if (goal.targetDate != null && goal.targetDate > now) {
                            ((goal.targetDate - now) / (30L * 24 * 60 * 60 * 1000)).coerceAtLeast(1L)
                        } else 1L

                        val suggestedMonthly = if (remainingAmount > BigDecimal.ZERO) {
                            remainingAmount.divide(BigDecimal(monthsRemaining), 2, java.math.RoundingMode.HALF_UP)
                        } else BigDecimal.ZERO

                        val formattedTargetDate = goal.targetDate?.let {
                            val sdf = SimpleDateFormat("dd MMM yyyy", Locale(languageActive))
                            sdf.format(Date(it))
                        } ?: (if (languageActive == "en") "No deadline" else "Tidak ada tenggat")

                        val goalTxs = remember(transactions, goal.id) {
                            transactions.filter { !it.deleted && it.goalId == goal.id }
                        }
                        val estimatedDateText = remember(goal, goalTxs) {
                            if (remainingAmount <= BigDecimal.ZERO) {
                                if (languageActive == "en") "Reached!" else "Tercapai!"
                            } else if (goal.currentAmount > BigDecimal.ZERO && goalTxs.isNotEmpty()) {
                                val firstTxTime = goalTxs.minOf { it.dateTime }
                                val diffMs = maxOf(1000L * 60 * 60 * 24, now - firstTxTime)
                                val diffDays = BigDecimal(diffMs).divide(BigDecimal(24L * 60 * 60 * 1000), 4, java.math.RoundingMode.HALF_UP)
                                val dailyRate = goal.currentAmount.divide(diffDays, 4, java.math.RoundingMode.HALF_UP)
                                if (dailyRate.compareTo(BigDecimal.ZERO) > 0) {
                                    val daysToTarget = remainingAmount.divide(dailyRate, 0, java.math.RoundingMode.CEILING).toLong()
                                    val targetTimeMs = now + (daysToTarget * 24L * 60 * 60 * 1000)
                                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale(languageActive))
                                    sdf.format(Date(targetTimeMs))
                                } else {
                                    if (languageActive == "en") "Cannot project" else "Tidak dapat diproyeksikan"
                                }
                            } else {
                                if (languageActive == "en") "No contributions yet" else "Belum ada kontribusi"
                            }
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
                                        Icon(
                                            imageVector = Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = goal.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = (if (languageActive == "en") "Deadline: " else "Tenggat: ") + formattedTargetDate,
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = { showContributionDialog = goal }) {
                                            Icon(Icons.Default.Savings, contentDescription = if (languageActive == "en") "Contribute" else "Menabung", tint = Color(0xFF00C853))
                                        }
                                        IconButton(onClick = { viewModel.deleteGoal(goal.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = if (languageActive == "en") "Delete" else "Hapus", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = (if (languageActive == "en") "Saved: " else "Terkumpul: ") + LocalizationUtils.formatCurrency(goal.currentAmount, currencyActive, languageActive),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                                    )
                                    Text(
                                        text = (if (languageActive == "en") "Target: " else "Target: ") + LocalizationUtils.formatCurrency(goal.targetAmount, currencyActive, languageActive),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = Color(0xFF00C853),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = (if (languageActive == "en") "Progress: " else "Progres: ") + pctText, style = MaterialTheme.typography.bodySmall)
                                    if (suggestedMonthly > BigDecimal.ZERO && goal.targetDate != null) {
                                        Text(
                                            text = (if (languageActive == "en") "Suggest: " else "Saran: ") + LocalizationUtils.formatCurrency(suggestedMonthly, currencyActive, languageActive) + (if (languageActive == "en") "/mo" else "/bln"),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = (if (languageActive == "en") "Est. Completion: " else "Estimasi Tercapai: ") + estimatedDateText,
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Create Goal
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (languageActive == "en") "New Financial Goal" else "Tujuan Keuangan Baru") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (languageActive == "en") "Goal Name" else "Nama Target") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetAmount,
                        onValueChange = { targetAmount = it },
                        label = { Text(if (languageActive == "en") "Target Amount" else "Nominal Target") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetDaysOffset,
                        onValueChange = { targetDaysOffset = it },
                        label = { Text(if (languageActive == "en") "Duration (Months, e.g., 12)" else "Masa Target (Jumlah Bulan, contoh: 12)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    var accExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = accounts.find { it.id == selectedAccountId }?.name ?: (if (languageActive == "en") "Select Linked Account (Optional)" else "Pilih Dompet Terkait (Opsional)"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (languageActive == "en") "Linked Account" else "Dompet Terkait") },
                            trailingIcon = { IconButton(onClick = { accExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "None" else "Tidak Ada") }, onClick = { selectedAccountId = null; accExpanded = false })
                            accounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedAccountId = acc.id; accExpanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numTarget = targetAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val months = targetDaysOffset.toLongOrNull() ?: 0L
                        if (name.isNotEmpty() && numTarget > BigDecimal.ZERO) {
                            val targetDateMs = if (months > 0) {
                                System.currentTimeMillis() + (months * 30L * 24 * 60 * 60 * 1000)
                            } else null

                            val newGoal = Goal(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                targetAmount = numTarget,
                                targetDate = targetDateMs,
                                linkedAccountId = selectedAccountId,
                                currentAmount = BigDecimal.ZERO,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.insertGoal(newGoal)
                            showCreateDialog = false
                            name = ""
                            targetAmount = ""
                            targetDaysOffset = ""
                            selectedAccountId = null
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

    // Dialog Setoran (Savings Contribution)
    if (showContributionDialog != null) {
        val activeGoal = showContributionDialog!!
        AlertDialog(
            onDismissRequest = { showContributionDialog = null },
            title = { Text(if (languageActive == "en") "Add Goal Contribution" else "Tambah Setoran Target") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = (if (languageActive == "en") "Goal: " else "Target: ") + activeGoal.name, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = contributionAmount,
                        onValueChange = { contributionAmount = it },
                        label = { Text(if (languageActive == "en") "Contribution Amount" else "Nominal Setoran") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    var accExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = accounts.find { it.id == contributionAccountId }?.name ?: (if (languageActive == "en") "Select Source Account (Optional)" else "Pilih Dompet Pengirim (Opsional)"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (languageActive == "en") "Source Account" else "Sumber Dompet") },
                            trailingIcon = { IconButton(onClick = { accExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (languageActive == "en") "Manual (No balance deduction)" else "Manual (Tanpa Kurangi Saldo)") }, onClick = { contributionAccountId = null; accExpanded = false })
                            accounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = { contributionAccountId = acc.id; accExpanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numAmt = contributionAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        if (numAmt > BigDecimal.ZERO) {
                            viewModel.addGoalContribution(activeGoal.id, numAmt, contributionAccountId)
                            showContributionDialog = null
                            contributionAmount = ""
                            contributionAccountId = null
                        }
                    }
                ) {
                    Text(if (languageActive == "en") "Contribute" else "Setor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContributionDialog = null }) {
                    Text(if (languageActive == "en") "Cancel" else "Batal")
                }
            }
        )
    }
}
