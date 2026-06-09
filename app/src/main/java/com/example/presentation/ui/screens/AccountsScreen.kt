package com.example.presentation.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.ui.components.IconMapper
import com.example.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val accounts by viewModel.accountsState.collectAsState()
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()

    var showAccountDialog by remember { mutableStateOf(false) }
    var selectedAccountForEdit by remember { mutableStateOf<Account?>(null) }

    // Dialog state variables
    var accountName by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf(AccountType.BANK) }
    var initialBalanceStr by remember { mutableStateOf("") }
    var accountIcon by remember { mutableStateOf("account_balance") }
    var accountColor by remember { mutableStateOf("#2196F3") }
    var isArchived by remember { mutableStateOf(false) }

    fun openAddDialog() {
        selectedAccountForEdit = null
        accountName = ""
        accountType = AccountType.BANK
        initialBalanceStr = "0"
        accountIcon = "account_balance"
        accountColor = "#2196F3"
        isArchived = false
        showAccountDialog = true
    }

    fun openEditDialog(account: Account) {
        selectedAccountForEdit = account
        accountName = account.name
        accountType = account.type
        initialBalanceStr = account.initialBalance.toPlainString()
        accountIcon = account.icon
        accountColor = account.color
        isArchived = account.isArchived
        showAccountDialog = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_label_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = ::openAddDialog) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Account")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (languageActive == "en") "No wallets/accounts created." else "Belum ada dompet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = ::openAddDialog) {
                        Text(stringResource(R.string.account_label_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(accounts) { account ->
                    val colorHex = try {
                        Color(android.graphics.Color.parseColor(account.color))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.secondary
                    }

                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
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
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(colorHex.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIconByName(account.icon),
                                        contentDescription = null,
                                        tint = colorHex,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = account.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (account.isArchived) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (languageActive == "en") "Archived" else "Arsip",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    
                                    val typeLabel = when (account.type) {
                                        AccountType.BANK -> stringResource(R.string.actype_bank)
                                        AccountType.E_WALLET -> stringResource(R.string.actype_ewallet)
                                        AccountType.BROKER -> stringResource(R.string.actype_broker)
                                        AccountType.DEPOSITO -> stringResource(R.string.actype_deposito)
                                        AccountType.TUNAI -> stringResource(R.string.actype_tunai)
                                        AccountType.KARTU_KREDIT -> stringResource(R.string.actype_credit)
                                        AccountType.CUSTOM -> stringResource(R.string.actype_custom)
                                    }
                                    
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = LocalizationUtils.formatCurrency(account.balance, currencyActive, languageActive),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (account.balance < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { openEditDialog(account) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            val deletedAccount = account
                                            viewModel.deleteAccount(account.id)
                                            // Trigger snackbar with undo
                                            scope.launch {
                                                val res = snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.account_deleted_message, deletedAccount.name),
                                                    actionLabel = context.getString(R.string.action_undo),
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    viewModel.insertAccount(deletedAccount)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add/Edit Dialog Block
        if (showAccountDialog) {
            AlertDialog(
                onDismissRequest = { showAccountDialog = false },
                title = { Text(selectedAccountForEdit?.let { stringResource(R.string.account_label_edit) } ?: stringResource(R.string.account_label_add)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = { accountName = it },
                            label = { Text(stringResource(R.string.account_label_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Selector for Account Type
                        Column {
                            Text(stringResource(R.string.account_label_type), style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val types = AccountType.values()
                                var expandedType by remember { mutableStateOf(false) }
                                
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val selectedTypeLabel = when (accountType) {
                                        AccountType.BANK -> stringResource(R.string.actype_bank)
                                        AccountType.E_WALLET -> stringResource(R.string.actype_ewallet)
                                        AccountType.BROKER -> stringResource(R.string.actype_broker)
                                        AccountType.DEPOSITO -> stringResource(R.string.actype_deposito)
                                        AccountType.TUNAI -> stringResource(R.string.actype_tunai)
                                        AccountType.KARTU_KREDIT -> stringResource(R.string.actype_credit)
                                        AccountType.CUSTOM -> stringResource(R.string.actype_custom)
                                    }
                                    ElevatedButton(
                                        onClick = { expandedType = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(selectedTypeLabel)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = expandedType,
                                        onDismissRequest = { expandedType = false }
                                    ) {
                                        types.forEach { t ->
                                            val label = when (t) {
                                                AccountType.BANK -> stringResource(R.string.actype_bank)
                                                AccountType.E_WALLET -> stringResource(R.string.actype_ewallet)
                                                AccountType.BROKER -> stringResource(R.string.actype_broker)
                                                AccountType.DEPOSITO -> stringResource(R.string.actype_deposito)
                                                AccountType.TUNAI -> stringResource(R.string.actype_tunai)
                                                AccountType.KARTU_KREDIT -> stringResource(R.string.actype_credit)
                                                AccountType.CUSTOM -> stringResource(R.string.actype_custom)
                                            }
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    accountType = t
                                                    expandedType = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Nominal Initial Balance
                        OutlinedTextField(
                            value = initialBalanceStr,
                            onValueChange = { initialBalanceStr = it },
                            label = { Text(stringResource(R.string.account_label_initial)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Color selection pallet
                        Column {
                            Text(stringResource(R.string.account_label_color), style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val colors = listOf("#2196F3", "#00BCD4", "#4CAF50", "#FF9800", "#FF5722", "#9C27B0")
                                colors.forEach { colorString ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(colorString)))
                                            .clickable { accountColor = colorString }
                                            .padding(2.dp)
                                    ) {
                                        if (accountColor == colorString) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Icon configuration selector
                        Column {
                            Text(stringResource(R.string.account_label_icon), style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val icons = listOf("account_balance", "wallet", "payments", "credit_card")
                                icons.forEach { iconString ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (accountIcon == iconString) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.outlineVariant
                                            )
                                            .clickable { accountIcon = iconString },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconMapper.getIconByName(iconString),
                                            contentDescription = null,
                                            tint = if (accountIcon == iconString) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Archives checkbox (only visible on editing)
                        if (selectedAccountForEdit != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isArchived, onCheckedChange = { isArchived = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.account_is_archived), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (accountName.isEmpty()) {
                                val emptyMsg = if (languageActive == "en") "Wallet name cannot be empty!" else "Nama dompet tidak boleh kosong!"
                                Toast.makeText(context, emptyMsg, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val initial = try { BigDecimal(initialBalanceStr) } catch (e: Exception) { BigDecimal.ZERO }

                            val account = Account(
                                id = selectedAccountForEdit?.id ?: UUID.randomUUID().toString(),
                                name = accountName,
                                type = accountType,
                                icon = accountIcon,
                                color = accountColor,
                                initialBalance = initial,
                                isArchived = isArchived,
                                updatedAt = System.currentTimeMillis()
                            )

                            viewModel.insertAccount(account)
                            showAccountDialog = false
                            val successMsg = if (languageActive == "en") "Save successful" else "Simpan sukses"
                            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAccountDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}
