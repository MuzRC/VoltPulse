package com.muzrc.voltpulse.ui.screens.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muzrc.voltpulse.core.usage.AppUsageTracker
import com.muzrc.voltpulse.data.local.entity.AppUsageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppUsageViewModel(application: Application) : AndroidViewModel(application) {

    private val _hasPermission = MutableStateFlow(AppUsageTracker.hasUsageStatsPermission(application))
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _appList = MutableStateFlow<List<AppUsageEntity>>(emptyList())
    val appList: StateFlow<List<AppUsageEntity>> = _appList.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        val ctx = getApplication<Application>()
        val granted = AppUsageTracker.hasUsageStatsPermission(ctx)
        _hasPermission.value = granted

        if (granted) {
            viewModelScope.launch(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val since24h = now - 86_400_000L
                val usages = AppUsageTracker.collectSessionAppUsage(
                    context = ctx,
                    sessionId = 0L,
                    startTime = since24h,
                    endTime = now,
                    totalSessionMah = 2500f
                )
                _appList.value = usages
            }
        }
    }
}
