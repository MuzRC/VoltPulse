package com.muzrc.voltpulse.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muzrc.voltpulse.data.local.entity.ChargeSessionEntity
import com.muzrc.voltpulse.data.repository.BatteryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application)

    val sessions: StateFlow<List<ChargeSessionEntity>> = repository
        .observeRecentSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )
}
