package com.muzrc.voltpulse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.muzrc.voltpulse.MainActivity
import com.muzrc.voltpulse.core.sensor.BatteryHealthCalculator
import com.muzrc.voltpulse.core.sensor.BatteryTelemetryManager
import com.muzrc.voltpulse.core.sensor.BatteryTelemetrySnapshot
import com.muzrc.voltpulse.core.usage.AppUsageTracker
import com.muzrc.voltpulse.data.local.VoltPulseDatabase
import com.muzrc.voltpulse.data.local.entity.BatteryHealthEntity
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import com.muzrc.voltpulse.data.local.entity.ChargeSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "voltpulse_telemetry_channel"
        const val NOTIFICATION_ID = 1001
        private const val BUFFER_FLUSH_SIZE = 15
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var telemetryManager: BatteryTelemetryManager
    private lateinit var powerManager: PowerManager
    private val dao by lazy { VoltPulseDatabase.getInstance(this).batteryDao() }

    private val logBuffer = mutableListOf<BatteryLogEntity>()
    private var activeSession: ChargeSessionEntity? = null
    private var lastSampleTimestamp: Long = 0L
    private var lastRecordedLevel: Int = -1
    private var samplingJob: Job? = null

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF -> {
                    serviceScope.launch {
                        processTelemetryTick(forceRecord = true)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        telemetryManager = BatteryTelemetryManager(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(telemetryManager.readCurrentSnapshot()))

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(systemEventReceiver, filter)

        startAdaptiveSamplingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(systemEventReceiver)
        } catch (_: IllegalArgumentException) {
        }
        samplingJob?.cancel()
        serviceScope.launch {
            flushBufferToDatabase()
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private fun startAdaptiveSamplingLoop() {
        samplingJob = serviceScope.launch {
            activeSession = dao.getLatestSession()
            while (isActive) {
                processTelemetryTick(forceRecord = false)
                val snapshot = telemetryManager.readCurrentSnapshot()
                val nextDelay = if (snapshot.isScreenOn || snapshot.isCharging) 6_000L else 60_000L
                delay(nextDelay)
            }
        }
    }

    private suspend fun processTelemetryTick(forceRecord: Boolean) {
        val snapshot = telemetryManager.readCurrentSnapshot()
        val now = snapshot.timestamp

        updateForegroundNotification(snapshot)
        handleSessionTransition(snapshot)

        val elapsedSinceLast = now - lastSampleTimestamp
        val levelChanged = snapshot.level != lastRecordedLevel
        val minInterval = if (snapshot.isScreenOn || snapshot.isCharging) 5_000L else 300_000L

        if (forceRecord || levelChanged || elapsedSinceLast >= minInterval) {
            lastSampleTimestamp = now
            lastRecordedLevel = snapshot.level

            synchronized(logBuffer) {
                logBuffer.add(
                    BatteryLogEntity(
                        timestamp = now,
                        level = snapshot.level,
                        currentMa = snapshot.currentNowMa,
                        voltageMv = snapshot.voltageMv,
                        temperatureC = snapshot.temperatureC,
                        isCharging = snapshot.isCharging,
                        isScreenOn = snapshot.isScreenOn
                    )
                )
            }

            if (logBuffer.size >= BUFFER_FLUSH_SIZE) {
                flushBufferToDatabase()
            }
        }
    }

    private suspend fun handleSessionTransition(snapshot: BatteryTelemetrySnapshot) {
        val currentType = if (snapshot.isCharging) "CHARGE" else "DISCHARGE"
        val now = snapshot.timestamp
        val current = activeSession

        if (current == null) {
            val newSession = ChargeSessionEntity(
                sessionType = currentType,
                startTime = now,
                endTime = now,
                startLevel = snapshot.level,
                endLevel = snapshot.level,
                mahTransferred = 0f,
                maxTemperatureC = snapshot.temperatureC,
                screenOnDurationMs = 0L
            )
            val id = dao.insertSession(newSession)
            activeSession = newSession.copy(sessionId = id)
            return
        }

        if (current.sessionType != currentType) {
            flushBufferToDatabase()
            finalizeCompletedSession(current, snapshot)

            val nextSession = ChargeSessionEntity(
                sessionType = currentType,
                startTime = now,
                endTime = now,
                startLevel = snapshot.level,
                endLevel = snapshot.level,
                mahTransferred = 0f,
                maxTemperatureC = snapshot.temperatureC,
                screenOnDurationMs = 0L
            )
            val newId = dao.insertSession(nextSession)
            activeSession = nextSession.copy(sessionId = newId)
        } else {
            val deltaTimeMs = (now - current.endTime).coerceAtLeast(0L)
            val addedSot = if (snapshot.isScreenOn && !snapshot.isCharging) deltaTimeMs else 0L
            val addedMah = abs(snapshot.currentNowMa) * (deltaTimeMs / 3_600_000f)

            val updated = current.copy(
                endTime = now,
                endLevel = snapshot.level,
                mahTransferred = current.mahTransferred + addedMah,
                maxTemperatureC = maxOf(current.maxTemperatureC, snapshot.temperatureC),
                screenOnDurationMs = current.screenOnDurationMs + addedSot
            )
            activeSession = updated
            dao.updateSession(updated)
        }
    }

    private suspend fun finalizeCompletedSession(
        completed: ChargeSessionEntity,
        latestSnapshot: BatteryTelemetrySnapshot
    ) {
        val sessionLogs = dao.getLogsSinceSync(completed.startTime)

        if (completed.sessionType == "CHARGE") {
            val estimatedFullCap = BatteryHealthCalculator.calculateCoulombCountedCapacity(
                chargeLogs = sessionLogs,
                startLevel = completed.startLevel,
                endLevel = completed.endLevel
            )
            if (estimatedFullCap != null) {
                val designCap = BatteryHealthCalculator.getDesignCapacityMah(this)
                val resistance = BatteryHealthCalculator.estimateInternalResistanceMohm(sessionLogs)
                dao.insertHealthRecord(
                    BatteryHealthEntity(
                        dateTimestamp = latestSnapshot.timestamp,
                        estimatedCapacityMah = estimatedFullCap,
                        designCapacityMah = designCap,
                        internalResistanceMohm = resistance
                    )
                )
            }
        } else if (completed.sessionType == "DISCHARGE") {
            val appUsages = AppUsageTracker.collectSessionAppUsage(
                context = this,
                sessionId = completed.sessionId,
                startTime = completed.startTime,
                endTime = completed.endTime,
                totalSessionMah = completed.mahTransferred
            )
            if (appUsages.isNotEmpty()) {
                dao.insertAppUsageBatch(appUsages)
            }
        }
    }

    private suspend fun flushBufferToDatabase() {
        val toWrite: List<BatteryLogEntity>
        synchronized(logBuffer) {
            if (logBuffer.isEmpty()) return
            toWrite = logBuffer.toList()
            logBuffer.clear()
        }
        dao.insertLogsBatch(toWrite)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemantauan Bateri Masa Nyata",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Memaparkan arus elektrik, voltan, dan suhu bateri secara langsung."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateForegroundNotification(snapshot: BatteryTelemetrySnapshot) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: BatteryTelemetrySnapshot): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentSign = if (snapshot.currentNowMa > 0) "+" else ""
        val title = String.format(
            Locale.US,
            "%d%% • %s%.0f mA • %.1f W",
            snapshot.level,
            currentSign,
            snapshot.currentNowMa,
            snapshot.powerWatts
        )
        val content = String.format(
            Locale.US,
            "Suhu: %.1f°C | Voltan: %d mV | %s",
            snapshot.temperatureC,
            snapshot.voltageMv,
            snapshot.chargeSource
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
