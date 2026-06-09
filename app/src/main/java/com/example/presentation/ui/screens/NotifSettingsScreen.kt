package com.example.presentation.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.ParsingRule
import com.example.domain.model.TransactionType
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifSettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val listenerEnabled by viewModel.notificationListenerEnabled.collectAsState()
    val rules by viewModel.parsingRulesState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()

    var showConsentDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0: Pengaturan, 1: Aturan/Pattern

    // Form input untuk Rule Editor
    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ParsingRule?>(null) }
    var ruleName by remember { mutableStateOf("") }
    var appPackage by remember { mutableStateOf("") }
    var regexPattern by remember { mutableStateOf("") }
    var ruleType by remember { mutableStateOf(TransactionType.PENGELUARAN) }
    var selectedAccountId by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var amountGroupIndex by remember { mutableStateOf("1") }
    var noteGroupIndex by remember { mutableStateOf("") }

    // State untuk Uji Pola
    var testText by remember { mutableStateOf("") }
    var testResultText by remember { mutableStateOf("") }
    var testResultColor by remember { mutableStateOf(Color.Gray) }

    // Aplikasi umum di Indonesia
    val defaultApps = listOf(
        Pair("BCA Mobile", "com.id.bca"),
        Pair("Mandiri Livin", "com.bankmandiri.jacob"),
        Pair("GoPay", "com.gojek.app"),
        Pair("OVO", "id.ovo.android"),
        Pair("Shopee", "com.shopee.id"),
        Pair("DANA", "id.dana")
    )

    fun resetRuleForm() {
        editingRule = null
        ruleName = ""
        appPackage = "com.gojek.app"
        regexPattern = "sebesar Rp\\s?([0-9.,]+)"
        ruleType = TransactionType.PENGELUARAN
        selectedAccountId = accounts.firstOrNull()?.id ?: ""
        selectedCategoryId = categories.firstOrNull()?.id
        amountGroupIndex = "1"
        noteGroupIndex = ""
    }

    LaunchedEffect(showRuleDialog) {
        if (showRuleDialog && editingRule != null) {
            val r = editingRule!!
            ruleName = r.name
            appPackage = r.appPackage
            regexPattern = r.regexPattern
            ruleType = r.type
            selectedAccountId = r.accountId
            selectedCategoryId = r.categoryId
            amountGroupIndex = r.amountGroupIndex.toString()
            noteGroupIndex = r.noteGroupIndex?.toString() ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Otomasi Notifikasi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                    Box(modifier = Modifier.padding(16.dp)) { Text("Layanan & Consent") }
                }
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                    Box(modifier = Modifier.padding(16.dp)) { Text("Aturan Regex") }
                }
            }

            if (activeTab == 0) {
                // TAB 0: Consent & disclosure screen
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "PENTING: Prominent Disclosure & Consent",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Aplikasi Sisa Uang membutuhkan akses ke notifikasi perangkat Anda untuk " +
                                        "membaca pesan transaksi dari aplikasi bank, e-wallet, dan merchant. " +
                                        "Akses ini digunakan secara eksklusif untuk mendeteksi nominal transaksi, " +
                                        "catatan belanja, dan dompet pembayaran guna dibuatkan draft transaksi otomatis.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "KEAMANAN & PRIVASI:\n" +
                                            "1. Seluruh proses analisis notifikasi berjalan 100% secara lokal di perangkat Anda (On-Device).\n" +
                                            "2. Tidak ada data notifikasi atau finansial apa pun yang dikirimkan ke server pihak ketiga.\n" +
                                            "3. Anda dapat mematikan akses ini kapan saja melalui halaman pengaturan ini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Aktifkan Pembaca Notifikasi", fontWeight = FontWeight.Bold)
                                Text(
                                    if (listenerEnabled) "Aktif" else "Nonaktif",
                                    fontSize = 12.sp,
                                    color = if (listenerEnabled) Color(0xFF4CAF50) else Color.Gray
                                )
                            }
                            Switch(
                                checked = listenerEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        showConsentDialog = true
                                    } else {
                                        viewModel.setNotificationListenerEnabled(false)
                                    }
                                }
                            )
                        }
                    }

                    if (listenerEnabled) {
                        item {
                            Text("Aplikasi Sumber yang Diizinkan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        items(defaultApps) { app ->
                            val isAppEnabled = remember(app.second) {
                                viewModel.isNotificationAppEnabled(app.second)
                            }
                            var appChecked by remember(isAppEnabled) { mutableStateOf(isAppEnabled) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(app.first, fontWeight = FontWeight.SemiBold)
                                    Text(app.second, fontSize = 11.sp, color = Color.Gray)
                                }
                                Switch(
                                    checked = appChecked,
                                    onCheckedChange = { checked ->
                                        appChecked = checked
                                        viewModel.setNotificationAppEnabled(app.second, checked)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB 1: Rules & Pattern list + editor + tester
                Column(modifier = Modifier.fillMaxSize()) {
                    // Pattern Tester
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("UJI POLA (Regex Testing)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = testText,
                                onValueChange = { testText = it },
                                placeholder = { Text("Masukkan contoh pesan notifikasi...") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        var matched = false
                                        for (rule in rules) {
                                            try {
                                                val regex = Regex(rule.regexPattern, RegexOption.IGNORE_CASE)
                                                val match = regex.find(testText)
                                                if (match != null) {
                                                    val amtGrp = match.groups[rule.amountGroupIndex]?.value
                                                    val amtVal = amtGrp?.replace(Regex("[^0-9]"), "") ?: "0"
                                                    val noteVal = if (rule.noteGroupIndex != null && rule.noteGroupIndex!! < match.groups.size) {
                                                        match.groups[rule.noteGroupIndex!!]?.value ?: ""
                                                    } else ""
                                                    testResultText = "Cocok dengan Aturan: ${rule.name}\n" +
                                                            "Nominal: Rp $amtVal\n" +
                                                            "Catatan: $noteVal\n" +
                                                            "Tipe: ${rule.type}"
                                                    testResultColor = Color(0xFF4CAF50)
                                                    matched = true
                                                    break
                                                }
                                            } catch (e: Exception) {
                                                // Ignore test regex exception
                                            }
                                        }
                                        if (!matched) {
                                            testResultText = "Tidak ada aturan regex yang cocok."
                                            testResultColor = Color(0xFFF44336)
                                        }
                                    }
                                ) {
                                    Text("Uji Pola")
                                }
                                TextButton(onClick = {
                                    // Seeding default pattern templates if rules are empty
                                    val bcaRule = ParsingRule(
                                        id = UUID.randomUUID().toString(),
                                        appPackage = "com.id.bca",
                                        name = "BCA Debit Keluar",
                                        regexPattern = "sebesar Rp\\s?([0-9.,]+) ke ([A-Za-z0-9 ]+)",
                                        type = TransactionType.PENGELUARAN,
                                        accountId = accounts.firstOrNull()?.id ?: "acc-bca",
                                        categoryId = categories.firstOrNull()?.id,
                                        amountGroupIndex = 1,
                                        noteGroupIndex = 2
                                    )
                                    val gopayRule = ParsingRule(
                                        id = UUID.randomUUID().toString(),
                                        appPackage = "com.gojek.app",
                                        name = "GoPay Keluar",
                                        regexPattern = "berhasil bayar Rp\\s?([0-9.,]+) ke ([A-Za-z0-9 ]+)",
                                        type = TransactionType.PENGELUARAN,
                                        accountId = accounts.find { it.type == com.example.domain.model.AccountType.E_WALLET }?.id ?: "acc-gopay",
                                        categoryId = categories.firstOrNull()?.id,
                                        amountGroupIndex = 1,
                                        noteGroupIndex = 2
                                    )
                                    viewModel.insertParsingRule(bcaRule)
                                    viewModel.insertParsingRule(gopayRule)
                                }) {
                                    Text("Muat Template Bawaan")
                                }
                            }
                            if (testResultText.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    testResultText,
                                    color = testResultColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daftar Aturan Regex", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = {
                            resetRuleForm()
                            showRuleDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rules) { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, fontWeight = FontWeight.Bold)
                                    Text("Package: ${rule.appPackage}", fontSize = 12.sp, color = Color.Gray)
                                    Text("Regex: `${rule.regexPattern}`", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingRule = rule
                                        showRuleDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                                    }
                                    IconButton(onClick = {
                                        viewModel.deleteParsingRule(rule.id)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Consent Prompt Dialog
        if (showConsentDialog) {
            AlertDialog(
                onDismissRequest = { showConsentDialog = false },
                title = { Text("Pernyataan Persetujuan") },
                text = {
                    Text(
                        "Saya menyetujui bahwa Sisa Uang akan memproses teks notifikasi bank dan e-wallet secara " +
                                "lokal di perangkat saya. Tidak ada data yang dikirim ke internet.\n\n" +
                                "Tekan OK untuk membuka pengaturan sistem Android dan berikan izin 'Notification Access' / " +
                                "'Akses Notifikasi' kepada Sisa Uang."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConsentDialog = false
                            viewModel.setNotificationListenerEnabled(true)
                            try {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            } catch (e: Exception) {
                                // Fallback
                            }
                        }
                    ) {
                        Text("Saya Setuju")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConsentDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Rule Form Dialog
        if (showRuleDialog) {
            AlertDialog(
                onDismissRequest = { showRuleDialog = false },
                title = { Text(if (editingRule == null) "Tambah Aturan" else "Edit Aturan") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(value = ruleName, onValueChange = { ruleName = it }, label = { Text("Nama Aturan") })
                        OutlinedTextField(value = appPackage, onValueChange = { appPackage = it }, label = { Text("App Package (Contoh: com.id.bca)") })
                        OutlinedTextField(value = regexPattern, onValueChange = { regexPattern = it }, label = { Text("Regex Pattern") })

                        // Type selector
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TransactionType.values().filter { it != TransactionType.RECONCILE }.forEach { type ->
                                val selected = ruleType == type
                                FilterChip(
                                    selected = selected,
                                    onClick = { ruleType = type },
                                    label = { Text(type.name) }
                                )
                            }
                        }

                        // Account selector
                        var accExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val accName = accounts.find { it.id == selectedAccountId }?.name ?: "Pilih Dompet"
                            OutlinedButton(onClick = { accExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(accName)
                            }
                            DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(text = { Text(acc.name) }, onClick = {
                                        selectedAccountId = acc.id
                                        accExpanded = false
                                    })
                                }
                            }
                        }

                        // Category selector
                        var catExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val catName = categories.find { it.id == selectedCategoryId }?.name ?: "Pilih Kategori"
                            OutlinedButton(onClick = { catExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(catName)
                            }
                            DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat.name) }, onClick = {
                                        selectedCategoryId = cat.id
                                        catExpanded = false
                                    })
                                }
                            }
                        }

                        OutlinedTextField(value = amountGroupIndex, onValueChange = { amountGroupIndex = it }, label = { Text("Regex Group Index Nominal (Biasanya 1)") })
                        OutlinedTextField(value = noteGroupIndex, onValueChange = { noteGroupIndex = it }, label = { Text("Regex Group Index Catatan (Opsional)") })
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsedAmountIndex = amountGroupIndex.toIntOrNull() ?: 1
                            val parsedNoteIndex = noteGroupIndex.toIntOrNull()
                            val rule = ParsingRule(
                                id = editingRule?.id ?: UUID.randomUUID().toString(),
                                appPackage = appPackage,
                                name = ruleName,
                                regexPattern = regexPattern,
                                type = ruleType,
                                accountId = selectedAccountId,
                                categoryId = selectedCategoryId,
                                amountGroupIndex = parsedAmountIndex,
                                noteGroupIndex = parsedNoteIndex,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.insertParsingRule(rule)
                            showRuleDialog = false
                        }
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRuleDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}
