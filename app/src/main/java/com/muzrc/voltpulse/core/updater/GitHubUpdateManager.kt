package com.muzrc.voltpulse.core.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.muzrc.voltpulse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersion: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

object GitHubUpdateManager {

    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/MuzRC/VoltPulse/main/version.json"

    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$VERSION_JSON_URL?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Ralat pelayan GitHub: HTTP $responseCode"))
            }

            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(jsonText)
            val latestVersion = json.optString("latestVersion", BuildConfig.VERSION_NAME)
            val remoteVersionCode = json.optInt("versionCode", BuildConfig.VERSION_CODE)
            val releaseNotes = json.optString("releaseNotes", "Kemaskini baharu tersedia.")
            val downloadUrl = json.optString("downloadUrl", "")

            val hasUpdate = remoteVersionCode > BuildConfig.VERSION_CODE && downloadUrl.isNotBlank()

            Result.success(
                AppUpdateInfo(
                    latestVersion = latestVersion,
                    versionCode = remoteVersionCode,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    isUpdateAvailable = hasUpdate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadAndInstallApk(context: Context, updateInfo: AppUpdateInfo) {
        val fileName = "VoltPulse-v${updateInfo.latestVersion}-signed.apk"
        val destinationFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("Memuat turun VoltPulse v${updateInfo.latestVersion}")
            .setDescription("Kemaskini aplikasi pemantau bateri...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (_: IllegalArgumentException) {
                    }
                    installApkFile(ctx, destinationFile)
                }
            }
        }

        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApkFile(context: Context, apkFile: File) {
        if (!apkFile.exists()) return
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
