package com.muzrc.voltpulse.data.repository

import android.content.Context
import com.muzrc.voltpulse.core.sensor.BatteryEstimates
import com.muzrc.voltpulse.core.sensor.BatteryHealthCalculator
import com.muzrc.voltpulse.core.sensor.BatteryTelemetryManager
import com.muzrc.voltpulse.core.sensor.BatteryTelemetrySnapshot
import com.muzrc.voltpulse.core.shizuku.KernelBatteryHardwareInfo
import com.muzrc.voltpulse.core.shizuku.ShizukuBridge
import com.muzrc.voltpulse.data.local.VoltPulseDatabase
import com.muzrc.voltpulse.data.local.entity.BatteryHealthEntity
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import com.muzrc.voltpulse.data.local.entity.ChargeSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class BatteryRepository(private val context: Context) {

    private val database = VoltPulseDatabase.getInstance(context)
    val dao = database.batteryDao()
    val telemetryManager = BatteryTelemetryManager(context)

    fun observeLiveTelemetry(intervalMs: Long = 1500L): Flow<BatteryTelemetrySnapshot> {
        return telemetryManager.observeTelemetry(intervalMs)
    }

    fun observeRecentLogs(hoursBack: Int = 24): Flow<List<BatteryLogEntity>> {
        val since = System.currentTimeMillis() - (hoursBack * 3600_000L)
        return dao.getLogsSince(since)
    }

    fun observeRecentSessions(): Flow<List<ChargeSessionEntity>> {
        return dao.getRecentSessions(50)
    }

    fun observeHealthHistory(): Flow<List<BatteryHealthEntity>> {
        return dao.getRecentHealthRecords()
    }

    private fun observeKernelHardwareInfo(): Flow<KernelBatteryHardwareInfo> = flow {
        emit(ShizukuBridge.readKernelBatteryHardware(context))
    }

    fun observeLiveEstimates(): Flow<BatteryEstimates> {
        val since24h = System.currentTimeMillis() - 86_400_000L
        return combine(
            observeLiveTelemetry(2000L),
            dao.getRecentHealthRecords(),
            dao.getLogsSince(since24h),
            observeKernelHardwareInfo()
        ) { snapshot, healthHistory, logs, kernelInfo ->
            val designCap = BatteryHealthCalculator.getDesignCapacityMah(context)
            val activeLogs = logs.filter { !it.isCharging && it.isScreenOn }
            val standbyLogs = logs.filter { !it.isCharging && !it.isScreenOn }

            val avgActiveMa = if (activeLogs.isNotEmpty()) {
                activeLogs.map { kotlin.math.abs(it.currentMa) }.average().toFloat()
            } else null

            val avgStandbyMa = if (standbyLogs.isNotEmpty()) {
                standbyLogs.map { kotlin.math.abs(it.currentMa) }.average().toFloat()
            } else null

            BatteryHealthCalculator.computeAllEstimates(
                snapshot = snapshot,
                designCapacityMah = designCap,
                healthHistory = healthHistory,
                avgActiveMa = avgActiveMa,
                avgStandbyMa = avgStandbyMa,
                recentLogs = logs.takeLast(20),
                kernelInfo = kernelInfo
            )
        }
    }

    suspend fun saveLogBatch(logs: List<BatteryLogEntity>) {
        if (logs.isNotEmpty()) {
            dao.insertLogsBatch(logs)
        }
    }
}
