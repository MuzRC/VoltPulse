package com.muzrc.voltpulse.core.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.muzrc.voltpulse.core.shizuku.ShizukuBridge
import com.muzrc.voltpulse.data.local.entity.AppUsageEntity
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

data class HourlyAppDrain(
    val slotIndex: Int,
    val hourOfDay: Int,
    val hourLabel: String,
    val foregroundMinutes: Float,
    val backgroundMinutes: Float,
    val estimatedMah: Float
)

data class AppBatteryInsight(
    val packageName: String,
    val appLabel: String,
    val totalEstimatedMah: Float,
    val percentageOfTotalDrain: Float,
    val foregroundTimeMs: Long,
    val backgroundTimeMs: Long,
    val hourlyBreakdown: List<HourlyAppDrain>,
    val peakHourRange: String,
    val peakHourMah: Float,
    val diagnosisTitle: String,
    val recommendations: List<String>,
    val isShizukuSystemMeasured: Boolean = false
)

object AppUsageTracker {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppInfoSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openUsageAccessSettings(context)
        }
    }

    fun collectSessionAppUsage(
        context: Context,
        sessionId: Long,
        startTime: Long,
        endTime: Long,
        totalSessionMah: Float
    ): List<AppUsageEntity> {
        if (!hasUsageStatsPermission(context) || endTime <= startTime) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        ) ?: return emptyList()

        val aggregatedMap = mutableMapOf<String, Pair<Long, Long>>()
        for (stat in stats) {
            if (stat.packageName == context.packageName) continue
            val fgTime = stat.totalTimeInForeground
            val bgTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stat.totalTimeForegroundServiceUsed
            } else {
                0L
            }
            if (fgTime > 1000L || bgTime > 1000L) {
                val existing = aggregatedMap[stat.packageName] ?: (0L to 0L)
                aggregatedMap[stat.packageName] = (existing.first + fgTime) to (existing.second + bgTime)
            }
        }

        if (aggregatedMap.isEmpty()) return emptyList()

        val weightedScores = aggregatedMap.mapValues { (_, times) ->
            times.first + (times.second * 0.35)
        }
        val totalScore = weightedScores.values.sum().coerceAtLeast(1.0)
        val effectiveMahPool = if (totalSessionMah > 1f) totalSessionMah else 100f

        return aggregatedMap.map { (pkg, times) ->
            val label = resolveAppLabel(pm, pkg)
            val share = (weightedScores[pkg] ?: 0.0) / totalScore
            val estMah = (effectiveMahPool * share).toFloat()

            AppUsageEntity(
                sessionId = sessionId,
                packageName = pkg,
                appLabel = label,
                foregroundTimeMs = times.first,
                backgroundTimeMs = times.second,
                estimatedMah = estMah
            )
        }.sortedByDescending { it.estimatedMah }.take(30)
    }

    suspend fun analyzeDetailedAppUsage24h(
        context: Context,
        logs24h: List<BatteryLogEntity>,
        totalActualDrainMah: Float
    ): List<AppBatteryInsight> {
        if (!hasUsageStatsPermission(context)) return emptyList()

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 86_400_000L
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val shizukuSystemMahMap = ShizukuBridge.readSystemBatteryStatsMah(context)
        val hasShizukuData = shizukuSystemMahMap.isNotEmpty()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        ) ?: return emptyList()

        val totalTimesMap = mutableMapOf<String, Pair<Long, Long>>()
        for (stat in stats) {
            if (stat.packageName == context.packageName) continue
            val fgTime = stat.totalTimeInForeground
            val bgTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stat.totalTimeForegroundServiceUsed
            } else {
                0L
            }
            if (fgTime > 1000L || bgTime > 1000L) {
                val existing = totalTimesMap[stat.packageName] ?: (0L to 0L)
                totalTimesMap[stat.packageName] = (existing.first + fgTime) to (existing.second + bgTime)
            }
        }

        for ((pkg, sysMah) in shizukuSystemMahMap) {
            if (sysMah >= 2f && !totalTimesMap.containsKey(pkg)) {
                totalTimesMap[pkg] = 0L to 60_000L
            }
        }

        if (totalTimesMap.isEmpty()) return emptyList()

        val hourlyFgMs = mutableMapOf<String, LongArray>()
        val hourlyBgMs = mutableMapOf<String, LongArray>()
        val activeFgStart = mutableMapOf<String, Long>()
        val activeBgStart = mutableMapOf<String, Long>()

        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (pkg == context.packageName) continue

                val ts = event.timeStamp.coerceIn(startTime, endTime)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> activeFgStart[pkg] = ts
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val started = activeFgStart.remove(pkg) ?: startTime
                        if (ts > started) {
                            distributeDurationToSlots(hourlyFgMs, pkg, started, ts, startTime)
                        }
                    }
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> activeBgStart[pkg] = ts
                    UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                        val started = activeBgStart.remove(pkg) ?: startTime
                        if (ts > started) {
                            distributeDurationToSlots(hourlyBgMs, pkg, started, ts, startTime)
                        }
                    }
                }
            }

            activeFgStart.forEach { (pkg, started) ->
                if (endTime > started) distributeDurationToSlots(hourlyFgMs, pkg, started, endTime, startTime)
            }
            activeBgStart.forEach { (pkg, started) ->
                if (endTime > started) distributeDurationToSlots(hourlyBgMs, pkg, started, endTime, startTime)
            }
        } catch (_: Exception) {
        }

        val slotCurrentMa = FloatArray(24) { 0f }
        val slotCounts = IntArray(24) { 0 }
        var globalDischargeSum = 0f
        var globalDischargeCount = 0

        for (log in logs24h) {
            if (!log.isCharging && log.timestamp in startTime..endTime) {
                val slot = ((log.timestamp - startTime) / 3_600_000L).toInt().coerceIn(0, 23)
                val ma = abs(log.currentMa)
                if (ma > 5f) {
                    slotCurrentMa[slot] += ma
                    slotCounts[slot]++
                    globalDischargeSum += ma
                    globalDischargeCount++
                }
            }
        }
        val fallbackMa = if (globalDischargeCount > 0) globalDischargeSum / globalDischargeCount else 350f
        for (i in 0 until 24) {
            slotCurrentMa[i] = if (slotCounts[i] > 0) slotCurrentMa[i] / slotCounts[i] else fallbackMa
        }

        val rawAppWeights = mutableMapOf<String, Double>()
        val rawSlotWeights = mutableMapOf<String, DoubleArray>()

        for ((pkg, times) in totalTimesMap) {
            val fgSlots = hourlyFgMs[pkg]
            val bgSlots = hourlyBgMs[pkg]
            val slotWeights = DoubleArray(24)

            val eventFgSum = fgSlots?.sum() ?: 0L
            val eventBgSum = bgSlots?.sum() ?: 0L

            for (s in 0 until 24) {
                val fgMsInSlot = if (eventFgSum > 0L) fgSlots!![s].toDouble() else times.first / 24.0
                val bgMsInSlot = if (eventBgSum > 0L) bgSlots!![s].toDouble() else times.second / 24.0
                val energyScore = ((fgMsInSlot * 1.0) + (bgMsInSlot * 0.35)) * slotCurrentMa[s]
                slotWeights[s] = energyScore
            }

            rawSlotWeights[pkg] = slotWeights
            rawAppWeights[pkg] = slotWeights.sum()
        }

        val grandTotalWeight = rawAppWeights.values.sum().coerceAtLeast(1.0)
        val effectivePoolMah = totalActualDrainMah.coerceAtLeast(10f)

        val appFinalMahMap = mutableMapOf<String, Float>()
        for ((pkg, _) in totalTimesMap) {
            val sysMah = shizukuSystemMahMap[pkg]
            if (hasShizukuData && sysMah != null && sysMah > 0.2f) {
                appFinalMahMap[pkg] = sysMah
            } else {
                val share = ((rawAppWeights[pkg] ?: 0.0) / grandTotalWeight).toFloat()
                appFinalMahMap[pkg] = effectivePoolMah * share
            }
        }

        val totalDisplayedMah = appFinalMahMap.values.sum().coerceAtLeast(1f)
        val cal = Calendar.getInstance().apply { timeInMillis = startTime }
        val baseHour = cal.get(Calendar.HOUR_OF_DAY)

        return totalTimesMap.map { (pkg, times) ->
            val label = resolveAppLabel(pm, pkg)
            val appTotalMah = appFinalMahMap[pkg] ?: 0f
            val pctOfTotal = (appTotalMah / totalDisplayedMah) * 100f

            val appWeight = rawAppWeights[pkg] ?: 0.0
            val slotWeights = rawSlotWeights[pkg] ?: DoubleArray(24)
            val fgSlots = hourlyFgMs[pkg]
            val bgSlots = hourlyBgMs[pkg]

            val hourlyList = (0 until 24).map { slot ->
                val clockHour = (baseHour + slot) % 24
                val slotShare = if (appWeight > 0.0) (slotWeights[slot] / appWeight).toFloat() else (1f / 24f)
                val slotMah = appTotalMah * slotShare
                val fgMins = ((fgSlots?.get(slot) ?: (times.first / 24L)) / 60_000f)
                val bgMins = ((bgSlots?.get(slot) ?: (times.second / 24L)) / 60_000f)

                HourlyAppDrain(
                    slotIndex = slot,
                    hourOfDay = clockHour,
                    hourLabel = String.format(Locale.US, "%02d:00", clockHour),
                    foregroundMinutes = fgMins,
                    backgroundMinutes = bgMins,
                    estimatedMah = slotMah
                )
            }

            val peakSlot = hourlyList.maxByOrNull { it.estimatedMah }
            val peakRange = peakSlot?.let {
                val nextHour = (it.hourOfDay + 1) % 24
                String.format(Locale.US, "%02d:00 - %02d:00", it.hourOfDay, nextHour)
            } ?: "--"

            val isShizukuMeasured = hasShizukuData && shizukuSystemMahMap.containsKey(pkg)
            val (diagTitle, adviceList) = generateDiagnostics(
                packageName = pkg,
                appLabel = label,
                fgMs = times.first,
                bgMs = times.second,
                totalMah = appTotalMah,
                hourlyList = hourlyList,
                isShizukuMeasured = isShizukuMeasured
            )

            AppBatteryInsight(
                packageName = pkg,
                appLabel = label,
                totalEstimatedMah = appTotalMah,
                percentageOfTotalDrain = pctOfTotal,
                foregroundTimeMs = times.first,
                backgroundTimeMs = times.second,
                hourlyBreakdown = hourlyList,
                peakHourRange = peakRange,
                peakHourMah = peakSlot?.estimatedMah ?: 0f,
                diagnosisTitle = diagTitle,
                recommendations = adviceList,
                isShizukuSystemMeasured = isShizukuMeasured
            )
        }.sortedByDescending { it.totalEstimatedMah }.take(35)
    }

    private fun distributeDurationToSlots(
        map: MutableMap<String, LongArray>,
        pkg: String,
        rangeStart: Long,
        rangeEnd: Long,
        windowStart: Long
    ) {
        val arr = map.getOrPut(pkg) { LongArray(24) }
        var cursor = rangeStart
        while (cursor < rangeEnd) {
            val slot = ((cursor - windowStart) / 3_600_000L).toInt().coerceIn(0, 23)
            val slotEnd = windowStart + ((slot + 1) * 3_600_000L)
            val segmentEnd = minOf(rangeEnd, slotEnd)
            arr[slot] += (segmentEnd - cursor).coerceAtLeast(0L)
            cursor = segmentEnd
        }
    }

    private fun generateDiagnostics(
        packageName: String,
        appLabel: String,
        fgMs: Long,
        bgMs: Long,
        totalMah: Float,
        hourlyList: List<HourlyAppDrain>,
        isShizukuMeasured: Boolean
    ): Pair<String, List<String>> {
        val pkgLower = packageName.lowercase(Locale.US)
        val fgHours = fgMs / 3_600_000f
        val bgHours = bgMs / 3_600_000f
        val totalHours = (fgHours + bgHours).coerceAtLeast(0.01f)
        val bgRatio = bgHours / totalHours

        val nightMah = hourlyList.filter { it.hourOfDay in 0..5 }.sumOf { it.estimatedMah.toDouble() }.toFloat()
        val nightRatio = if (totalMah > 1f) nightMah / totalMah else 0f

        val isNetworkOrSystemTool = pkgLower.contains("dns") ||
                pkgLower.contains("vpn") ||
                pkgLower.contains("netmonster") ||
                pkgLower == "android" ||
                pkgLower.contains("launcher") ||
                pkgLower.contains("systemui")

        val tips = mutableListOf<String>()
        if (isShizukuMeasured) {
            tips.add("Bacaan mAh ini disahkan terus daripada enjin sistem Android (dumpsys batterystats: CPU, Modem & Wakelock).")
        }

        return when {
            isNetworkOrSystemTool -> {
                if (bgHours > 4f) {
                    tips.add("Aplikasi ini menjalankan perkhidmatan rangkaian atau sistem secara berterusan di latar belakang.")
                    tips.add("Jika ia adalah aplikasi VPN/DNS atau pemantau rangkaian, matikan servis apabila tidak diperlukan untuk menjimatkan tenaga modem & CPU.")
                } else {
                    tips.add("Penggunaan tenaga berada dalam julat normal bagi komponen sistem atau utiliti rangkaian.")
                }
                "Utiliti Sistem / Rangkaian Aktif" to tips
            }

            bgHours >= 4f || (bgRatio > 0.65f && totalMah > 80f) -> {
                tips.add("Aplikasi kekal aktif di latar belakang selama ${String.format(Locale.US, "%.1f", bgHours)} jam walaupun skrin ditutup.")
                tips.add("Gunakan butang 'Sekat Latar Belakang (Shizuku)' di bawah atau tukar tetapan bateri aplikasi kepada 'Terhad' (Restricted).")
                if (nightRatio > 0.25f) {
                    tips.add("Sebanyak ${(nightRatio * 100).toInt()}% tenaga digunakan antara jam 12:00 tengah malam hingga 6:00 pagi.")
                }
                tips.add("Hadkan kebenaran Lokasi (GPS) kepada 'Semasa menggunakan aplikasi sahaja'.")
                "Penggunaan Latar Belakang Tinggi" to tips
            }

            fgHours >= 1.5f -> {
                tips.add("Penggunaan didominasi oleh skrin aktif (${String.format(Locale.US, "%.1f", fgHours)} jam) dan pemprosesan grafik/data.")
                tips.add("Aktifkan Mod Gelap (Dark Mode) di dalam $appLabel untuk menjimatkan kuasa skrin AMOLED.")
                tips.add("Rendahkan kecerahan skrin atau gunakan Wi-Fi berbanding data mudah alih 4G/5G semasa sesi panjang.")
                "Penggunaan Skrin Aktif Tinggi" to tips
            }

            nightRatio > 0.35f && totalMah > 40f -> {
                tips.add("Aplikasi dikesan aktif pada waktu tidur (00:00 - 06:00) dan membangunkan peranti daripada mod Deep Sleep.")
                tips.add("Sekat aktiviti latar belakang aplikasi ini sebelum tidur.")
                "Aktiviti Latar Belakang Waktu Malam" to tips
            }

            else -> {
                tips.add("Penggunaan bateri oleh $appLabel adalah terkawal dan seimbang dengan tempoh penggunaannya.")
                "Penggunaan Tenaga Normal" to tips
            }
        }
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
