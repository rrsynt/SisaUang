package com.example.presentation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.viewmodel.MainViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCreatePin: () -> Unit,
    onNavigateToCloudSync: () -> Unit
) {
    val context = LocalContext.current

    val themeSelected by viewModel.themeState.collectAsState()
    val languageSelected by viewModel.languageState.collectAsState()
    val currencySelected by viewModel.currencyState.collectAsState()
    val pinHash by viewModel.securePinHash.collectAsState()
    val bioEnabled by viewModel.isBiometricEnabled.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }
    var showLocalBackupDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            // Sesuai rule: Code comments: Bahasa Indonesia on important/non-obvious parts
            // Menulis data backup transaksi ke file CSV lokal yang dipilih oleh pengguna
            coroutineScope.launch {
                try {
                    val csvContent = viewModel.exportTransactionsToCsv()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                    val msg = if (languageSelected == "en") "CSV export saved successfully to device!" else "Ekspor CSV berhasil disimpan di perangkat!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    val msg = if (languageSelected == "en") "Failed to save CSV file: ${e.localizedMessage}" else "Gagal menyimpan file CSV: ${e.localizedMessage}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Sesuai rule: Code comments: Bahasa Indonesia on important/non-obvious parts
            // Membaca file CSV dari perangkat dan mengimpor transaksi ke database lokal
            coroutineScope.launch {
                try {
                    val csvContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                    if (!csvContent.isNullOrBlank()) {
                        viewModel.importTransactionsFromCsv(csvContent)
                        val msg = if (languageSelected == "en") "Transaction data successfully imported from CSV!" else "Data transaksi berhasil diimpor dari CSV!"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = if (languageSelected == "en") "CSV file is empty or corrupted." else "File CSV kosong atau rusak."
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    val msg = if (languageSelected == "en") "Failed to import data: ${e.localizedMessage}" else "Gagal mengimpor data: ${e.localizedMessage}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // 1. GENERAL DYNAMIC PREFERENCES SECTION
            Text(
                text = stringResource(R.string.settings_section_general),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            // Dynamic Theme config buttons
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val themes = listOf(
                            "SYSTEM" to stringResource(R.string.settings_theme_system),
                            "LIGHT" to stringResource(R.string.settings_theme_light),
                            "DARK" to stringResource(R.string.settings_theme_dark)
                        )

                        themes.forEach { (code, label) ->
                            val isSel = themeSelected == code
                            Button(
                                onClick = { viewModel.setAppTheme(code) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Instantly active language selector
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.settings_language), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val languages = listOf("id" to "Bahasa Indonesia", "en" to "English")
                        languages.forEach { (code, label) ->
                            val isSel = languageSelected == code
                            Button(
                                onClick = { viewModel.setAppLanguage(code) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Currency selection card
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (languageSelected == "en") "Primary Currency" else "Mata Uang Utama", fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currencies = listOf(
                            "IDR" to (if (languageSelected == "en") "Rupiah (IDR)" else "Rupiah (Rp)"),
                            "USD" to (if (languageSelected == "en") "Dollar (USD)" else "Dollar ($)")
                        )
                        currencies.forEach { (code, label) ->
                            val isSel = currencySelected == code
                            Button(
                                onClick = { viewModel.setAppCurrency(code) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. SECURITY COMPLIANCE CONFIGURATIONS
            Text(
                text = stringResource(R.string.settings_section_security),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // PIN Lock Toggle row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (pinHash == null) {
                                    onNavigateToCreatePin()
                                } else {
                                    viewModel.createOrUpdatePin(null)
                                    val msg = if (languageSelected == "en") "PIN disabled!" else "PIN dinonaktifkan!"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.settings_security_pin), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (pinHash != null) {
                                    if (languageSelected == "en") "PIN Active" else "PIN Aktif"
                                } else {
                                    if (languageSelected == "en") "Disabled" else "Dinonaktifkan"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = pinHash != null,
                            onCheckedChange = {
                                if (pinHash == null) {
                                    onNavigateToCreatePin()
                                } else {
                                    viewModel.createOrUpdatePin(null)
                                    val msg = if (languageSelected == "en") "PIN disabled!" else "PIN dinonaktifkan!"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Divider()

                    // Biometrics lock row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (pinHash == null) {
                                    val msg = if (languageSelected == "en") "Enable PIN lock first!" else "Aktifkan PIN terlebih dahulu mandatori!"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setBiometricLock(!bioEnabled)
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.settings_security_biometrics), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (bioEnabled) {
                                    if (languageSelected == "en") "Biometrics Active" else "Biometrik Aktiv"
                                } else {
                                    if (languageSelected == "en") "Disabled" else "Nonaktif"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = bioEnabled,
                            onCheckedChange = {
                                if (pinHash == null) {
                                    val msg = if (languageSelected == "en") "Enable PIN lock first!" else "Aktifkan PIN terlebih dahulu mandatori!"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setBiometricLock(it)
                                }
                            }
                        )
                    }

                    Divider()

                    // Database Encryption Status row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(if (languageSelected == "en") "Database Encryption" else "Enkripsi Database", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (viewModel.isDatabaseEncrypted) {
                                    if (languageSelected == "en") "Active (SQLCipher)" else "Aktif (SQLCipher)"
                                } else {
                                    if (languageSelected == "en") "Inactive (Standard SQLite)" else "Tidak Aktif (Standard SQLite)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (viewModel.isDatabaseEncrypted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            )
                        }
                        Icon(
                            imageVector = if (viewModel.isDatabaseEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (viewModel.isDatabaseEncrypted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 3. SECURE DATA OPERATIONS SECTION
            Text(
                text = stringResource(R.string.settings_section_data),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Cloud Sync row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToCloudSync() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(if (languageSelected == "en") "Cloud Sync" else "Sinkronisasi Cloud", fontWeight = FontWeight.SemiBold)
                            Text(if (languageSelected == "en") "Connect with Google Sheets & Google Drive" else "Hubungkan dengan Google Sheets & Google Drive", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Divider()

                    // Local Backup & Restore row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLocalBackupDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(if (languageSelected == "en") "Local Backup & Restore (CSV)" else "Backup & Restore Lokal (CSV)", fontWeight = FontWeight.SemiBold)
                            Text(if (languageSelected == "en") "Export or import transactions in CSV format" else "Ekspor atau impor transaksi dalam format CSV", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Divider()

                    // App Guide / Tour Guide row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.restartDashboardTour()
                                val msg = if (languageSelected == "en") "User guide re-enabled. Open Home tab to view!" else "Panduan aplikasi diaktifkan kembali. Buka tab Beranda untuk melihat!"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(if (languageSelected == "en") "User Guide" else "Panduan Pengguna", fontWeight = FontWeight.SemiBold)
                            Text(if (languageSelected == "en") "Show the main feature walkthrough again" else "Tampilkan kembali panduan petunjuk fitur utama", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Divider()

                    // Populate test seed data row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.seedDemoData()
                                Toast.makeText(context, context.getString(R.string.settings_data_seeded_msg), Toast.LENGTH_SHORT).show()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF00C853))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.settings_data_seed_title), fontWeight = FontWeight.SemiBold, color = Color(0xFF00C853))
                            Text(stringResource(R.string.settings_data_seed_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Divider()

                    // Wipe database clean slate row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearConfirm = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.settings_data_clear_title), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.settings_data_clear_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Wipe Database Confirmation Dialog
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(stringResource(R.string.settings_data_clear_title)) },
                text = { Text(if (languageSelected == "en") "Are you sure you want to delete all financial data? This action cannot be undone." else "Apakah Anda yakin ingin menghapus seluruh data finansial? Tindakan ini tidak dapat dibatalkan.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllUserData()
                            showClearConfirm = false
                            Toast.makeText(context, context.getString(R.string.settings_data_cleared_msg), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        // ---------------- LOCAL BACKUP & RESTORE DIALOG ----------------
        if (showLocalBackupDialog) {
            AlertDialog(
                onDismissRequest = { showLocalBackupDialog = false },
                title = { Text(if (languageSelected == "en") "Local Backup & Restore (CSV)" else "Backup & Restore Lokal (CSV)") },
                text = { Text(if (languageSelected == "en") "Export your transaction data to a CSV file (can be opened in Excel/Google Sheets) or import new transactions from a backup CSV file." else "Ekspor data transaksi Anda ke file CSV (bisa dibuka di Excel/Google Sheets) atau impor transaksi baru dari file CSV backup.") },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showLocalBackupDialog = false
                                createDocumentLauncher.launch("sisauang_transaksi.csv")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (languageSelected == "en") "Export to CSV File" else "Ekspor ke File CSV")
                        }
                        Button(
                            onClick = {
                                showLocalBackupDialog = false
                                openDocumentLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (languageSelected == "en") "Import from CSV File" else "Impor dari File CSV")
                        }
                        TextButton(
                            onClick = { showLocalBackupDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (languageSelected == "en") "Cancel" else "Batal")
                        }
                    }
                },
                dismissButton = null
            )
        }
    }
}
