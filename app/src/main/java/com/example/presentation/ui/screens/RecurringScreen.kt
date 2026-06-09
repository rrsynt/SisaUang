package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.domain.model.*
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val rules by viewModel.recurringRulesState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val goals by viewModel.goalsState.collectAsState()
    val suggestions by viewModel.detectedRecurringSuggestionsState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    // Create Rule form states
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.PENGELUARAN) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var accountId by remember { mutableStateOf("") }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    var interval by remember { mutableStateOf("1") }
    var isAutoExecute by remember { mutableStateOf(true) }
    var goalId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaksi Berulang", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah Aturan")
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
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Rekomendasi Tagihan Terdeteksi",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    items(suggestions) { suggestion ->
                        val budgetCat = categories.find { it.id == suggestion.categoryId }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.width(260.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = suggestion.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatAmount(suggestion.amount, currencyActive, languageActive)} • ${budgetCat?.name ?: "Kategori"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = suggestion.confidence,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        name = suggestion.name
                                        amount = suggestion.amount.toPlainString()
                                        type = TransactionType.PENGELUARAN
                                        categoryId = suggestion.categoryId
                                        accountId = suggestion.accountId
                                        frequency = suggestion.frequency
                                        interval = suggestion.interval.toString()
                                        isAutoExecute = false
                                        showCreateDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .height(32.dp)
                                ) {
                                    Text("Tambahkan", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Daftar Aturan Rutin",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (rules.filter { !it.deleted }.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventRepeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum ada transaksi berulang dibuat.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(rules.filter { !it.deleted }) { rule ->
                        val nextRunDate = remember(rule.nextExecutionDate) {
                            val sdf = SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))
                            sdf.format(Date(rule.nextExecutionDate))
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
                                            imageVector = if (rule.isAutoExecute) Icons.Default.Autorenew else Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = if (rule.isAutoExecute) Color(0xFF00C853) else Color(0xFFFF9800),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = rule.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "${rule.frequency.name} (tiap ${rule.interval}) • ${if (rule.isAutoExecute) "AUTO" else "INGATKAN"}",
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = { viewModel.executeRecurringRuleManual(rule.id) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Eksekusi Sekarang", tint = Color(0xFF00C853))
                                        }
                                        IconButton(onClick = { viewModel.deleteRecurringRule(rule.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Nominal: ${formatAmount(rule.amount, currencyActive, languageActive)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Tipe: ${rule.type.name}",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Jadwal Berikutnya: $nextRunDate",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                )
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
            title = { Text("Aturan Transaksi Berulang") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nama Aturan/Tagihan") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Nominal") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        var typeExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = type.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipe Transaksi") },
                                trailingIcon = { IconButton(onClick = { typeExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                DropdownMenuItem(text = { Text("PEMASUKAN") }, onClick = { type = TransactionType.PEMASUKAN; typeExpanded = false })
                                DropdownMenuItem(text = { Text("PENGELUARAN") }, onClick = { type = TransactionType.PENGELUARAN; typeExpanded = false })
                            }
                        }
                    }

                    item {
                        var catExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = categories.find { it.id == categoryId }?.name ?: "Semua Kategori",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kategori") },
                                trailingIcon = { IconButton(onClick = { catExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                DropdownMenuItem(text = { Text("Semua Kategori") }, onClick = { categoryId = null; catExpanded = false })
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat.name) }, onClick = { categoryId = cat.id; catExpanded = false })
                                }
                            }
                        }
                    }

                    item {
                        var accExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = accounts.find { it.id == accountId }?.name ?: "Pilih Dompet Sumber",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Dompet") },
                                trailingIcon = { IconButton(onClick = { accExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = {
                                            accountId = acc.id
                                            accExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        var freqExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = frequency.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Frekuensi") },
                                trailingIcon = { IconButton(onClick = { freqExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                                RecurringFrequency.values().forEach { freq ->
                                    DropdownMenuItem(text = { Text(freq.name) }, onClick = { frequency = freq; freqExpanded = false })
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = interval,
                            onValueChange = { interval = it },
                            label = { Text("Interval (e.g. setiap 1, setiap 2)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        var goalExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = goals.find { it.id == goalId }?.name ?: "Link ke Tujuan Keuangan (Opsional)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Link Target Tabungan") },
                                trailingIcon = { IconButton(onClick = { goalExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = goalExpanded, onDismissRequest = { goalExpanded = false }) {
                                DropdownMenuItem(text = { Text("Tidak Ada Link") }, onClick = { goalId = null; goalExpanded = false })
                                goals.forEach { goal ->
                                    DropdownMenuItem(text = { Text(goal.name) }, onClick = { goalId = goal.id; goalExpanded = false })
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = isAutoExecute, onCheckedChange = { isAutoExecute = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auto-Execute (Catat otomatis tanpa pengingat)")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numAmt = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val intVal = interval.toIntOrNull() ?: 1
                        if (name.isNotEmpty() && numAmt > BigDecimal.ZERO && accountId.isNotEmpty()) {
                            val newRule = RecurringRule(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                amount = numAmt,
                                type = type,
                                categoryId = categoryId,
                                accountId = accountId,
                                toAccountId = toAccountId,
                                note = note,
                                frequency = frequency,
                                interval = intVal,
                                nextExecutionDate = System.currentTimeMillis() + (24L * 60 * 60 * 1000), // First execution tomorrow
                                isAutoExecute = isAutoExecute,
                                isEnabled = true,
                                goalId = goalId,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.insertRecurringRule(newRule)
                            showCreateDialog = false
                            name = ""
                            amount = ""
                            accountId = ""
                            categoryId = null
                            toAccountId = null
                            goalId = null
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

private fun formatAmount(amount: BigDecimal, currencyCode: String, languageActive: String): String {
    return LocalizationUtils.formatCurrency(amount, currencyCode, languageActive)
}
