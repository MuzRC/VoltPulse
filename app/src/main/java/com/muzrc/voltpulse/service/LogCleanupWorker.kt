package com.muzrc.voltpulse.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.muzrc.voltpulse.data.local.VoltPulseDatabase

class LogCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dao = VoltPulseDatabase.getInstance(applicationContext).batteryDao()
            val fourteenDaysAgoMs = System.currentTimeMillis() - (14L * 24L * 3600_000L)
            dao.deleteLogsOlderThan(fourteenDaysAgoMs)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
