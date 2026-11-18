package com.muzrc.voltpulse.core.sensor

import android.annotation.SuppressLint
import android.content.Context
import com.muzrc.voltpulse.data.local.entity.BatteryHealthEntity
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import kotlin.math.abs

data class BatteryEstimates(
    val actualCapacityMah: Float,
    val designCapacityMah: Float,
    val healthPercentage: Float,
    val remainingCapacityMah: Float,
    val remainingSotHours: Float,
    val remainingStandbyHours: Float,
    val remainingCombinedHours: Float,
    val internalResistanceMohm: Float?
)

object BatteryHealthCalculator {

    private const val PREFS_NAME = "voltpulse_prefs"
    private const val KEY_DESIGN_CAPACITY = "design_capacity_mah"

    fun getDesignCapacityMah(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getFloat(KEY_DESIGN_CAPACITY, -1f)
        if (saved > 500f) return saved

        val systemCapacity = readSystemDesignCapacity(context)
        if (systemCapacity > 500f) {
            prefs.edit().putFloat(KEY_DESIGN_CAPACITY, systemCapacity).apply()
            return systemCapacity
        }
        return 5000f // Nilai lalai standard jika pengeluar menyekat PowerProfile
    }

    fun setManualDesignCapacityMah(context: Context, capacityMah: Float) {
        if (capacityMah in 500f..20000f) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_DESIGN_CAPACITY, capacityMah)
                .apply()
        }
    }

    @SuppressLint("PrivateApi")
    private fun readSystemDesignCapacity(context: Context): Float {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfile = constructor.newInstance(context)
            val getAveragePower = powerProfileClass.getMethod("getBatteryCapacity")
            val capacity = getAveragePower.invoke(powerProfile) as Double
            capacity.toFloat()
        } catch (_: Exception) {
            -1f
        }
    }

    /**
     * Formula 1A: Integrasi Arus (Coulomb Counting) daripada log pengecasan sah.
     */
    fun calculateCoulombCountedCapacity(
        chargeLogs: List<BatteryLogEntity>,
        startLevel: Int,
        endLevel: Int
    ): Float? {
        val deltaPercent = endLevel - startLevel
        if (deltaPercent < 15 || chargeLogs.size < 2) return null

        var totalMahTransferred = 0.0
        for (i in 1 until chargeLogs.size) {
            val prev = chargeLogs[i - 1]
            val curr = chargeLogs[i]
            val deltaSeconds = (curr.timestamp - prev.timestamp) / 1000.0
            if (deltaSeconds in 0.5..120.0 && curr.currentMa > 0f) {
                val avgCurrentMa = (abs(prev.currentMa) + abs(curr.currentMa)) / 2.0
                totalMahTransferred += avgCurrentMa * (deltaSeconds / 3600.0)
            }
        }

        if (totalMahTransferred <= 50.0) return null
        val estimatedFullMah = (totalMahTransferred / deltaPercent) * 100.0
        return if (estimatedFullMah in 500.0..20000.0) estimatedFullMah.toFloat() else null
    }

    /**
     * Formula 1B: Bacaan Perkakasan Terus daripada CHARGE_COUNTER (uAh).
     */
    fun calculateHardwareCounterCapacity(chargeCounterUah: Long, currentLevel: Int): Float? {
        if (chargeCounterUah <= 0L || currentLevel !in 5..100) return null
        val remainingMah = chargeCounterUah / 1000f
        val fullMah = (remainingMah / currentLevel) * 100f
        return if (fullMah in 500f..20000f) fullMah else null
    }

    /**
     * Formula 2: Penstabilan Median Kesihatan Bateri daripada rekod sesi terdahulu.
     */
    fun calculateMedianCapacity(
        healthHistory: List<BatteryHealthEntity>,
        fallbackHardwareCapacity: Float?,
        designCapacity: Float
    ): Float {
        val validCapacities = healthHistory
            .map { it.estimatedCapacityMah }
            .filter { it in 500f..20000f }
            .sorted()

        if (validCapacities.isNotEmpty()) {
            val mid = validCapacities.size / 2
            return if (validCapacities.size % 2 == 0) {
                (validCapacities[mid - 1] + validCapacities[mid]) / 2f
            } else {
                validCapacities[mid]
            }
        }

        return fallbackHardwareCapacity ?: designCapacity
    }

    /**
     * Formula 4: Rintangan Dalaman (mOhm) menggunakan Hukum Ohm semasa lonjakan beban.
     */
    fun estimateInternalResistanceMohm(recentLogs: List<BatteryLogEntity>): Float? {
        if (recentLogs.size < 2) return null
        for (i in recentLogs.size - 1 downTo 1) {
            val prev = recentLogs[i - 1]
            val curr = recentLogs[i]
            val deltaTimeMs = curr.timestamp - prev.timestamp
            if (deltaTimeMs in 200..3000 && !curr.isCharging && !prev.isCharging) {
                val deltaCurrentMa = abs(curr.currentMa - prev.currentMa)
                val deltaVoltageMv = abs(curr.voltageMv - prev.voltageMv)
                if (deltaCurrentMa >= 400f && deltaVoltageMv > 0) {
                    val resistanceMohm = (deltaVoltageMv / deltaCurrentMa) * 1000f
                    if (resistanceMohm in 10f..1000f) {
                        return resistanceMohm
                    }
                }
            }
        }
        return null
    }

    /**
     * Formula 3: Pengiraan Lengkap Baki SOT, Standby, dan Gabungan.
     */
    fun computeAllEstimates(
        snapshot: BatteryTelemetrySnapshot,
        designCapacityMah: Float,
        healthHistory: List<BatteryHealthEntity>,
        avgActiveMa: Float?,
        avgStandbyMa: Float?,
        recentLogs: List<BatteryLogEntity>
    ): BatteryEstimates {
        val hwCap = calculateHardwareCounterCapacity(snapshot.chargeCounterUah, snapshot.level)
        val actualCap = calculateMedianCapacity(healthHistory, hwCap, designCapacityMah)
        val healthPct = ((actualCap / designCapacityMah) * 100f).coerceIn(1f, 110f)

        val remainingMah = if (snapshot.chargeCounterUah > 0L) {
            snapshot.chargeCounterUah / 1000f
        } else {
            actualCap * (snapshot.level / 100f)
        }

        val activeDrainMa = (avgActiveMa?.takeIf { it > 50f }
            ?: if (!snapshot.isCharging && snapshot.isScreenOn && abs(snapshot.currentNowMa) > 50f) abs(snapshot.currentNowMa) else 450f)

        val standbyDrainMa = (avgStandbyMa?.takeIf { it > 5f } ?: 35f)

        val sotHours = remainingMah / activeDrainMa
        val standbyHours = remainingMah / standbyDrainMa

        // Nisbah corak penggunaan standard 25% aktif / 75% melahu
        val combinedDrainMa = (0.25f * activeDrainMa) + (0.75f * standbyDrainMa)
        val combinedHours = remainingMah / combinedDrainMa

        val resistance = estimateInternalResistanceMohm(recentLogs)
            ?: healthHistory.firstOrNull { it.internalResistanceMohm != null }?.internalResistanceMohm

        return BatteryEstimates(
            actualCapacityMah = actualCap,
            designCapacityMah = designCapacityMah,
            healthPercentage = healthPct,
            remainingCapacityMah = remainingMah,
            remainingSotHours = sotHours,
            remainingStandbyHours = standbyHours,
            remainingCombinedHours = combinedHours,
            internalResistanceMohm = resistance
        )
    }
}
