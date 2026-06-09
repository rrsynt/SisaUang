package com.example.presentation.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.viewmodel.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val syncMeta by viewModel.syncMetaFlow.collectAsState()
    val syncRunningMsg by viewModel.syncRunningState.collectAsState()
    val languageActive by viewModel.languageState.collectAsState()

    var isGoogleSignedIn by remember { mutableStateOf(viewModel.isGoogleSignIn()) }
    var userEmail by remember { mutableStateOf(viewModel.getGoogleEmail()) }
    var spreadsheetIdInput by remember { mutableStateOf(syncMeta?.googleSpreadsheetId ?: "") }

    LaunchedEffect(syncMeta) {
        if (syncMeta != null && spreadsheetIdInput.isEmpty()) {
            spreadsheetIdInput = syncMeta?.googleSpreadsheetId ?: ""
        }
    }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                if (account != null) {
                    isGoogleSignedIn = true
                    userEmail = account.email
                    val msg = if (languageActive == "en") "Successfully signed in as $userEmail" else "Berhasil masuk sebagai $userEmail"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val msg = if (languageActive == "en") "Sign in failed: ${e.localizedMessage}" else "Gagal masuk: ${e.localizedMessage}"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        } else {
            val statusCode = try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                task.getResult(ApiException::class.java)
                null
            } catch (e: ApiException) {
                e.statusCode
            } catch (e: Exception) {
                null
            }
            val errorMsg = if (languageActive == "en") {
                when (statusCode) {
                    10 -> "Developer Error (10): Verify SHA-1 & Package Name in Google Cloud Console."
                    7 -> "Network Error (7): Check internet connection."
                    12500 -> "Sign-in Failed (12500): Google Play Services configuration issue."
                    else -> "GMS error: $statusCode, resultCode: ${result.resultCode}"
                }
            } else {
                when (statusCode) {
                    10 -> "Developer Error (10): Periksa SHA-1 & Package Name di Google Cloud Console."
                    7 -> "Network Error (7): Periksa koneksi internet."
                    12500 -> "Sign-in Failed (12500): Masalah konfigurasi Google Play Services."
                    else -> "GMS error: $statusCode, resultCode: ${result.resultCode}"
                }
            }
            val msg = if (languageActive == "en") "Sign in failed. $errorMsg" else "Gagal masuk. $errorMsg"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (languageActive == "en") "Cloud Sync" else "Sinkronisasi Cloud", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (languageActive == "en") "Back" else "Kembali")
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
            
            // 1. Google OAuth Card
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (languageActive == "en") "Google Account" else "Akun Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = if (isGoogleSignedIn) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    if (isGoogleSignedIn) {
                        Text(if (languageActive == "en") "Connected as:" else "Tersambung sebagai:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(userEmail ?: (if (languageActive == "en") "Email not found" else "Email tidak ditemukan"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                        Button(
                            onClick = {
                                viewModel.signOutGoogle {
                                    isGoogleSignedIn = false
                                    userEmail = null
                                    val msg = if (languageActive == "en") "Logged out successfully" else "Log out berhasil"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (languageActive == "en") "Disconnect" else "Putuskan Sambungan")
                        }
                    } else {
                        Text(
                            if (languageActive == "en") "Connect your Google account to enable automatic Google Sheets synchronization and backup data to Google Drive." else "Sambungkan akun Google Anda untuk mengaktifkan sinkronisasi otomatis Google Sheets dan backup data di Google Drive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                val intent = viewModel.getGoogleSignInIntent()
                                googleSignInLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (languageActive == "en") "Sign in with Google" else "Masuk dengan Google")
                        }
                    }
                }
            }

            if (isGoogleSignedIn) {
                // 2. Google Sheets Configuration Card
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(if (languageActive == "en") "Google Sheets Settings" else "Pengaturan Google Sheets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = spreadsheetIdInput,
                            onValueChange = { spreadsheetIdInput = it },
                            label = { Text(if (languageActive == "en") "Google Spreadsheet ID" else "ID Spreadsheet Google") },
                            placeholder = { Text(if (languageActive == "en") "Leave blank to create a new one" else "Biarkan kosong untuk membuat baru") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text(
                            if (languageActive == "en") "If left blank, the app will automatically create a new Spreadsheet in your Drive on the first sync." else "Jika dikosongkan, aplikasi akan otomatis membuat Spreadsheet baru di Drive Anda pada sinkronisasi pertama.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                viewModel.updateSpreadsheetId(spreadsheetIdInput)
                                val msg = if (languageActive == "en") "Spreadsheet ID saved successfully" else "ID Spreadsheet berhasil disimpan"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (languageActive == "en") "Save" else "Simpan")
                        }
                    }
                }

                // 3. Two-Way Sync Operations Card
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(if (languageActive == "en") "Automatic Sync" else "Sinkronisasi Otomatis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        // Status Sync
                        val status = syncMeta?.status ?: "IDLE"
                        val lastSyncAt = syncMeta?.lastSyncAt ?: 0L
                        val dateText = if (lastSyncAt > 0L) {
                            val locale = if (languageActive == "en") Locale("en", "US") else Locale("id", "ID")
                            val pattern = if (languageActive == "en") "MMM dd, yyyy, hh:mm a" else "dd MMM yyyy, HH:mm"
                            val sdf = SimpleDateFormat(pattern, locale)
                            sdf.format(Date(lastSyncAt))
                        } else {
                            if (languageActive == "en") "Never" else "Belum pernah"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (languageActive == "en") "Last Sync" else "Sinkronisasi Terakhir", style = MaterialTheme.typography.bodyMedium)
                            Text(dateText, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (languageActive == "en") "Status" else "Status", style = MaterialTheme.typography.bodyMedium)
                            
                            val (bgColor, textColor, label) = when (status) {
                                "SUCCESS" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), if (languageActive == "en") "Synced" else "Tersinkron")
                                "RUNNING" -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), if (languageActive == "en") "Syncing" else "Berjalan")
                                "FAILED" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), if (languageActive == "en") "Failed" else "Gagal")
                                else -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), if (languageActive == "en") "Pending" else "Menunggu")
                            }

                            Surface(
                                color = bgColor,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (syncRunningMsg != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (status == "RUNNING") {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = syncRunningMsg!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (syncMeta?.googleSpreadsheetId.isNullOrBlank() && spreadsheetIdInput.isBlank()) {
                                    viewModel.clearSyncRunningMessage()
                                    viewModel.createSpreadsheet("Sisa Uang Backup") { newId ->
                                        if (newId != null) {
                                            viewModel.updateSpreadsheetId(newId)
                                            spreadsheetIdInput = newId
                                            val msg = if (languageActive == "en") "New Spreadsheet created successfully!" else "Spreadsheet baru berhasil dibuat!"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            viewModel.runManualSync()
                                        } else {
                                            val msg = if (languageActive == "en") "Failed to create new spreadsheet." else "Gagal membuat spreadsheet baru."
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    viewModel.runManualSync()
                                }
                            },
                            enabled = status != "RUNNING",
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (languageActive == "en") "Sync Now" else "Sinkronkan Sekarang")
                        }
                    }
                }

                // 4. Drive Backup & Restore Card
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(if (languageActive == "en") "Google Drive Backup & Restore" else "Google Drive Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (languageActive == "en") "Export the entire internal database to sisauang_backup.json in your Google Drive or restore from it." else "Ekspor seluruh database internal ke file sisauang_backup.json di Google Drive Anda atau pulihkan dari file tersebut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.runBackupToDrive { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (languageActive == "en") "Export Backup" else "Ekspor Backup")
                            }

                            Button(
                                onClick = {
                                    viewModel.runRestoreFromDrive { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (languageActive == "en") "Import Restore" else "Impor Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}
