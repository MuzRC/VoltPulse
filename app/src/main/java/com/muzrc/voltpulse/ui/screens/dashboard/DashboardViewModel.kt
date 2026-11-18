package com.muzrc.voltpulse.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muzrc.voltpulse.core.sensor.BatteryEstimates
import com.muzrc.voltpulse.core.sensor.BatteryTelemetrySnapshot
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import com.muzrc.voltpulse.data.repository.BatteryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application)

    val telemetry: StateFlow<BatteryTelemetrySnapshot> = repository
        .observeLiveTelemetry(1500L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = repository.telemetryManager.readCurrentSnapshot()
        )

    val estimates: StateFlow<BatteryEstimates> = repository
        .observeLiveEstimates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = BatteryEstimates(
                actualCapacityMah = 5000f,
                designCapacityMah = 5000f,
                healthPercentage = 100f,
                remainingCapacityMah = 2500f,
                remainingSotHours = 5f,
                remainingStandbyHours = 48f,
                remainingCombinedHours = 18f,
                internalResistanceMohm = null
            )
        )

    val recentLogs: StateFlow<List<BatteryLogEntity>> = repository
        .observeRecentLogs(24)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )
}
