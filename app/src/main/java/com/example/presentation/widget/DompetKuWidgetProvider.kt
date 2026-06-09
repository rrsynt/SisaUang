package com.example.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.DompetKuApplication
import com.example.MainActivity
import com.example.R
import com.example.domain.utils.FinanceCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class DompetKuWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as DompetKuApplication
        val container = app.container

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accounts = container.accountRepository.getAccounts()
                val holdings = container.assetHoldingRepository.getAssetHoldings()
                val netWorth = FinanceCalculator.calculateNetWorth(accounts, holdings)
                val formattedNetWorth = com.example.domain.utils.LocalizationUtils.formatCurrency(
                    netWorth,
                    com.example.domain.utils.LocalizationUtils.activeCurrency,
                    com.example.domain.utils.LocalizationUtils.activeLanguage
                )

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.widget_net_worth, formattedNetWorth)

                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = "ACTION_QUICK_RECORD"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        101,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_add, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, DompetKuWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        onUpdate(context, appWidgetManager, ids)
    }
}
