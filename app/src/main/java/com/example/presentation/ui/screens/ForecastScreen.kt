package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.utils.LocalizationUtils
import com.example.presentation.viewmodel.MainViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val languageActive by viewModel.languageState.collectAsState()
    val currencyActive by viewModel.currencyState.collectAsState()
    val forecast by viewModel.forecastState.collectAsState()

    val cal = Calendar.getInstance()
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentDay = cal.get(Calendar.DAY_OF_MONTH)
    val remainingDays = (totalDays - currentDay + 1).coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (languageActive == "en") "Cash Flow Forecast" else "Proyeksi Arus Kas", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = if (languageActive == "en") "Back" else "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Projection Card
            item {
                val cardColor = if (forecast.isDeficitProjected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                val contentColor = if (forecast.isDeficitProjected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = if (languageActive == "en") "ESTIMATED MONTH-END BALANCE" else "ESTIMASI SALDO AKHIR BULAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = contentColor.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = LocalizationUtils.formatCurrency(forecast.projectedBalance, currencyActive, languageActive),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = contentColor
                            )
                        )

                        if (forecast.isDeficitProjected) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (languageActive == "en") "Warning: Projected deficit of ${LocalizationUtils.formatCurrency(forecast.deficitAmount, currencyActive, languageActive)} before next payday!" else "Peringatan: Diperkirakan defisit ${LocalizationUtils.formatCurrency(forecast.deficitAmount, currencyActive, languageActive)} sebelum gajian!",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }
            }

            // Calculation Breakdown Card
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (languageActive == "en") "Estimation Breakdown Details" else "Rincian Estimasi Perhitungan",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        ForecastRow(
                            label = if (languageActive == "en") "Current Liquid Balance (Cash/Bank)" else "Saldo Likuid Saat Ini (Kas/Bank)",
                            value = forecast.currentLiquidBalance,
                            currency = currencyActive,
                            language = languageActive
                        )
                        ForecastRow(
                            label = if (languageActive == "en") "Expected Recurring Income (+)" else "Estimasi Pemasukan Rutin (+)",
                            value = forecast.expectedIncome,
                            currency = currencyActive,
                            language = languageActive,
                            valueColor = Color(0xFF00C853)
                        )
                        ForecastRow(
                            label = if (languageActive == "en") "Expected Recurring Expenses (-)" else "Estimasi Pengeluaran Rutin (-)",
                            value = forecast.expectedExpense,
                            currency = currencyActive,
                            language = languageActive,
                            valueColor = MaterialTheme.colorScheme.error
                        )
                        ForecastRow(
                            label = if (languageActive == "en") "Average Daily Expenses" else "Rata-rata Pengeluaran Harian",
                            value = forecast.averageDailyExpense,
                            currency = currencyActive,
                            language = languageActive,
                            suffix = if (languageActive == "en") " / day" else " / hari"
                        )
                        ForecastRow(
                            label = if (languageActive == "en") "Total Expected Daily ($remainingDays days)" else "Total Estimasi Harian ($remainingDays hari)",
                            value = forecast.averageDailyExpense.multiply(BigDecimal(remainingDays)),
                            currency = currencyActive,
                            language = languageActive,
                            valueColor = MaterialTheme.colorScheme.error
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (languageActive == "en") "Projected Ending Balance" else "Hasil Proyeksi Saldo", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = LocalizationUtils.formatCurrency(forecast.projectedBalance, currencyActive, languageActive),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (forecast.isDeficitProjected) MaterialTheme.colorScheme.error else Color(0xFF00C853)
                                )
                            )
                        }
                    }
                }
            }

            // Recommendations
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (languageActive == "en") "Financial Security Tips" else "Saran Keamanan Finansial",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (languageActive == "en") {
                                if (forecast.isDeficitProjected) {
                                    "• Reduce non-essential daily spending to lower daily average.\n" +
                                    "• Postpone major purchases until next month.\n" +
                                    "• Ensure high-priority bills are paid first."
                                } else {
                                    "• Your cash flow is healthy. You can allocate surplus funds to savings goals.\n" +
                                    "• Maintain your current daily spending average."
                                }
                            } else {
                                if (forecast.isDeficitProjected) {
                                    "• Kurangi pengeluaran harian non-primer untuk menekan rata-rata harian.\n" +
                                    "• Tunda pembelian besar sampai bulan berikutnya.\n" +
                                    "• Pastikan tagihan dengan prioritas tinggi dibayar terlebih dahulu."
                                } else {
                                    "• Kondisi cash flow Anda sehat. Anda bisa mengalokasikan kelebihan dana ke tujuan tabungan.\n" +
                                    "• Pertahankan rata-rata pengeluaran harian saat ini."
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ForecastRow(
    label: String,
    value: BigDecimal,
    currency: String,
    language: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    suffix: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = LocalizationUtils.formatCurrency(value, currency, language) + suffix,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = valueColor)
        )
    }
}

private fun formatAmount(amount: BigDecimal): String {
    val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
    return format.format(amount)
}
