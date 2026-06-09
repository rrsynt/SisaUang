package com.example.presentation.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.*
import com.example.domain.utils.LocalizationUtils
import com.example.domain.utils.MathExpressionParser
import com.example.presentation.ui.components.IconMapper
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEntryScreen(
    viewModel: MainViewModel,
    editingTransactionId: String? = null, // If present, we are editing this transaction id
    voiceActive: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToAccounts: () -> Unit = {}
) {
    val context = LocalContext.current
    val accounts by viewModel.accountsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()

    // Category management states
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<Category?>(null) }
    
    var categoryNameInput by remember { mutableStateOf("") }
    var categoryIconInput by remember { mutableStateOf("category") }
    var categoryColorInput by remember { mutableStateOf("#4CAF50") }
    var categoryIsFavInput by remember { mutableStateOf(false) }
    var categorySortOrderInput by remember { mutableStateOf(0) }
    var categoryParentIdInput by remember { mutableStateOf<String?>(null) }

    // Screen central variables
    var currentType by remember { mutableStateOf(TransactionType.PENGELUARAN) }
    var expressionInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf("") }
    
    // Transfer specifically
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var adminFeeInput by remember { mutableStateOf("") }

    // Otomasi Input: Suara & Teks Bebas
    var freeTextInput by remember { mutableStateOf("") }
    val corrections by viewModel.correctionsState.collectAsState()

    val speechRecognizerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                freeTextInput = spokenText
            }
        }
    }

    LaunchedEffect(Unit) {
        if (voiceActive) {
            val promptMsg = if (languageActive == "en") "Say something... (e.g., lunch 15k cash)" else "Katakan sesuatu... (contoh: makan 15rb gopay)"
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (languageActive == "en") "en-US" else "id-ID")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, promptMsg)
            }
            speechRecognizerLauncher.launch(intent)
        }
    }

    val parsedResult = remember(freeTextInput, accounts, categories, corrections) {
        if (freeTextInput.isBlank()) null
        else {
            com.example.domain.utils.FreeTextParser.parse(
                freeTextInput,
                accounts,
                categories,
                corrections
            )
        }
    }

    fun applyParsedResult() {
        val res = parsedResult ?: return
        expressionInput = res.amount.toPlainString()
        noteInput = res.note
        currentType = res.type
        if (res.account != null) selectedAccountId = res.account.id
        if (res.toAccount != null) toAccountId = res.toAccount.id
        if (res.category != null) selectedCategoryId = res.category.id
    }
    
    // Dynamic prefilled smart-defaults or edit pre-population
    LaunchedEffect(transactions, accounts, categories, currencyActive) {
        if (editingTransactionId != null) {
            val tx = transactions.find { it.id == editingTransactionId }
            if (tx != null) {
                currentType = tx.type
                val amt = if (currencyActive == "USD") {
                    tx.amount.divide(com.example.domain.utils.LocalizationUtils.usdToIdrRate, 2, java.math.RoundingMode.HALF_UP)
                } else {
                    tx.amount
                }
                expressionInput = amt.stripTrailingZeros().toPlainString()
                noteInput = tx.note
                selectedCategoryId = tx.categoryId
                selectedAccountId = tx.accountId
                toAccountId = tx.toAccountId
                val fee = tx.adminFee
                adminFeeInput = if (fee != null) {
                    val convertedFee = if (currencyActive == "USD") {
                        fee.divide(com.example.domain.utils.LocalizationUtils.usdToIdrRate, 2, java.math.RoundingMode.HALF_UP)
                    } else {
                        fee
                    }
                    convertedFee.stripTrailingZeros().toPlainString()
                } else ""
                tagInput = tx.tags.joinToString(",")
            }
        } else if (transactions.isNotEmpty() && accounts.isNotEmpty()) {
            // Smart prefill from the last non-deleted transaction!
            val lastTx = transactions.firstOrNull { !it.deleted }
            if (lastTx != null) {
                selectedAccountId = lastTx.accountId
                selectedCategoryId = lastTx.categoryId
                currentType = lastTx.type
            } else {
                selectedAccountId = accounts.first().id
                selectedCategoryId = categories.firstOrNull { it.type == CategoryType.EXPENSE }?.id
            }
        } else if (accounts.isNotEmpty()) {
            selectedAccountId = accounts.first().id
            selectedCategoryId = categories.firstOrNull { it.type == CategoryType.EXPENSE }?.id
        }
    }

    // Dynamic result display of nominal calculator evaluation
    val evaluatedAmount: BigDecimal by remember(expressionInput) {
        derivedStateOf {
            MathExpressionParser.parseAndCompute(expressionInput) ?: BigDecimal.ZERO
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editingTransactionId != null) stringResource(R.string.tx_label_edit) else stringResource(R.string.tx_label_add),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (accounts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (languageActive == "en") "No Wallet / Account Found" else "Belum Ada Dompet / Akun",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (languageActive == "en") 
                        "You must create a wallet or account first before recording transactions." 
                    else 
                        "Anda harus membuat dompet atau akun terlebih dahulu sebelum mencatat transaksi.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onNavigateToAccounts,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (languageActive == "en") "Create Account / Wallet" else "Buat Dompet / Akun")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onNavigateBack) {
                    Text(if (languageActive == "en") "Go Back" else "Kembali")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.background)
            ) {
            // Card Perekam Suara & NLP Teks Bebas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Catat Cepat (Teks / Suara)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = freeTextInput,
                            onValueChange = { freeTextInput = it },
                            placeholder = { Text("Ketik atau katakan sesuatu...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Katakan sesuatu... (contoh: makan 15rb gopay)")
                                }
                                speechRecognizerLauncher.launch(intent)
                            }
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (parsedResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val formattedAmt = try {
                            java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID")).format(parsedResult.amount)
                        } catch (e: Exception) { parsedResult.amount.toString() }

                        val typeName = if (languageActive == "en") {
                            when (parsedResult.type) {
                                TransactionType.PEMASUKAN -> "Income"
                                TransactionType.PENGELUARAN -> "Expense"
                                TransactionType.TRANSFER -> "Transfer"
                                TransactionType.RECONCILE -> "Reconcile"
                            }
                        } else {
                            when (parsedResult.type) {
                                TransactionType.PEMASUKAN -> "Pemasukan"
                                TransactionType.PENGELUARAN -> "Pengeluaran"
                                TransactionType.TRANSFER -> "Transfer"
                                TransactionType.RECONCILE -> "Penyesuaian"
                            }
                        }
                        val displayAmt = if (currencyActive == "USD") {
                            val usdVal = parsedResult.amount.divide(LocalizationUtils.usdToIdrRate, 2, java.math.RoundingMode.HALF_UP)
                            "$ $usdVal"
                        } else {
                            "Rp $formattedAmt"
                        }
                        val infoText = if (languageActive == "en") {
                            "Detected: $displayAmt | Type: $typeName | Note: ${parsedResult.note}"
                        } else {
                            "Terdeteksi: $displayAmt | Tipe: $typeName | Catatan: ${parsedResult.note}"
                        }

                        Text(
                            text = infoText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                applyParsedResult()
                                freeTextInput = ""
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (languageActive == "en") "Apply to Form" else "Terapkan ke Form")
                        }
                    }
                }
            }

            // 1. SELECT TRANSACTION TYPE
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val types = TransactionType.values()
                types.forEachIndexed { index, type ->
                    val isSel = currentType == type
                    SegmentedButton(
                        selected = isSel,
                        onClick = {
                            currentType = type
                            // Reset categories type accordingly
                            if (type == TransactionType.PEMASUKAN) {
                                selectedCategoryId = categories.firstOrNull { it.type == CategoryType.INCOME }?.id
                            } else if (type == TransactionType.PENGELUARAN) {
                                selectedCategoryId = categories.firstOrNull { it.type == CategoryType.EXPENSE }?.id
                            } else {
                                selectedCategoryId = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                    ) {
                        Text(
                            text = when (type) {
                                TransactionType.PEMASUKAN -> stringResource(R.string.txtype_income)
                                TransactionType.PENGELUARAN -> stringResource(R.string.txtype_expense)
                                TransactionType.TRANSFER -> if (languageActive == "en") "Transfer" else "Transfer"
                                TransactionType.RECONCILE -> if (languageActive == "en") "Recon" else "Penyesuaian"
                            },
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 2. NOMINAL CALCULATOR VALUE SCREEN CARD
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = expressionInput.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "= ${LocalizationUtils.formatCurrency(evaluatedAmount, currencyActive, languageActive)}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // 3. SELECT SOURCE ACCOUNT AND TRANSFER DESTINATIONS
            Text(
                text = stringResource(R.string.tx_label_account),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(accounts) { acc ->
                    val itemColor = try {
                        Color(android.graphics.Color.parseColor(acc.color))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.secondary
                    }
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text(acc.name) },
                        leadingIcon = {
                            Icon(IconMapper.getIconByName(acc.icon), "Icon", tint = itemColor)
                        }
                    )
                }
            }

            if (currentType == TransactionType.TRANSFER) {
                // Selector for dynamic destination account
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.tx_label_to_account),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(accounts.filter { it.id != selectedAccountId }) { acc ->
                        val itemColor = try {
                            Color(android.graphics.Color.parseColor(acc.color))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.secondary
                        }
                        FilterChip(
                            selected = toAccountId == acc.id,
                            onClick = { toAccountId = acc.id },
                            label = { Text(acc.name) },
                            leadingIcon = {
                                Icon(IconMapper.getIconByName(acc.icon), "Icon", tint = itemColor)
                            }
                        )
                    }
                }

                // Selector for dynamic transfer admin fee
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = adminFeeInput,
                    onValueChange = { adminFeeInput = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    label = { Text(stringResource(R.string.tx_label_fee)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // 4. CATEGORIZATION LISTS WITH FAVORITE EMBLEMS
            if (currentType == TransactionType.PEMASUKAN || currentType == TransactionType.PENGELUARAN) {
                val activeCategories = remember(categories, currentType) {
                    categories.filter {
                        val expectedType = if (currentType == TransactionType.PEMASUKAN) CategoryType.INCOME else CategoryType.EXPENSE
                        it.type == expectedType
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tx_label_category),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = { showManageCategoriesDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kelola", fontSize = 12.sp)
                    }
                }
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(activeCategories) { cat ->
                        val itemColor = try {
                            Color(android.graphics.Color.parseColor(cat.color))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.secondary
                        }
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Icon(IconMapper.getIconByName(cat.icon), "Icon", tint = itemColor)
                            }
                        )
                    }
                }
            }

            // 5. MEMO NOTE, TAGS & PHOTOS
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text(stringResource(R.string.tx_label_note)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = { Text(stringResource(R.string.tx_label_tags)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 6. ACTION CONTROLLER PAD: KEYPAD AND SAVING TRIGGER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: keypad inputs & backspaces
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val keyRows = listOf(
                        listOf("1", "2", "3", "+"),
                        listOf("4", "5", "6", "-"),
                        listOf("7", "8", "9", "*"),
                        listOf(".", "0", "C", "/")
                    )
                    
                    keyRows.forEach { r ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            r.forEach { k ->
                                ElevatedButton(
                                    onClick = {
                                        if (k == "C") {
                                            expressionInput = ""
                                        } else {
                                            expressionInput += k
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(k, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Right Column: Big save & delete backspace
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Backspace button
                    IconButton(
                        onClick = {
                            if (expressionInput.isNotEmpty()) {
                                expressionInput = expressionInput.dropLast(1)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Large dynamic Save Button
                    Button(
                        onClick = {
                            // Validation checks
                            if (evaluatedAmount <= BigDecimal.ZERO) {
                                val zeroMsg = if (languageActive == "en") {
                                    if (currencyActive == "USD") "Amount must be greater than $0!" else "Amount must be greater than Rp0!"
                                } else {
                                    if (currencyActive == "USD") "Nominal harus lebih besar dari $0!" else "Nominal harus lebih besar dari Rp0!"
                                }
                                Toast.makeText(context, zeroMsg, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedAccountId.isEmpty()) {
                                val msg = if (languageActive == "en") "Please select a wallet first!" else "Pilih dompet terlebih dahulu!"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (currentType == TransactionType.TRANSFER && toAccountId == null) {
                                val msg = if (languageActive == "en") "Please select a destination wallet!" else "Pilih dompet tujuan transfer!"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val finalAmount = if (currencyActive == "USD") {
                                evaluatedAmount.multiply(com.example.domain.utils.LocalizationUtils.usdToIdrRate)
                            } else {
                                evaluatedAmount
                            }

                            val fee = if (adminFeeInput.isNotEmpty()) {
                                try {
                                    val parsedFee = BigDecimal(adminFeeInput)
                                    if (currencyActive == "USD") {
                                        parsedFee.multiply(com.example.domain.utils.LocalizationUtils.usdToIdrRate)
                                    } else {
                                        parsedFee
                                    }
                                } catch (e: Exception) { null }
                            } else null

                            val tx = Transaction(
                                id = editingTransactionId ?: UUID.randomUUID().toString(),
                                amount = finalAmount,
                                type = currentType,
                                categoryId = selectedCategoryId,
                                subCategoryId = null,
                                accountId = selectedAccountId,
                                toAccountId = toAccountId,
                                adminFee = fee,
                                note = noteInput,
                                tags = if (tagInput.isEmpty()) emptyList() else tagInput.split(","),
                                dateTime = System.currentTimeMillis()
                            )

                            viewModel.insertTransaction(tx)
                            val saveMsg = if (languageActive == "en") "Transaction saved!" else "Transaksi disimpan!"
                            Toast.makeText(context, saveMsg, Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("SAVE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
        }

        // ---------------- CATEGORY DIALOGS ----------------

        // 1. Manage Categories Dialog
        if (showManageCategoriesDialog) {
            val expectedType = if (currentType == TransactionType.PEMASUKAN) CategoryType.INCOME else CategoryType.EXPENSE
            val typeCategories = categories.filter { !it.deleted && it.type == expectedType }

            AlertDialog(
                onDismissRequest = { showManageCategoriesDialog = false },
                title = { Text("Kelola Kategori") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                // Reset form and show add dialog
                                categoryNameInput = ""
                                categoryIconInput = "category"
                                categoryColorInput = "#4CAF50"
                                categoryIsFavInput = false
                                categorySortOrderInput = 0
                                categoryParentIdInput = null
                                showAddCategoryDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tambah Kategori Baru")
                        }

                        if (typeCategories.isEmpty()) {
                            Text("Belum ada kategori.", modifier = Modifier.padding(16.dp))
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(typeCategories) { cat ->
                                    val catColor = try {
                                        Color(android.graphics.Color.parseColor(cat.color))
                                    } catch (e: Exception) {
                                        MaterialTheme.colorScheme.secondary
                                    }

                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(32.dp).background(catColor.copy(alpha = 0.15f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = IconMapper.getIconByName(cat.icon),
                                                        contentDescription = null,
                                                        tint = catColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(cat.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                                    if (cat.parentId != null) {
                                                        val parentCat = categories.find { it.id == cat.parentId }
                                                        Text("Sub-kategori dari: ${parentCat?.name ?: ""}", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Favorite star
                                                IconButton(
                                                    onClick = {
                                                        viewModel.insertCategory(cat.copy(isFavorite = !cat.isFavorite, updatedAt = System.currentTimeMillis()))
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (cat.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = null,
                                                        tint = if (cat.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                // Sort order buttons
                                                IconButton(
                                                    onClick = {
                                                        viewModel.insertCategory(cat.copy(order = cat.order - 1, updatedAt = System.currentTimeMillis()))
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.insertCategory(cat.copy(order = cat.order + 1, updatedAt = System.currentTimeMillis()))
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(16.dp))
                                                }

                                                // Edit
                                                IconButton(
                                                    onClick = {
                                                        categoryNameInput = cat.name
                                                        categoryIconInput = cat.icon
                                                        categoryColorInput = cat.color
                                                        categoryIsFavInput = cat.isFavorite
                                                        categorySortOrderInput = cat.order
                                                        categoryParentIdInput = cat.parentId
                                                        showEditCategoryDialog = cat
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                                }

                                                // Delete
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteCategory(cat.id)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showManageCategoriesDialog = false }) {
                        Text("Tutup")
                    }
                }
            )
        }

        // 2. Add Category Dialog
        if (showAddCategoryDialog) {
            val expectedType = if (currentType == TransactionType.PEMASUKAN) CategoryType.INCOME else CategoryType.EXPENSE
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = { Text("Tambah Kategori Baru") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = categoryNameInput,
                            onValueChange = { categoryNameInput = it },
                            label = { Text("Nama Kategori") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Sub-category selector: can choose parent category
                        val potentialParents = categories.filter { !it.deleted && it.type == expectedType && it.parentId == null }
                        var parentMenuExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = potentialParents.find { it.id == categoryParentIdInput }?.name ?: "Kategori Utama",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sub-kategori dari (Opsional)") },
                                trailingIcon = { IconButton(onClick = { parentMenuExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = parentMenuExpanded, onDismissRequest = { parentMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Kategori Utama") }, onClick = { categoryParentIdInput = null; parentMenuExpanded = false })
                                potentialParents.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.name) }, onClick = { categoryParentIdInput = p.id; parentMenuExpanded = false })
                                }
                            }
                        }

                        // Colors list selector
                        val colors = listOf("#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63", "#FFC107", "#F44336", "#00BCD4", "#9E9E9E", "#00C853", "#0288D1", "#607D8B")
                        Text("Pilih Warna", style = MaterialTheme.typography.bodySmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            colors.take(6).forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(color, CircleShape)
                                        .clickable { categoryColorInput = colorHex }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (categoryColorInput == colorHex) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            colors.drop(6).forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(color, CircleShape)
                                        .clickable { categoryColorInput = colorHex }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (categoryColorInput == colorHex) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Icons selector
                        val icons = listOf("restaurant", "directions_car", "shopping_bag", "sports_esports", "receipt_long", "medical_services", "payments", "trending_up", "home", "work", "flight", "school")
                        Text("Pilih Ikon", style = MaterialTheme.typography.bodySmall)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(icons) { iconName ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (categoryIconInput == iconName) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        .clickable { categoryIconInput = iconName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIconByName(iconName),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (categoryIconInput == iconName) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (categoryNameInput.isNotEmpty()) {
                                val expectedType = if (currentType == TransactionType.PEMASUKAN) CategoryType.INCOME else CategoryType.EXPENSE
                                val newCat = Category(
                                    id = UUID.randomUUID().toString(),
                                    name = categoryNameInput,
                                    type = expectedType,
                                    icon = categoryIconInput,
                                    color = categoryColorInput,
                                    isFavorite = categoryIsFavInput,
                                    order = categorySortOrderInput,
                                    parentId = categoryParentIdInput,
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.insertCategory(newCat)
                                showAddCategoryDialog = false
                            }
                        }
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // 3. Edit Category Dialog
        if (showEditCategoryDialog != null) {
            val editingCat = showEditCategoryDialog!!
            val expectedType = editingCat.type
            AlertDialog(
                onDismissRequest = { showEditCategoryDialog = null },
                title = { Text("Edit Kategori") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = categoryNameInput,
                            onValueChange = { categoryNameInput = it },
                            label = { Text("Nama Kategori") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Sub-category selector: can choose parent category
                        val potentialParents = categories.filter { !it.deleted && it.type == expectedType && it.parentId == null && it.id != editingCat.id }
                        var parentMenuExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = potentialParents.find { it.id == categoryParentIdInput }?.name ?: "Kategori Utama",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sub-kategori dari (Opsional)") },
                                trailingIcon = { IconButton(onClick = { parentMenuExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = parentMenuExpanded, onDismissRequest = { parentMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Kategori Utama") }, onClick = { categoryParentIdInput = null; parentMenuExpanded = false })
                                potentialParents.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.name) }, onClick = { categoryParentIdInput = p.id; parentMenuExpanded = false })
                                }
                            }
                        }

                        // Colors list selector
                        val colors = listOf("#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63", "#FFC107", "#F44336", "#00BCD4", "#9E9E9E", "#00C853", "#0288D1", "#607D8B")
                        Text("Pilih Warna", style = MaterialTheme.typography.bodySmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            colors.take(6).forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(color, CircleShape)
                                        .clickable { categoryColorInput = colorHex }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (categoryColorInput == colorHex) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            colors.drop(6).forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(color, CircleShape)
                                        .clickable { categoryColorInput = colorHex }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (categoryColorInput == colorHex) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Icons selector
                        val icons = listOf("restaurant", "directions_car", "shopping_bag", "sports_esports", "receipt_long", "medical_services", "payments", "trending_up", "home", "work", "flight", "school")
                        Text("Pilih Ikon", style = MaterialTheme.typography.bodySmall)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(icons) { iconName ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (categoryIconInput == iconName) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        .clickable { categoryIconInput = iconName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIconByName(iconName),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (categoryIconInput == iconName) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (categoryNameInput.isNotEmpty()) {
                                val updatedCat = editingCat.copy(
                                    name = categoryNameInput,
                                    icon = categoryIconInput,
                                    color = categoryColorInput,
                                    parentId = categoryParentIdInput,
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.insertCategory(updatedCat)
                                showEditCategoryDialog = null
                            }
                        }
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditCategoryDialog = null }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}
