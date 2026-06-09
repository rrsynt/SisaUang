package com.example.domain.utils

import android.content.Context
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalizationUtils {
    var activeCurrency: String = "IDR"
    var activeLanguage: String = "id"
    var usdToIdrRate: BigDecimal = BigDecimal("16000")

    /**
     * Updates configuration locales on the fly, returning localized Context
     */
    fun updateLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun formatCurrency(amount: BigDecimal, currencyCode: String, languageCode: String): String {
        val isIndo = languageCode.lowercase() == "id"
        val prefix = if (currencyCode.uppercase() == "USD") "$ " else "Rp "
        
        val displayAmount = if (currencyCode.uppercase() == "USD") {
            amount.divide(usdToIdrRate, 2, java.math.RoundingMode.HALF_UP)
        } else {
            amount
        }
        val absoluteAmount = displayAmount.abs()

        val symbols = DecimalFormatSymbols(Locale(languageCode)).apply {
            if (isIndo) {
                groupingSeparator = '.'
                decimalSeparator = ','
            } else {
                groupingSeparator = ','
                decimalSeparator = '.'
            }
        }

        val pattern = if (currencyCode.uppercase() == "USD") "#,##0.00" else "#,##0"
        val df = DecimalFormat(pattern, symbols)
        val formatted = df.format(absoluteAmount)
        val sign = if (amount < BigDecimal.ZERO) "-" else ""
        return "$sign$prefix$formatted"
    }

    fun formatRupiah(amount: BigDecimal, languageCode: String): String {
        return formatCurrency(amount, activeCurrency, languageCode)
    }

    /**
     * Formats localized date string (e.g. "31 Mei 2026" or "31 May 2026")
     */
    fun formatDateByLocale(epochMillis: Long, languageCode: String): String {
        val locale = Locale(languageCode)
        val sdf = SimpleDateFormat("dd MMM yyyy", locale)
        return sdf.format(Date(epochMillis))
    }

    /**
     * Formats localized date-time string
     */
    fun formatDateTimeByLocale(epochMillis: Long, languageCode: String): String {
        val locale = Locale(languageCode)
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
        return sdf.format(Date(epochMillis))
    }
}
