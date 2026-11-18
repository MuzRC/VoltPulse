package com.muzrc.voltpulse.ui.screens.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muzrc.voltpulse.core.sensor.BatteryHealthCalculator
import com.muzrc.voltpulse.core.sensor.BatteryTelemetryManager
import com.muzrc.voltpulse.core.shizuku.ShizukuBridge
import com.muzrc.voltpulse.core.usage.AppBatteryInsight
import com.muzrc.voltpulse.core.usage.AppUsageTracker
import com.muzrc.voltpulse.data.local.VoltPulseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class AppUsageUiState(
    val hasPermission: Boolean = false,
    val isShizukuActive: Boolean = false,
    val isLoading: Boolean = true,
    val totalDrainMah: Float = 0f,
    val totalDrainPercentOfBattery: Float = 0f,
    val screenDrainMah: Float = 0f,
    val backgroundDrainMah: Float = 0f,
    val appInsights: List<AppBatteryInsight> = emptyList(),
    val selectedApp: AppBatteryInsight? = null,
    val actionFeedbackMessage: String? = null,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

class AppUsageViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = VoltPulseDatabase.getInstance(application).batteryDao()
    private val telemetryManager = BatteryTelemetryManager(application)

    private val _uiState = MutableStateFlow(
        AppUsageUiState(
            hasPermission = AppUsageTracker.hasUsageStatsPermission(application),
            isShizukuActive = ShizukuBridge.isReady(application)
        )
    )
    val uiState: StateFlow<AppUsageUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun selectApp(insight: AppBatteryInsight?) {
        _uiState.update { it.copy(selectedApp = insight, actionFeedbackMessage = null) }
    }

    fun restrictAppWithShizuku(packageName: String, appLabel: String) {
        viewModelScope.launch {
            val ok = ShizukuBridge.restrictBackgroundViaShizuku(getApplication(), packageName)
            _uiState.update {
                it.copy(
                    actionFeedbackMessage = if (ok) {
                        "Berjaya menyekat proses latar belakang untuk $appLabel."
                    } else {
                        "Gagal melaksanakan arahan Shizuku. Pastikan servis Shizuku aktif."
                    }
                )
            }
        }
    }

    fun forceStopAppWithShizuku(packageName: String, appLabel: String) {
        viewModelScope.launch {
            val ok = ShizukuBridge.forceStopViaShizuku(getApplication(), packageName)
            _uiState.update {
                it.copy(
                    actionFeedbackMessage = if (ok) {
                        "$appLabel telah dihentikan paksa (Force Stopped)."
                    } else {
                        "Gagal menghentikan $appLabel melalui Shizuku."
                    }
                )
            }
        }
    }

    fun refreshData() {
        val ctx = getApplication<Application>()
        val granted = AppUsageTracker.hasUsageStatsPermission(ctx)
        val shizukuReady = ShizukuBridge.isReady(ctx)

        if (!granted) {
            _uiState.update {
                it.copy(
                    hasPermission = false,
                    isShizukuActive = shizukuReady,
                    isLoading = false,
                    appInsights = emptyList()
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(hasPermission = true, isShizukuActive = shizukuReady, isLoading = true) }

            val now = System.currentTimeMillis()
            val since24h = now - 86_400_000L
            val logs24h = dao.getLogsSinceSync(since24h)
            val healthHistory = dao.getRecentHealthRecordsSync()
            val snapshot = telemetryManager.readCurrentSnapshot()
            val kernelInfo = ShizukuBridge.readKernelBatteryHardware(ctx)

            val designCap = BatteryHealthCalculator.getDesignCapacityMah(ctx)
            val hwCap = BatteryHealthCalculator.calculateHardwareCounterCapacity(
                snapshot.chargeCounterUah,
                snapshot.level
            )
            val actualCap = kernelInfo.chargeFullMah
                ?: BatteryHealthCalculator.calculateMedianCapacity(healthHistory, hwCap, designCap)

            var integratedDischargeMah = 0.0
            var levelDropDischargeMah = 0.0

            for (i in 1 until logs24h.size) {
                val prev = logs24h[i - 1]
                val curr = logs24h[i]
                if (!prev.isCharging && !curr.isCharging) {
                    val deltaHours = (curr.timestamp - prev.timestamp) / 3_600_000.0
                    if (deltaHours in 0.0001..0.5) {
                        val avgMa = (abs(prev.currentMa) + abs(curr.currentMa)) / 2.0
                        integratedDischargeMah += avgMa * deltaHours
                    }
                    val levelDrop = (prev.level - curr.level).coerceAtLeast(0)
                    if (levelDrop > 0) {
                        levelDropDischargeMah += (levelDrop / 100.0) * actualCap
                    }
                }
            }

            val realTotalDrainMah = maxOf(
                integratedDischargeMah.toFloat(),
                levelDropDischargeMah.toFloat(),
                150f
            )

            val insights = AppUsageTracker.analyzeDetailedAppUsage24h(
                context = ctx,
                logs24h = logs24h,
                totalActualDrainMah = realTotalDrainMah
            )

            val sumAppsMah = insights.sumOf { it.totalEstimatedMah.toDouble() }.toFloat()
            val displayTotalMah = if (sumAppsMah > 1f) sumAppsMah else realTotalDrainMah

            var screenWeightedSum = 0.0
            var bgWeightedSum = 0.0
            for (app in insights) {
                val fgW = app.foregroundTimeMs * 1.0
                val bgW = app.backgroundTimeMs * 0.35
                val totW = (fgW + bgW).coerceAtLeast(1.0)
                screenWeightedSum += app.totalEstimatedMah * (fgW / totW)
                bgWeightedSum += app.totalEstimatedMah * (bgW / totW)
            }

            val drainPercent = ((displayTotalMah / actualCap) * 100f).coerceIn(0f, 300f)

            _uiState.update { current ->
                val updatedSelected = current.selectedApp?.let { sel ->
                    insights.find { it.packageName == sel.packageName }
                }
                current.copy(
                    hasPermission = true,
                    isShizukuActive = shizukuReady,
                    isLoading = false,
                    totalDrainMah = displayTotalMah,
                    totalDrainPercentOfBattery = drainPercent,
                    screenDrainMah = screenWeightedSum.toFloat(),
                    backgroundDrainMah = bgWeightedSum.toFloat(),
                    appInsights = insights,
                    selectedApp = updatedSelected,
                    lastUpdatedTimestamp = now
                )
            }
        }
    }
}
