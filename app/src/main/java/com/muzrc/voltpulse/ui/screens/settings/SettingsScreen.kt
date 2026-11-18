package com.muzrc.voltpulse.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muzrc.voltpulse.BuildConfig
import com.muzrc.voltpulse.VoltPulseApp
import com.muzrc.voltpulse.core.updater.GitHubUpdateManager
import com.muzrc.voltpulse.ui.components.UpdateDialog
import com.muzrc.voltpulse.ui.theme.AmoledBlack
import com.muzrc.voltpulse.ui.theme.ChargeGreen
import com.muzrc.voltpulse.ui.theme.DarkOutline
import com.muzrc.voltpulse.ui.theme.DarkSurface
import com.muzrc.voltpulse.ui.theme.ElectricCyan
import com.muzrc.voltpulse.ui.theme.TextPrimary
import com.muzrc.voltpulse.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val designCapacity by viewModel.designCapacity.collectAsState()
    val statusMessage by viewModel.updateStatusMessage.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val isChecking by viewModel.isCheckingUpdate.collectAsState()

    availableUpdate?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { viewModel.dismissUpdateDialog() },
            onConfirmDownload = {
                viewModel.dismissUpdateDialog()
                GitHubUpdateManager.downloadAndInstallApk(context, updateInfo)
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Tetapan & Kemas Kini",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kapasiti Asal Bateri Kilang (mAh)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Digunakan sebagai rujukan untuk mengira peratus kesihatan bateri sebenar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = designCapacity,
                        onValueChange = { viewModel.updateDesignCapacityInput(it) },
                        label = { Text("Contoh: 5000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.saveDesignCapacity() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = AmoledBlack
                        )
                    ) {
                        Text("Simpan Kapasiti")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Perkhidmatan Pemantauan Latar Belakang",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pastikan perkhidmatan aktif untuk merekod log nyahcas dan kesihatan bateri secara berterusan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            (context.applicationContext as? VoltPulseApp)?.startMonitorServiceSafely()
                        }
                    ) {
                        Text("Aktifkan Semula Servis Pemantauan", color = ChargeGreen)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Versi Aplikasi: v${BuildConfig.VERSION_NAME} (Build #${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Repositori: MuzRC/VoltPulse",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.checkForGitHubUpdate() },
                        enabled = !isChecking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = AmoledBlack
                        )
                    ) {
                        Text(if (isChecking) "Menyemak GitHub..." else "Semak Kemas Kini di GitHub")
                    }

                    statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChargeGreen
                        )
                    }
                }
            }
        }
    }
}
