package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.AppContainerImpl

class DompetKuApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)

        try {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.data.worker.RecurringTransactionWorker>(
                1, java.util.concurrent.TimeUnit.HOURS
            ).build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "recurring_transactions_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            val syncConstraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val syncWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.data.worker.CloudSyncWorker>(
                3, java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(syncConstraints)
                .build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "cloud_sync_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                syncWorkRequest
            )
        } catch (e: Exception) {
            // Abaikan error inisialisasi WorkManager pada unit test
        }
    }
}
