package com.muzrc.voltpulse

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.muzrc.voltpulse.service.BatteryMonitorService
import com.muzrc.voltpulse.service.LogCleanupWorker
import java.util.concurrent.TimeUnit

class VoltPulseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleLogCleanup()
        startMonitorServiceSafely()
    }

    private fun scheduleLogCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<LogCleanupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "voltpulse_log_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    fun startMonitorServiceSafely() {
        try {
            val intent = Intent(this, BatteryMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
        }
    }
}
