package com.example.presentation.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
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
import com.example.domain.model.DraftTransaction
import com.example.domain.model.ImportTemplate
import com.example.domain.model.TransactionType
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val template_drafts by viewModel.importTemplatesState.collectAsState() // template state helper name
    val templates by viewModel.importTemplatesState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val drafts by viewModel.draftsState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val languageActive by viewModel.languageState.collectAsState()

    var csvData by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var csvHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    // Column Mapping state
    var dateColumnIndex by remember { mutableStateOf(-1) }
    var amountColumnIndex by remember { mutableStateOf(-1) }
    var noteColumnIndex by remember { mutableStateOf(-1) }
    var selectedDelimiter by remember { mutableStateOf(",") }

    // Save template inputs
    var templateName by remember { mutableStateOf("") }
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }

    // Selected Account for imports
    var selectedAccountId by remember { mutableStateOf("") }

    val rupiahFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))

    // Helper for parsing CSV
    fun parseCsvContent(text: String, delimiter: String): List<List<String>> {
        val lines = text.split(Regex("\\r?\\n"))
        return lines.mapNotNull { line ->
            if (line.isBlank()) null
            else {
                val result = mutableListOf<String>()
                var currentToken = StringBuilder()
                var inQuotes = false
                var i = 0
                while (i < line.length) {
                    val c = line[i]
                    if (c == '\"') {
                        inQuotes = !inQuotes
                    } else if (c.toString() == delimiter && !inQuotes) {
                        result.add(currentToken.toString().trim().removeSurrounding("\""))
                        currentToken = StringBuilder()
                    } else {
                        currentToken.append(c)
                    }
                    i++
                }
                result.add(currentToken.toString().trim().removeSurrounding("\""))
                result
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst() && nameIndex != -1) {
                    selectedFileName = it.getString(nameIndex)
                }
            }

            try {
                val textContent = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val parsed = parseCsvContent(textContent, selectedDelimiter)
                if (parsed.isNotEmpty()) {
                    csvHeaders = parsed.first()
                    csvData = parsed.drop(1)
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
    }

    LaunchedEffect(accounts) {
        if (selectedAccountId.isEmpty()) {
            selectedAccountId = accounts.firstOrNull()?.id ?: ""
        }
    }

    // Auto map columns on template select
    LaunchedEffect(selectedTemplateId) {
        if (selectedTemplateId != null) {
            val template = templates.find { it.id == selectedTemplateId }
            if (template != null) {
                dateColumnIndex = template.dateColumnIndex
                amountColumnIndex = template.amountColumnIndex
                noteColumnIndex = template.noteColumnIndex
                selectedDelimiter = template.delimiter
                templateName = template.sourceName
            }
        }
    }

    // Parsed rows
    val parsedRows = remember(csvData, dateColumnIndex, amountColumnIndex, noteColumnIndex, transactions, drafts) {
        csvData.mapNotNull { row ->
            if (dateColumnIndex in row.indices && amountColumnIndex in row.indices) {
                val dateStr = row[dateColumnIndex]
                val amtStr = row[amountColumnIndex].replace(Regex("[^0-9.,-]"), "").replace(",", ".")
                val noteStr = if (noteColumnIndex in row.indices) row[noteColumnIndex] else ""

                val amount = amtStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                // Parse date or fallback to now
                val parsedDate = try {
                    val formats = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd MMM yyyy")
                    var time: Long? = null
                    for (fmt in formats) {
                        try {
                            val sdf = SimpleDateFormat(fmt, Locale.US)
                            time = sdf.parse(dateStr)?.time
                            if (time != null) break
                        } catch (e: Exception) {}
                    }
                    time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                // Compute deduplication hash
                // hash = MD5(parsedDate/day + amount + note)
                val dayTimestamp = parsedDate / (24 * 60 * 60 * 1000L)
                val hashInput = "$selectedAccountId|$amount|$noteStr|$dayTimestamp"
                val hash = MessageDigest.getInstance("MD5")
                    .digest(hashInput.toByteArray())
                    .joinToString("") { "%02x".format(it) }

                // Check duplicate against existing transactions or drafts
                val isDuplicate = transactions.any { it.transactionHash == hash || (it.amount.compareTo(amount) == 0 && it.note == noteStr) } ||
                        drafts.any { it.transactionHash == hash }

                ImportRowPreview(
                    date = parsedDate,
                    amount = amount,
                    note = noteStr,
                    isDuplicate = isDuplicate,
                    hash = hash
                )
            } else {
                null
            }
        }
    }

    val newRowsCount = parsedRows.count { !it.isDuplicate }
    val duplicateRowsCount = parsedRows.count { it.isDuplicate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impor CSV Mutasi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Template selection
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pilih Template Impor", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        var tempExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val tempName = templates.find { it.id == selectedTemplateId }?.sourceName ?: "Pilih Template Reusable"
                            OutlinedButton(onClick = { tempExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(tempName)
                            }
                            DropdownMenu(expanded = tempExpanded, onDismissRequest = { tempExpanded = false }) {
                                templates.forEach { temp ->
                                    DropdownMenuItem(text = { Text(temp.sourceName) }, onClick = {
                                        selectedTemplateId = temp.id
                                        tempExpanded = false
                                    })
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Delimiter toggle
                            FilterChip(selected = selectedDelimiter == ",", onClick = { selectedDelimiter = "," }, label = { Text("Koma (,)") })
                            FilterChip(selected = selectedDelimiter == ";", onClick = { selectedDelimiter = ";" }, label = { Text("Titik Koma (;)") })
                        }
                    }
                }
            }

            // File selection
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Pilih File Mutasi (CSV)", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        Button(onClick = { filePickerLauncher.launch("text/*") }) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pilih File")
                        }

                        if (selectedFileName.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                selectedFileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Columns mapping section (Visible after CSV parsed)
            if (csvHeaders.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Petakan Kolom CSV", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))

                            // Date column
                            Text("Kolom Tanggal", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            var dateExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val headerName = if (dateColumnIndex in csvHeaders.indices) csvHeaders[dateColumnIndex] else "Pilih Kolom Tanggal"
                                OutlinedButton(onClick = { dateExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(headerName)
                                }
                                DropdownMenu(expanded = dateExpanded, onDismissRequest = { dateExpanded = false }) {
                                    csvHeaders.forEachIndexed { idx, name ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            dateColumnIndex = idx
                                            dateExpanded = false
                                        })
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Amount column
                            Text("Kolom Nominal/Jumlah", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            var amtExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val headerName = if (amountColumnIndex in csvHeaders.indices) csvHeaders[amountColumnIndex] else "Pilih Kolom Nominal"
                                OutlinedButton(onClick = { amtExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(headerName)
                                }
                                DropdownMenu(expanded = amtExpanded, onDismissRequest = { amtExpanded = false }) {
                                    csvHeaders.forEachIndexed { idx, name ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            amountColumnIndex = idx
                                            amtExpanded = false
                                        })
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Note column
                            Text("Kolom Catatan/Keterangan", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            var noteExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val headerName = if (noteColumnIndex in csvHeaders.indices) csvHeaders[noteColumnIndex] else "Pilih Kolom Catatan"
                                OutlinedButton(onClick = { noteExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(headerName)
                                }
                                DropdownMenu(expanded = noteExpanded, onDismissRequest = { noteExpanded = false }) {
                                    csvHeaders.forEachIndexed { idx, name ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            noteColumnIndex = idx
                                            noteExpanded = false
                                        })
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Target Account selection
                            Text("Rekening Tujuan Impor", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            var accExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val currentAccName = accounts.find { it.id == selectedAccountId }?.name ?: "Pilih Rekening"
                                OutlinedButton(onClick = { accExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(currentAccName)
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

                            Spacer(Modifier.height(16.dp))

                            // Optional: Save mapping as template
                            OutlinedTextField(
                                value = templateName,
                                onValueChange = { templateName = it },
                                label = { Text("Simpan Template Sebagai...") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (templateName.isNotBlank()) {
                                        val temp = ImportTemplate(
                                            id = UUID.randomUUID().toString(),
                                            sourceName = templateName,
                                            dateColumnIndex = dateColumnIndex,
                                            amountColumnIndex = amountColumnIndex,
                                            noteColumnIndex = noteColumnIndex,
                                            delimiter = selectedDelimiter
                                        )
                                        viewModel.insertImportTemplate(temp)
                                    }
                                },
                                enabled = templateName.isNotBlank() && dateColumnIndex >= 0 && amountColumnIndex >= 0
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Simpan Template Mapping")
                            }
                        }
                    }
                }
            }

            // Preview summary & records
            if (parsedRows.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Pratinjau Impor", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$newRowsCount Baru, $duplicateRowsCount Duplikat terdeteksi",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    parsedRows.filter { !it.isDuplicate }.forEach { row ->
                                        val draft = DraftTransaction(
                                            id = UUID.randomUUID().toString(),
                                            amount = row.amount.abs(),
                                            type = if (row.amount >= BigDecimal.ZERO) TransactionType.PEMASUKAN else TransactionType.PENGELUARAN,
                                            categoryId = null,
                                            accountId = selectedAccountId.ifEmpty { null },
                                            toAccountId = null,
                                            adminFee = null,
                                            note = row.note,
                                            dateTime = row.date,
                                            tags = listOf("IMPORT"),
                                            sourceInput = "IMPORT",
                                            transactionHash = row.hash,
                                            isConfirmed = false
                                        )
                                        viewModel.insertDraft(draft)
                                    }
                                    onNavigateBack()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = newRowsCount > 0
                            ) {
                                Text("Impor $newRowsCount Draft Baru")
                            }
                        }
                    }
                }

                items(parsedRows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (row.isDuplicate) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.note.ifEmpty { "Tanpa Keterangan" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(row.date)),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val amtColor = if (row.amount >= BigDecimal.ZERO) Color(0xFF4CAF50) else Color(0xFFF44336)
                            Text(
                                text = com.example.domain.utils.LocalizationUtils.formatCurrency(row.amount, currencyActive, languageActive),
                                color = amtColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                if (row.isDuplicate) "Duplikat (Lewati)" else "Baru",
                                fontSize = 10.sp,
                                color = if (row.isDuplicate) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ImportRowPreview(
    val date: Long,
    val amount: BigDecimal,
    val note: String,
    val isDuplicate: Boolean,
    val hash: String
)
