package com.muzrc.voltpulse.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.regex.Pattern

enum class ShizukuStatus {
    NOT_INSTALLED,
    SERVICE_STOPPED,
    PERMISSION_REQUIRED,
    READY
}

data class KernelBatteryHardwareInfo(
    val chargeFullMah: Float? = null,
    val chargeFullDesignMah: Float? = null,
    val cycleCount: Int? = null
)

object ShizukuBridge {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE_SHIZUKU = 2001

    fun getStatus(context: Context): ShizukuStatus {
        val installed = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
        if (!installed) return ShizukuStatus.NOT_INSTALLED

        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
        if (!binderAlive) return ShizukuStatus.SERVICE_STOPPED

        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        return if (granted) ShizukuStatus.READY else ShizukuStatus.PERMISSION_REQUIRED
    }

    fun isReady(context: Context): Boolean = getStatus(context) == ShizukuStatus.READY

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun execShell(command: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return@withContext null
            }

            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as ShizukuRemoteProcess

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }
            process.waitFor()
            process.destroy()
            output
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Membaca terus fail PMIC kernel di /sys/class/power_supply/battery/
     */
    suspend fun readKernelBatteryHardware(context: Context): KernelBatteryHardwareInfo {
        if (!isReady(context)) return KernelBatteryHardwareInfo()

        val cmd = "cat /sys/class/power_supply/battery/charge_full 2>/dev/null || echo -1; " +
            "echo '---'; " +
            "cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null || echo -1; " +
            "echo '---'; " +
            "cat /sys/class/power_supply/battery/cycle_count 2>/dev/null || echo -1"

        val raw = execShell(cmd) ?: return KernelBatteryHardwareInfo()
        val parts = raw.split("---").map { it.trim() }

        val fullRaw = parts.getOrNull(0)?.toLongOrNull() ?: -1L
        val designRaw = parts.getOrNull(1)?.toLongOrNull() ?: -1L
        val cycleRaw = parts.getOrNull(2)?.toIntOrNull() ?: -1

        val fullMah = normalizeUahToMah(fullRaw)
        val designMah = normalizeUahToMah(designRaw)
        val cycles = cycleRaw.takeIf { it >= 0 }

        return KernelBatteryHardwareInfo(
            chargeFullMah = fullMah,
            chargeFullDesignMah = designMah,
            cycleCount = cycles
        )
    }

    private fun normalizeUahToMah(raw: Long): Float? {
        if (raw <= 0L) return null
        val mah = if (raw > 100_000L) raw / 1000f else raw.toFloat()
        return mah.takeIf { it in 500f..25000f }
    }

    /**
     * Mengekstrak anggaran penggunaan mAh sebenar setiap aplikasi daripada enjin sistem:
     * dumpsys batterystats
     */
    suspend fun readSystemBatteryStatsMah(context: Context): Map<String, Float> {
        if (!isReady(context)) return emptyMap()

        val output = execShell("dumpsys batterystats") ?: return emptyMap()
        val pm = context.packageManager
        val resultMap = mutableMapOf<String, Float>()

        val lines = output.lines()
        var inPowerSection = false
        val uidPattern = Pattern.compile("^\\s*Uid\\s+([a-zA-Z0-9_]+):\\s+([0-9.]+)")

        for (line in lines) {
            if (line.contains("Estimated power use (mAh):")) {
                inPowerSection = true
                continue
            }
            if (inPowerSection) {
                if (line.isBlank() || (!line.startsWith(" ") && !line.startsWith("\t"))) {
                    if (resultMap.isNotEmpty()) break
                    continue
                }
                val matcher = uidPattern.matcher(line)
                if (matcher.find()) {
                    val uidStr = matcher.group(1) ?: continue
                    val mahVal = matcher.group(2)?.toFloatOrNull() ?: continue
                    if (mahVal <= 0.1f) continue

                    val numericUid = parseAndroidUidString(uidStr) ?: continue
                    val packages = pm.getPackagesForUid(numericUid) ?: continue
                    if (packages.isNotEmpty()) {
                        val sharePerPkg = mahVal / packages.size.toFloat()
                        for (pkg in packages) {
                            if (pkg != context.packageName) {
                                resultMap[pkg] = (resultMap[pkg] ?: 0f) + sharePerPkg
                            }
                        }
                    }
                }
            }
        }
        return resultMap
    }

    private fun parseAndroidUidString(uidStr: String): Int? {
        val clean = uidStr.trim().lowercase(Locale.US)
        clean.toIntOrNull()?.let { return it }

        val match = Pattern.compile("^u(\\d+)a(\\d+)$").matcher(clean)
        if (match.find()) {
            val userId = match.group(1)?.toIntOrNull() ?: 0
            val appId = match.group(2)?.toIntOrNull() ?: return null
            return (userId * 100_000) + UserHandle.FIRST_APPLICATION_UID + appId
        }
        return null
    }

    suspend fun restrictBackgroundViaShizuku(context: Context, packageName: String): Boolean {
        if (!isReady(context)) return false
        val cmd = "cmd appops set $packageName RUN_IN_BACKGROUND ignore; " +
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND ignore"
        val res = execShell(cmd)
        return res != null
    }

    suspend fun forceStopViaShizuku(context: Context, packageName: String): Boolean {
        if (!isReady(context)) return false
        val res = execShell("am force-stop $packageName")
        return res != null
    }
}
