package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.ui.screens.*
import com.example.presentation.viewmodel.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupAppShortcuts()

        setContent {
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = MainViewModel.Factory(application)
            )

            // Dynamic User Preference Hooks
            val activeLanguage by viewModel.languageState.collectAsState()
            val activeTheme by viewModel.themeState.collectAsState()
            val activeCurrencyState = viewModel.currencyState.collectAsState()
            val activeCurrency = activeCurrencyState.value
            val onboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
            val isLocked by viewModel.isLocked.collectAsState()

            LaunchedEffect(activeCurrency, activeLanguage) {
                com.example.domain.utils.LocalizationUtils.activeCurrency = activeCurrency
                com.example.domain.utils.LocalizationUtils.activeLanguage = activeLanguage
            }

            val darkTheme = when (activeTheme) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            // High Precision, Instant Localization Injector Context Wrapping
            val localizedContext = remember(activeLanguage) {
                LocalizationUtils.updateLocale(this@MainActivity, activeLanguage)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                MyApplicationTheme(darkTheme = darkTheme) {
                    val navController = rememberNavController()

                    val intentAction = this@MainActivity.intent?.action
                    val intentRoute = this@MainActivity.intent?.getStringExtra("ROUTE")
                    LaunchedEffect(intentAction, intentRoute, isLocked) {
                        if (intentAction == "ACTION_QUICK_RECORD" && onboardingCompleted && !isLocked) {
                            this@MainActivity.intent?.action = null
                            navController.navigate("transaction_entry")
                        } else if (intentRoute != null && onboardingCompleted && !isLocked) {
                            this@MainActivity.intent?.removeExtra("ROUTE")
                            navController.navigate(intentRoute)
                        }
                    }

                    if (!onboardingCompleted) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onOnboardingComplete = {}
                        )
                    } else if (isLocked) {
                        PinLockScreen(
                            viewModel = viewModel,
                            onSuccess = {
                                viewModel.unlockApp()
                            }
                        )
                    } else {
                        // Core Authenticated Area with Bottom Navigation tabs
                        val backstackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = backstackEntry?.destination?.route ?: "dashboard"

                        val bottomTabRoutes = listOf("dashboard", "transactions", "accounts", "settings")

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                if (currentRoute in bottomTabRoutes) {
                                    NavigationBar {
                                        bottomTabRoutes.forEach { route ->
                                            val isSel = currentRoute == route
                                            NavigationBarItem(
                                                selected = isSel,
                                                onClick = {
                                                    navController.navigate(route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = when (route) {
                                                            "dashboard" -> Icons.Default.Home
                                                            "transactions" -> Icons.Default.History
                                                            "accounts" -> Icons.Default.AccountBalance
                                                            else -> Icons.Default.Settings
                                                        },
                                                        contentDescription = route,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = when (route) {
                                                            "dashboard" -> stringResource(R.string.nav_dashboard)
                                                            "transactions" -> stringResource(R.string.nav_transactions)
                                                            "accounts" -> stringResource(R.string.nav_accounts)
                                                            else -> stringResource(R.string.nav_settings)
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = "dashboard",
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                 composable("dashboard") {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateToNewTx = { voiceActive ->
                                            val route = if (voiceActive) "transaction_entry?voiceActive=true" else "transaction_entry"
                                            navController.navigate(route)
                                        },
                                        onNavigateToEditTx = { txId -> navController.navigate("transaction_entry?transactionId=$txId") },
                                        onNavigateToTransactions = { navController.navigate("transactions") },
                                        onNavigateToAccounts = { navController.navigate("accounts") },
                                        onNavigateToBudgets = { navController.navigate("budgets") },
                                        onNavigateToGoals = { navController.navigate("goals") },
                                        onNavigateToPortfolio = { navController.navigate("portfolio") },
                                        onNavigateToRecurring = { navController.navigate("recurring") },
                                        onNavigateToForecast = { navController.navigate("forecast") },
                                        onNavigateToReports = { navController.navigate("reports") },
                                        onNavigateToDraftsInbox = { navController.navigate("drafts_inbox") },
                                        onNavigateToNotifSettings = { navController.navigate("notif_settings") },
                                        onNavigateToCsvImport = { navController.navigate("csv_import") },
                                        onNavigateToOcrReceipt = { navController.navigate("ocr_receipt") }
                                    )
                                }
                                composable("drafts_inbox") {
                                    DraftsInboxScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("notif_settings") {
                                    NotifSettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("csv_import") {
                                    CsvImportScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("ocr_receipt") {
                                    OcrReceiptScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("transactions") {
                                    TransactionsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToEditTx = { txId -> navController.navigate("transaction_entry?transactionId=$txId") }
                                    )
                                }
                                composable("accounts") {
                                    AccountsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToCreatePin = { navController.navigate("create_pin") },
                                        onNavigateToCloudSync = { navController.navigate("cloud_sync") }
                                    )
                                }
                                composable("cloud_sync") {
                                    CloudSyncScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("transaction_entry") {
                                    TransactionEntryScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }, onNavigateToAccounts = { navController.navigate("accounts") }
                                    )
                                }
                                composable("transaction_entry?transactionId={transactionId}") { backstack ->
                                    val txId = backstack.arguments?.getString("transactionId")
                                    TransactionEntryScreen(
                                        viewModel = viewModel,
                                        editingTransactionId = txId,
                                        onNavigateBack = { navController.popBackStack() }, onNavigateToAccounts = { navController.navigate("accounts") }
                                    )
                                }
                                composable("transaction_entry?voiceActive={voiceActive}") { backstack ->
                                    val voiceActive = backstack.arguments?.getString("voiceActive") == "true"
                                    TransactionEntryScreen(
                                        viewModel = viewModel,
                                        voiceActive = voiceActive,
                                        onNavigateBack = { navController.popBackStack() }, onNavigateToAccounts = { navController.navigate("accounts") }
                                    )
                                }
                                composable("create_pin") {
                                    PinLockScreen(
                                        viewModel = viewModel,
                                        isConfigurationMode = true,
                                        onSuccess = { navController.popBackStack() }
                                    )
                                }
                                composable("budgets") {
                                    BudgetsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("goals") {
                                    GoalsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("portfolio") {
                                    PortfolioScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("recurring") {
                                    RecurringScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("forecast") {
                                    ForecastScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("reports") {
                                    ReportsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Lock the applications database access instantly when paused to background to preserve financial privacy
        try {
            val viewModel = ViewModelProvider(this, MainViewModel.Factory(application))[MainViewModel::class.java]
            viewModel.lockApp()
        } catch (e: Exception) {
            // Prevent lifecycle exceptions during system teardowns
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun setupAppShortcuts() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            try {
                val shortcutManager = getSystemService(android.content.pm.ShortcutManager::class.java)
                
                val recordShortcut = android.content.pm.ShortcutInfo.Builder(this, "shortcut_record")
                    .setShortLabel("Catat Cepat")
                    .setLongLabel("Catat Transaksi Baru")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_input_add))
                    .setIntent(android.content.Intent(this, MainActivity::class.java).apply {
                        action = "ACTION_QUICK_RECORD"
                    })
                    .build()

                val reportsShortcut = android.content.pm.ShortcutInfo.Builder(this, "shortcut_reports")
                    .setShortLabel("Laporan")
                    .setLongLabel("Lihat Laporan Keuangan")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_compass))
                    .setIntent(android.content.Intent(this, MainActivity::class.java).apply {
                        action = "android.intent.action.VIEW"
                        putExtra("ROUTE", "reports")
                    })
                    .build()

                val portfolioShortcut = android.content.pm.ShortcutInfo.Builder(this, "shortcut_portfolio")
                    .setShortLabel("Portofolio")
                    .setLongLabel("Lihat Portofolio")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_gallery))
                    .setIntent(android.content.Intent(this, MainActivity::class.java).apply {
                        action = "android.intent.action.VIEW"
                        putExtra("ROUTE", "portfolio")
                    })
                    .build()

                shortcutManager.dynamicShortcuts = listOf(recordShortcut, reportsShortcut, portfolioShortcut)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
