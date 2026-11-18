package com.muzrc.voltpulse.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.muzrc.voltpulse.core.updater.AppUpdateInfo
import com.muzrc.voltpulse.ui.theme.AmoledBlack
import com.muzrc.voltpulse.ui.theme.DarkSurfaceVariant
import com.muzrc.voltpulse.ui.theme.ElectricCyan
import com.muzrc.voltpulse.ui.theme.TextPrimary
import com.muzrc.voltpulse.ui.theme.TextSecondary

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onConfirmDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Text(
                text = "Kemaskini VoltPulse v${updateInfo.latestVersion}",
                style = MaterialTheme.typography.headlineSmall,
                color = ElectricCyan
            )
        },
        text = {
            Text(
                text = "Versi baharu (Build #${updateInfo.versionCode}) tersedia di GitHub.

Nota Keluaran:
${updateInfo.releaseNotes}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = AmoledBlack
                )
            ) {
                Text("Muat Turun & Pasang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Nanti", color = TextSecondary)
            }
        }
    )
}
