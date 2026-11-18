package com.muzrc.voltpulse.ui.screens.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muzrc.voltpulse.core.usage.AppUsageTracker
import com.muzrc.voltpulse.data.local.entity.AppUsageEntity
import com.muzrc.voltpulse.ui.components.formatHoursToHm
import com.muzrc.voltpulse.ui.theme.AmoledBlack
import com.muzrc.voltpulse.ui.theme.DarkOutline
import com.muzrc.voltpulse.ui.theme.DarkSurface
import com.muzrc.voltpulse.ui.theme.DischargeAmber
import com.muzrc.voltpulse.ui.theme.ElectricCyan
import com.muzrc.voltpulse.ui.theme.TextPrimary
import com.muzrc.voltpulse.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AppUsageScreen(
    viewModel: AppUsageViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasPermission by viewModel.hasPermission.collectAsState()
    val appList by viewModel.appList.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Penggunaan Tenaga Aplikasi (24 Jam)",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        if (!hasPermission) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Kebenaran Akses Penggunaan Diperlukan",
                            style = MaterialTheme.typography.titleMedium,
                            color = DischargeAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Untuk mengesan aplikasi mana yang menggunakan tenaga di skrin dan latar belakang, sila benarkan akses Usage Stats untuk VoltPulse.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { AppUsageTracker.openUsageAccessSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = AmoledBlack
                            )
                        ) {
                            Text("Buka Tetapan Akses Penggunaan")
                        }
                    }
                }
            }
        } else if (appList.isEmpty()) {
            item {
                Text(
                    text = "Tiada data penggunaan aplikasi direkodkan dalam 24 jam terakhir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            items(appList, key = { it.packageName }) { item ->
                AppUsageRowCard(item)
            }
        }
    }
}

@Composable
private fun AppUsageRowCard(usage: AppUsageEntity) {
    val fgHours = usage.foregroundTimeMs / 3_600_000f
    val bgHours = usage.backgroundTimeMs / 3_600_000f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usage.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Skrin: ${formatHoursToHm(fgHours)} • Latar Belakang: ${formatHoursToHm(bgHours)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Text(
                text = String.format(Locale.US, "~%.0f mAh", usage.estimatedMah),
                style = MaterialTheme.typography.titleMedium,
                color = DischargeAmber
            )
        }
    }
}
