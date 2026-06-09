package com.example.presentation.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.*
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.ui.components.IconMapper
import com.example.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditTx: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val accounts by viewModel.accountsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()

    // Query Search & Filtering States
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterAccountId by remember { mutableStateOf<String?>(null) }
    var selectedFilterCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedFilterType by remember { mutableStateOf<TransactionType?>(null) }
    var minAmountInput by remember { mutableStateOf("") }
    var maxAmountInput by remember { mutableStateOf("") }
    
    var showAdvancedFilters by remember { mutableStateOf(false) }

    // Pagination threshold state
    var visibleLimit by remember { mutableStateOf(20) }

    // Compute active filtering dynamically
    val filteredTransactions = remember(
        transactions, searchQuery, selectedFilterAccountId, 
        selectedFilterCategoryId, selectedFilterType, minAmountInput, maxAmountInput
    ) {
        val nonDeleted = transactions.filter { !it.deleted }
        nonDeleted.filter { tx ->
            val matchQuery = searchQuery.isEmpty() || 
                tx.note.contains(searchQuery, ignoreCase = true) || 
                tx.tags.any { it.contains(searchQuery, ignoreCase = true) }
            
            val matchAccount = selectedFilterAccountId == null || tx.accountId == selectedFilterAccountId || tx.toAccountId == selectedFilterAccountId
            val matchCategory = selectedFilterCategoryId == null || tx.categoryId == selectedFilterCategoryId
            val matchType = selectedFilterType == null || tx.type == selectedFilterType
            
            val minAmount = minAmountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val maxAmount = maxAmountInput.toBigDecimalOrNull() ?: BigDecimal("99999999999")
            val matchAmountRange = tx.amount in minAmount..maxAmount

            matchQuery && matchAccount && matchCategory && matchType && matchAmountRange
        }
    }

    // Paginated list
    val paginatedList = remember(filteredTransactions, visibleLimit) {
        filteredTransactions.take(visibleLimit)
    }

    // Auto load next page when scrolled near bottom
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collectLatest { layoutInfo ->
            val totalCells = layoutInfo.totalItemsCount
            val lastVisibleCell = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (totalCells > 0 && lastVisibleCell >= totalCells - 3) {
                // Near bottom, expand limit
                if (visibleLimit < filteredTransactions.size) {
                    visibleLimit += 20
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_transactions), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdvancedFilters = !showAdvancedFilters }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Toggle Filters",
                            tint = if (showAdvancedFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. KEYWORD SEARCH BAR
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // 2. ADVANCED EXPANDABLE FILTERS CARD
            AnimatedVisibility(visible = showAdvancedFilters) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                        // Filter by transaction types
                        Column {
                            Text(stringResource(R.string.filter_by_type), style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TransactionType.values().forEach { t ->
                                    val isSel = selectedFilterType == t
                                    val displayName = if (languageActive == "en") {
                                        when (t) {
                                            TransactionType.PEMASUKAN -> "Income"
                                            TransactionType.PENGELUARAN -> "Expense"
                                            TransactionType.TRANSFER -> "Transfer"
                                            TransactionType.RECONCILE -> "Reconcile"
                                        }
                                    } else {
                                        when (t) {
                                            TransactionType.PEMASUKAN -> "Pemasukan"
                                            TransactionType.PENGELUARAN -> "Pengeluaran"
                                            TransactionType.TRANSFER -> "Transfer"
                                            TransactionType.RECONCILE -> "Penyesuaian"
                                        }
                                    }
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedFilterType = if (isSel) null else t },
                                        label = { Text(displayName, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Filter by numerical limits Min & Max
                        Text(stringResource(R.string.filter_by_amount_range), style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = minAmountInput,
                                onValueChange = { minAmountInput = it },
                                placeholder = { Text("Min") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = maxAmountInput,
                                onValueChange = { maxAmountInput = it },
                                placeholder = { Text("Max") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        // Reset buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    selectedFilterAccountId = null
                                    selectedFilterCategoryId = null
                                    selectedFilterType = null
                                    minAmountInput = ""
                                    maxAmountInput = ""
                                    searchQuery = ""
                                }
                            ) {
                                Text(stringResource(R.string.action_reset), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // 1B. LIST ROW WITH RECENT CHIPS FOR ACCOUNTS & CATEGORIES
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilterAccountId == null,
                        onClick = { selectedFilterAccountId = null },
                        label = { Text(if (languageActive == "en") "All Accounts" else "Semua Dompet") }
                    )
                }
                items(accounts) { acc ->
                    FilterChip(
                        selected = selectedFilterAccountId == acc.id,
                        onClick = { selectedFilterAccountId = acc.id },
                        label = { Text(acc.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. PAGINATED RECYCLING LIST VIEW
            if (paginatedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.filter_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(paginatedList, key = { it.id }) { tx ->
                        val acc = accounts.find { it.id == tx.accountId }
                        val catName = if (tx.categoryId != null) {
                            categories.find { it.id == tx.categoryId }?.name ?: ""
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
                            modifier = Modifier.fillMaxWidth(),
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
                                    val catIcon = if (tx.categoryId != null) {
                                        categories.find { it.id == tx.categoryId }?.icon ?: "category"
                                    } else "swap_horiz"

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconMapper.getIconByName(catIcon),
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
                                            text = "${acc?.name ?: ""} • ${LocalizationUtils.formatDateTimeByLocale(tx.dateTime, languageActive)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (tx.tags.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = tx.tags.joinToString(" ") { "#$it" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$prefix${LocalizationUtils.formatCurrency(tx.amount, currencyActive, languageActive)}",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = valueColor
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = { onNavigateToEditTx(tx.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                val deletedTx = tx
                                                viewModel.deleteTransaction(tx.id)
                                                scope.launch {
                                                    val res = snackbarHostState.showSnackbar(
                                                        message = context.getString(R.string.tx_deleted_message),
                                                        actionLabel = context.getString(R.string.action_undo),
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (res == SnackbarResult.ActionPerformed) {
                                                        viewModel.insertTransaction(deletedTx)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
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
        }
    }
}

// Helper expansion for converting standard input string to BigDecimal
fun String.toBigDecimalOrNull(): BigDecimal? {
    return try { BigDecimal(this) } catch (e: Exception) { null }
}
