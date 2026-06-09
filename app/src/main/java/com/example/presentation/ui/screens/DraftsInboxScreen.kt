package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.DraftTransaction
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsInboxScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditDraft: (String) -> Unit = {}
) {
    val drafts by viewModel.draftsState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()

    var selectedDrafts by remember { mutableStateOf(setOf<String>()) }
    val indonesianLocale = Locale("id", "ID")
    val rupiahFormat = NumberFormat.getNumberInstance(indonesianLocale)
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", indonesianLocale)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kotak Masuk Draft", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (drafts.isNotEmpty()) {
                        IconButton(onClick = {
                            if (selectedDrafts.size == drafts.size) {
                                selectedDrafts = emptySet()
                            } else {
                                selectedDrafts = drafts.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Pilih Semua")
                        }
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
            // Bulk Actions Bar
            AnimatedVisibility(visible = selectedDrafts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedDrafts.size} draft terpilih",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                viewModel.bulkDeleteDrafts(selectedDrafts.toList())
                                selectedDrafts = emptySet()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Buang")
                        }
                        Button(
                            onClick = {
                                val txsToConfirm = drafts.filter { it.id in selectedDrafts }.mapNotNull { d ->
                                    val finalAccId = d.accountId ?: accounts.firstOrNull()?.id ?: return@mapNotNull null
                                    val finalCatId = d.categoryId ?: categories.firstOrNull()?.id
                                    val tx = Transaction(
                                        id = UUID.randomUUID().toString(),
                                        amount = d.amount,
                                        type = d.type,
                                        categoryId = finalCatId,
                                        accountId = finalAccId,
                                        toAccountId = d.toAccountId,
                                        adminFee = d.adminFee,
                                        note = d.note,
                                        dateTime = d.dateTime,
                                        tags = d.tags,
                                        sourceInput = d.sourceInput,
                                        transactionHash = d.transactionHash,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    // Learn corrections on confirm
                                    if (d.note.isNotBlank()) {
                                        viewModel.learnCorrection(d.note, finalCatId, finalAccId)
                                    }
                                    Pair(d.id, tx)
                                }
                                viewModel.bulkConfirmDrafts(txsToConfirm)
                                selectedDrafts = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Konfirmasi")
                        }
                    }
                }
            }

            if (drafts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Kotak masuk draft kosong",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(drafts, key = { it.id }) { draft ->
                        DraftItemCard(
                            draft = draft,
                            accounts = accounts,
                            categories = categories,
                            isSelected = draft.id in selectedDrafts,
                            rupiahFormat = rupiahFormat,
                            dateFormat = dateFormat,
                            onToggleSelect = {
                                selectedDrafts = if (draft.id in selectedDrafts) {
                                    selectedDrafts - draft.id
                                } else {
                                    selectedDrafts + draft.id
                                }
                            },
                            onConfirm = { updatedDraft ->
                                val finalAccId = updatedDraft.accountId ?: accounts.firstOrNull()?.id
                                val finalCatId = updatedDraft.categoryId ?: categories.firstOrNull()?.id
                                if (finalAccId != null) {
                                    val tx = Transaction(
                                        id = UUID.randomUUID().toString(),
                                        amount = updatedDraft.amount,
                                        type = updatedDraft.type,
                                        categoryId = finalCatId,
                                        accountId = finalAccId,
                                        toAccountId = updatedDraft.toAccountId,
                                        adminFee = updatedDraft.adminFee,
                                        note = updatedDraft.note,
                                        dateTime = updatedDraft.dateTime,
                                        tags = updatedDraft.tags,
                                        sourceInput = updatedDraft.sourceInput,
                                        transactionHash = updatedDraft.transactionHash,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    // Learn corrections on confirm
                                    if (updatedDraft.note.isNotBlank()) {
                                        viewModel.learnCorrection(updatedDraft.note, finalCatId, finalAccId)
                                    }
                                    viewModel.confirmDraft(updatedDraft.id, tx)
                                }
                            },
                            onDelete = {
                                viewModel.deleteDraft(draft.id)
                            },
                            onSaveEdit = { updatedDraft ->
                                viewModel.insertDraft(updatedDraft)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DraftItemCard(
    draft: DraftTransaction,
    accounts: List<Account>,
    categories: List<Category>,
    isSelected: Boolean,
    rupiahFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onToggleSelect: () -> Unit,
    onConfirm: (DraftTransaction) -> Unit,
    onDelete: () -> Unit,
    onSaveEdit: (DraftTransaction) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var noteState by remember(draft.note) { mutableStateOf(draft.note) }
    var amountState by remember(draft.amount) { mutableStateOf(draft.amount.toString()) }
    var categoryIdState by remember(draft.categoryId) { mutableStateOf(draft.categoryId) }
    var accountIdState by remember(draft.accountId) { mutableStateOf(draft.accountId) }
    var typeState by remember(draft.type) { mutableStateOf(draft.type) }

    val containerBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .clickable { onToggleSelect() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Source Indicator
                SuggestionChip(
                    onClick = {},
                    label = { Text(draft.sourceInput, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.height(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dateFormat.format(Date(draft.dateTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (isEditing) {
            // Edit Fields
            OutlinedTextField(
                value = amountState,
                onValueChange = { amountState = it },
                label = { Text("Nominal") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = noteState,
                onValueChange = { noteState = it },
                label = { Text("Catatan") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // Dropdowns
            // Account Selection
            var accExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                val currentAccName = accounts.find { it.id == accountIdState }?.name ?: "Pilih Rekening"
                OutlinedButton(
                    onClick = { accExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(currentAccName)
                }
                DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.name) },
                            onClick = {
                                accountIdState = acc.id
                                accExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Category Selection
            if (typeState != TransactionType.TRANSFER) {
                var catExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentCatName = categories.find { it.id == categoryIdState }?.name ?: "Pilih Kategori"
                    OutlinedButton(
                        onClick = { catExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(currentCatName)
                    }
                    DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    categoryIdState = cat.id
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { isEditing = false }) {
                    Text("Batal")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsedAmount = amountState.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val updated = draft.copy(
                            amount = parsedAmount,
                            note = noteState,
                            categoryId = categoryIdState,
                            accountId = accountIdState
                        )
                        onSaveEdit(updated)
                        isEditing = false
                    }
                ) {
                    Text("Simpan")
                }
            }
        } else {
            // Read-only Details
            val amountColor = when (draft.type) {
                TransactionType.PEMASUKAN -> Color(0xFF4CAF50)
                TransactionType.PENGELUARAN -> Color(0xFFF44336)
                TransactionType.TRANSFER -> Color(0xFF2196F3)
                else -> MaterialTheme.colorScheme.onSurface
            }
            val prefix = when (draft.type) {
                TransactionType.PEMASUKAN -> "+"
                TransactionType.PENGELUARAN -> "-"
                TransactionType.TRANSFER -> "⇄"
                else -> ""
            }

            Text(
                text = "$prefix Rp ${rupiahFormat.format(draft.amount)}",
                color = amountColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = draft.note,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(8.dp))

            // Badges / Tags
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val accName = accounts.find { it.id == draft.accountId }?.name ?: "Dompet tidak dikenal"
                val catName = categories.find { it.id == draft.categoryId }?.name ?: "Tanpa Kategori"

                SuggestionChip(
                    onClick = {},
                    label = { Text("Rekening: $accName", fontSize = 11.sp) }
                )

                if (draft.type != TransactionType.TRANSFER) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Kategori: $catName", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = { isEditing = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(draft) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Konfirmasi", fontSize = 13.sp)
                }
            }
        }
    }
}
