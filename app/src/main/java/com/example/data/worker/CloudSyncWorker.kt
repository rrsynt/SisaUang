package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.DompetKuApplication
import com.example.domain.model.SyncMeta

class CloudSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as DompetKuApplication
        val container = app.container

        val syncMetaRepo = container.syncMetaRepository
        val cloudSyncManager = container.cloudSyncManager

        val meta = syncMetaRepo.getSyncMeta()
        val spreadsheetId = meta?.googleSpreadsheetId

        if (spreadsheetId.isNullOrBlank()) {
            return Result.failure()
        }

        try {
            syncMetaRepo.saveSyncMeta(
                SyncMeta(
                    googleSpreadsheetId = spreadsheetId,
                    schemaVersion = meta.schemaVersion,
                    lastSyncAt = meta.lastSyncAt,
                    status = "RUNNING"
                )
            )

            // 1. Jalankan migrasi/bootstrap kolom dan tab otomatis
            cloudSyncManager.bootstrapOrMigrate(spreadsheetId)

            // 2. Sinkronisasi dua arah
            cloudSyncManager.syncAll(spreadsheetId)

            syncMetaRepo.saveSyncMeta(
                SyncMeta(
                    googleSpreadsheetId = spreadsheetId,
                    schemaVersion = 4,
                    lastSyncAt = System.currentTimeMillis(),
                    status = "SUCCESS"
                )
            )
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            syncMetaRepo.saveSyncMeta(
                SyncMeta(
                    googleSpreadsheetId = spreadsheetId,
                    schemaVersion = meta.schemaVersion,
                    lastSyncAt = meta.lastSyncAt,
                    status = "FAILED"
                )
            )
            return Result.retry()
        }
    }
}
