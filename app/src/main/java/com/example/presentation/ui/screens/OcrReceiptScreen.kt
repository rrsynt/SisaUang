package com.example.presentation.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.example.domain.model.TransactionType
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.viewmodel.MainViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

private fun getTempPhotoUri(context: android.content.Context): Uri {
    val tempFile = File(context.cacheDir, "temp_photo.jpg")
    if (tempFile.exists()) tempFile.delete()
    tempFile.createNewFile()
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReceiptScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val accounts by viewModel.accountsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val languageActive by viewModel.languageState.collectAsState()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var ocrTotal by remember { mutableStateOf("") }
    var ocrDate by remember { mutableStateOf("") }
    var ocrNote by remember { mutableStateOf("Struk Belanja") }

    // Account & Category
    var selectedAccountId by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }

    // Split Bill fields
    var isSplitMode by remember { mutableStateOf(false) }
    val splitItems = remember { mutableStateListOf<SplitBillItem>() }

    val rupiahFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))

    LaunchedEffect(accounts) {
        if (selectedAccountId.isEmpty()) {
            selectedAccountId = accounts.firstOrNull()?.id ?: ""
        }
    }
    LaunchedEffect(categories) {
        if (selectedCategoryId.isEmpty()) {
            selectedCategoryId = categories.firstOrNull()?.id ?: ""
        }
    }

    // Heuristik parsing struk
    fun parseReceipt(text: String) {
        val cleanedText = text.replace(Regex("(\\d)\\s*([.,])\\s*(\\d)"), "$1$2$3")
        val lines = cleanedText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var detectedTotal = BigDecimal.ZERO
        var detectedDate = ""

        // Kata kunci total yang umum di Indonesia maupun internasional
        val totalKeywords = listOf(
            "total", "grand total", "jumlah", "grandtotal", "total bayar", 
            "subtotal", "sub total", "net total", "amount", 
            "netto", "net", "tagihan", "due", "total purchase"
        )
        // Regex pencocokan angka (mendukung angka desimal/ribuan)
        val numberRegex = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?|\\d+)")

        // Fungsi bantu membersihkan dan mengonversi string angka
        fun cleanNumberString(raw: String): BigDecimal? {
            var s = raw.replace(Regex("[^0-9.,]"), "").trim { it == '.' || it == ',' }
            if (s.isEmpty()) return null
            
            val hasDot = s.contains(".")
            val hasComma = s.contains(",")
            
            if (hasDot && hasComma) {
                val lastDot = s.lastIndexOf(".")
                val lastComma = s.lastIndexOf(",")
                if (lastComma > lastDot) {
                    val integerPart = s.substring(0, lastComma).replace(".", "")
                    val decimalPart = s.substring(lastComma + 1)
                    s = "$integerPart.$decimalPart"
                } else {
                    s = s.replace(",", "")
                }
            } else if (hasDot) {
                val parts = s.split(".")
                if (parts.size == 2) {
                    val decimalPart = parts[1]
                    if (decimalPart.length == 2) {
                        // Desimal desimal
                    } else {
                        s = s.replace(".", "")
                    }
                } else {
                    s = s.replace(".", "")
                }
            } else if (hasComma) {
                val parts = s.split(",")
                if (parts.size == 2) {
                    val decimalPart = parts[1]
                    if (decimalPart.length == 2) {
                        s = parts[0] + "." + decimalPart
                    } else {
                        s = s.replace(",", "")
                    }
                } else {
                    s = s.replace(",", "")
                }
            }
            return s.toBigDecimalOrNull()
        }

        // Loop baris demi baris mencari total
        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()
            
            if (totalKeywords.any { lowerLine.contains(it) }) {
                // Temukan angka di baris yang sama
                val matcher = numberRegex.matcher(line)
                val candidates = mutableListOf<BigDecimal>()
                while (matcher.find()) {
                    val numStr = matcher.group(1) ?: ""
                    cleanNumberString(numStr)?.let { candidates.add(it) }
                }
                
                if (candidates.isNotEmpty()) {
                    // Ambil angka terbesar di baris total
                    val maxCandidate = candidates.maxOrNull() ?: BigDecimal.ZERO
                    if (maxCandidate > detectedTotal) {
                        detectedTotal = maxCandidate
                    }
                } else {
                    // Jika baris total tidak mengandung angka, cek baris berikutnya (maks 2 baris ke bawah)
                    for (offset in 1..2) {
                        if (i + offset < lines.size) {
                            val nextLine = lines[i + offset]
                            val nextMatcher = numberRegex.matcher(nextLine)
                            val nextCandidates = mutableListOf<BigDecimal>()
                            while (nextMatcher.find()) {
                                val numStr = nextMatcher.group(1) ?: ""
                                cleanNumberString(numStr)?.let { nextCandidates.add(it) }
                            }
                            if (nextCandidates.isNotEmpty()) {
                                val maxCandidate = nextCandidates.maxOrNull() ?: BigDecimal.ZERO
                                if (maxCandidate > detectedTotal) {
                                    detectedTotal = maxCandidate
                                    break
                                }
                            }
                        }
                    }
                }
            }

            // Cari tanggal (dd/MM/yyyy atau dd-MM-yyyy atau yyyy-MM-dd)
            val dateRegex = Pattern.compile("(\\b\\d{2}[/-]\\d{2}[/-]\\d{4}\\b)|(\\b\\d{4}[/-]\\d{2}[/-]\\d{2}\\b)")
            val dateMatcher = dateRegex.matcher(line)
            if (dateMatcher.find()) {
                detectedDate = dateMatcher.group(0) ?: ""
            }
        }

        // Fallback: Jika tidak ditemukan total menggunakan kata kunci, cari angka terbesar di seluruh struk
        if (detectedTotal == BigDecimal.ZERO) {
            var maxEntireReceipt = BigDecimal.ZERO
            for (line in lines) {
                if (line.contains("/") || line.contains("-") || line.replace(Regex("[^0-9]"), "").length > 10) {
                    continue
                }
                val matcher = numberRegex.matcher(line)
                while (matcher.find()) {
                    val numStr = matcher.group(1) ?: ""
                    cleanNumberString(numStr)?.let { valAmt ->
                        if (valAmt > maxEntireReceipt && valAmt < BigDecimal("100000000")) {
                            maxEntireReceipt = valAmt
                        }
                    }
                }
            }
            detectedTotal = maxEntireReceipt
        }

        // Terapkan hasil pencocokan
        ocrTotal = if (detectedTotal > BigDecimal.ZERO) detectedTotal.toString() else ""
        ocrDate = detectedDate.ifEmpty {
            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
        }

        // Ekstrak item baris (Line Items) untuk Split Bill
        splitItems.clear()
        val itemPattern = Pattern.compile("^(.+?)\\s+(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?|\\d+)\\s*$", Pattern.CASE_INSENSITIVE)
        for (line in lines) {
            val m = itemPattern.matcher(line)
            if (m.find()) {
                val name = m.group(1)?.trim() ?: ""
                val priceStr = m.group(2) ?: ""
                val price = cleanNumberString(priceStr) ?: BigDecimal.ZERO
                if (price > BigDecimal.ZERO && !totalKeywords.any { name.lowercase().contains(it) }) {
                    splitItems.add(SplitBillItem(name = name, amount = price))
                }
            }
        }
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val uri = tempPhotoUri
            if (uri != null) {
                imageUri = uri
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            extractedText = visionText.text
                            parseReceipt(visionText.text)
                        }
                } catch (e: Exception) {
                    // Ignore ML Kit errors
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val uri = getTempPhotoUri(context)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        extractedText = visionText.text
                        parseReceipt(visionText.text)
                    }
            } catch (e: Exception) {
                // Ignore ML Kit errors
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pindai Struk OCR", fontWeight = FontWeight.Bold) },
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
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Pindai Nota Belanja Anda", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Proses OCR berjalan lokal dan gratis menggunakan ML Kit.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { launcher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Pilih Galeri", maxLines = 1, fontSize = 13.sp)
                            }
                            Button(
                                onClick = {
                                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCameraPermission) {
                                        val uri = getTempPhotoUri(context)
                                        tempPhotoUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Ambil Foto", maxLines = 1, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            if (extractedText.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Hasil Deteksi Struk", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = ocrTotal,
                                onValueChange = { ocrTotal = it },
                                label = { Text("Total Belanja") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = ocrDate,
                                onValueChange = { ocrDate = it },
                                label = { Text("Tanggal") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = ocrNote,
                                onValueChange = { ocrNote = it },
                                label = { Text("Catatan / Nama Merchant") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))

                            // Account selector
                            Text("Rekening Pembayaran", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            var accExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val accName = accounts.find { it.id == selectedAccountId }?.name ?: "Pilih Rekening"
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

                            Spacer(Modifier.height(8.dp))

                            // Category selector (hanya jika bukan split bill)
                            if (!isSplitMode) {
                                Text("Kategori Pengeluaran", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSplitMode, onCheckedChange = { isSplitMode = it })
                        Text("Pecah Tagihan (Split Bill per Item)", fontWeight = FontWeight.Bold)
                    }
                }

                if (isSplitMode) {
                    item {
                        Card {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Daftar Item Struk", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))

                                splitItems.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontSize = 13.sp)
                                            Text(LocalizationUtils.formatCurrency(item.amount, currencyActive, languageActive), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        // Category picker for this item
                                        var itemCatExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            val currentCatName = categories.find { it.id == item.categoryId }?.name ?: "Kategori"
                                            TextButton(onClick = { itemCatExpanded = true }) {
                                                Text(currentCatName, fontSize = 12.sp)
                                            }
                                            DropdownMenu(expanded = itemCatExpanded, onDismissRequest = { itemCatExpanded = false }) {
                                                categories.forEach { cat ->
                                                    DropdownMenuItem(text = { Text(cat.name) }, onClick = {
                                                        splitItems[index] = item.copy(categoryId = cat.id)
                                                        itemCatExpanded = false
                                                    })
                                                }
                                            }
                                        }

                                        IconButton(onClick = { splitItems.removeAt(index) }) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                TextButton(onClick = { splitItems.add(SplitBillItem("Item Baru", BigDecimal.ZERO)) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Tambah Item Manual")
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            val parsedAmount = ocrTotal.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val dateLong = try {
                                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                sdf.parse(ocrDate)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }

                            if (isSplitMode && splitItems.isNotEmpty()) {
                                // Buat draf terpisah untuk setiap item jika di-split
                                splitItems.forEach { item ->
                                    val draft = DraftTransaction(
                                        id = UUID.randomUUID().toString(),
                                        amount = item.amount,
                                        type = TransactionType.PENGELUARAN,
                                        categoryId = item.categoryId ?: selectedCategoryId.ifEmpty { null },
                                        accountId = selectedAccountId.ifEmpty { null },
                                        toAccountId = null,
                                        adminFee = null,
                                        note = "${ocrNote} - ${item.name}",
                                        dateTime = dateLong,
                                        tags = listOf("OCR", "SPLIT"),
                                        sourceInput = "OCR",
                                        transactionHash = UUID.randomUUID().toString(),
                                        isConfirmed = false
                                    )
                                    viewModel.insertDraft(draft)
                                }
                            } else {
                                // Buat draf tunggal
                                val draft = DraftTransaction(
                                    id = UUID.randomUUID().toString(),
                                    amount = parsedAmount,
                                    type = TransactionType.PENGELUARAN,
                                    categoryId = selectedCategoryId.ifEmpty { null },
                                    accountId = selectedAccountId.ifEmpty { null },
                                    toAccountId = null,
                                    adminFee = null,
                                    note = ocrNote,
                                    dateTime = dateLong,
                                    tags = listOf("OCR"),
                                    sourceInput = "OCR",
                                    transactionHash = UUID.randomUUID().toString(),
                                    isConfirmed = false
                                )
                                viewModel.insertDraft(draft)
                            }
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan Sebagai Draft")
                    }
                }
            }
        }
    }
}

data class SplitBillItem(
    val name: String,
    val amount: BigDecimal,
    val categoryId: String? = null
)
