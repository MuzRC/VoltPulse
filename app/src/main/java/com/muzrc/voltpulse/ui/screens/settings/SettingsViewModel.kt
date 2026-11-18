package com.muzrc.voltpulse.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muzrc.voltpulse.core.sensor.BatteryHealthCalculator
import com.muzrc.voltpulse.core.shizuku.ShizukuBridge
import com.muzrc.voltpulse.core.shizuku.ShizukuStatus
import com.muzrc.voltpulse.core.updater.AppUpdateInfo
import com.muzrc.voltpulse.core.updater.GitHubUpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _designCapacity = MutableStateFlow(
        BatteryHealthCalculator.getDesignCapacityMah(application).toInt().toString()
    )
    val designCapacity: StateFlow<String> = _designCapacity.asStateFlow()

    private val _shizukuStatus = MutableStateFlow(ShizukuBridge.getStatus(application))
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val _kernelHardwareSummary = MutableStateFlow<String?>(null)
    val kernelHardwareSummary: StateFlow<String?> = _kernelHardwareSummary.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    init {
        refreshShizukuStatus()
    }

    fun refreshShizukuStatus() {
        val ctx = getApplication<Application>()
        val status = ShizukuBridge.getStatus(ctx)
        _shizukuStatus.value = status

        if (status == ShizukuStatus.READY) {
            viewModelScope.launch {
                val hw = ShizukuBridge.readKernelBatteryHardware(ctx)
                val parts = mutableListOf<String>()
                hw.chargeFullMah?.let { parts.add("Kapasiti Penuh CIP: ${it.toInt()} mAh") }
                hw.chargeFullDesignMah?.let { parts.add("Reka Bentuk Kilang: ${it.toInt()} mAh") }
                hw.cycleCount?.let { parts.add("Kitaran Cas: $it") }
                _kernelHardwareSummary.value = if (parts.isNotEmpty()) {
                    parts.joinToString(" • ")
                } else {
                    "Shizuku aktif (Enjin dumpsys batterystats sedia)"
                }
            }
        } else {
            _kernelHardwareSummary.value = null
        }
    }

    fun requestShizukuPermission() {
        ShizukuBridge.requestPermission()
    }

    fun updateDesignCapacityInput(value: String) {
        _designCapacity.value = value.filter { it.isDigit() }
    }

    fun saveDesignCapacity() {
        val parsed = _designCapacity.value.toFloatOrNull()
        if (parsed != null && parsed in 500f..20000f) {
            BatteryHealthCalculator.setManualDesignCapacityMah(getApplication(), parsed)
            _updateStatusMessage.value = "Kapasiti asal bateri disimpan: ${parsed.toInt()} mAh"
        } else {
            _updateStatusMessage.value = "Masukkan nilai kapasiti sah (500 - 20000 mAh)."
        }
    }

    fun checkForGitHubUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateStatusMessage.value = null
            val result = GitHubUpdateManager.checkForUpdate()
            _isCheckingUpdate.value = false

            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _availableUpdate.value = info
                } else {
                    _updateStatusMessage.value = "Anda sudah menggunakan versi terkini (v${info.latestVersion})."
                }
            }.onFailure { err ->
                _updateStatusMessage.value = "Gagal menyemak kemas kini: ${err.message}"
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }
}
