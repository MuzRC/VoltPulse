package com.muzrc.voltpulse.core.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.muzrc.voltpulse.data.local.entity.AppUsageEntity

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

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
