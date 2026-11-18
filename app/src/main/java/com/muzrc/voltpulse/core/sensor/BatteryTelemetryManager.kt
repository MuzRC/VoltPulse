package com.muzrc.voltpulse.core.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class BatteryTelemetrySnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Int = 0,
    val voltageMv: Int = 0,
    val temperatureC: Float = 0f,
    val currentNowMa: Float = 0f,
    val currentAverageMa: Float = 0f,
    val powerWatts: Float = 0f,
    val chargeCounterUah: Long = -1L,
    val cycleCount: Int = -1,
    val isCharging: Boolean = false,
    val chargeSource: String = "Bateri",
    val healthStatus: String = "Baik",
    val technology: String = "Li-ion",
    val isScreenOn: Boolean = true
)

class BatteryTelemetryManager(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun readCurrentSnapshot(): BatteryTelemetrySnapshot {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return parseIntent(intent)
    }

    fun observeTelemetry(intervalMs: Long = 1500L): Flow<BatteryTelemetrySnapshot> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    trySend(parseIntent(intent))
                }
            }
        }

        val initialIntent = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        trySend(parseIntent(initialIntent))

        val pollingJob = launch {
            while (isActive) {
                delay(intervalMs)
                val latestIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                trySend(parseIntent(latestIntent))
            }
        }

        awaitClose {
            pollingJob.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    private fun parseIntent(intent: Intent?): BatteryTelemetrySnapshot {
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (rawLevel >= 0 && scale > 0) ((rawLevel * 100f) / scale).toInt() else 0

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureC = tempTenths / 10f

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusInt == BatteryManager.BATTERY_STATUS_FULL

        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargeSource = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Pengecas AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wayarles"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dok"
            else -> "Bateri"
        }

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthStatus = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Baik"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Terlalu Panas"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Rosak"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Voltan Berlebihan"
            BatteryManager.BATTERY_HEALTH_COLD -> "Terlalu Sejuk"
            else -> "Normal"
        }

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { "Li-ion" } ?: "Li-ion"

        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        } else {
            -1
        }

        // Bacaan arus elektrik & penormalan tanda (+/-) mengikut pengeluar cip
        val rawCurrentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawCurrentAvg = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)

        val normalizedNowMa = normalizeCurrentToMa(rawCurrentNow, isCharging)
        val normalizedAvgMa = normalizeCurrentToMa(rawCurrentAvg, isCharging)

        val chargeCounterUah = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        val powerWatts = (voltageMv / 1000f) * (abs(normalizedNowMa) / 1000f)

        return BatteryTelemetrySnapshot(
            timestamp = System.currentTimeMillis(),
            level = level,
            voltageMv = voltageMv,
            temperatureC = temperatureC,
            currentNowMa = normalizedNowMa,
            currentAverageMa = normalizedAvgMa,
            powerWatts = powerWatts,
            chargeCounterUah = chargeCounterUah,
            cycleCount = cycleCount,
            isCharging = isCharging,
            chargeSource = chargeSource,
            healthStatus = healthStatus,
            technology = technology,
            isScreenOn = powerManager.isInteractive
        )
    }

    /**
     * Menukar nilai microampere (uA) atau milliampere (mA) dan menormalkan tanda:
     * Positif (+) semasa mengecas, Negatif (-) semasa menyahcas.
     */
    private fun normalizeCurrentToMa(rawValue: Int, isCharging: Boolean): Float {
        if (rawValue == Int.MIN_VALUE || rawValue == 0) return 0f
        val magnitude = abs(rawValue).toFloat()
        // Kebanyakan peranti Android memulangkan nilai dalam uA (> 10,000), sebahagian kecil dalam mA
        val ma = if (magnitude > 10000f) magnitude / 1000f else magnitude
        return if (isCharging) ma else -ma
    }
}
