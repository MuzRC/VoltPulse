package com.muzrc.voltpulse.ui.screens.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muzrc.voltpulse.data.local.entity.ChargeSessionEntity
import com.muzrc.voltpulse.ui.components.formatHoursToHm
import com.muzrc.voltpulse.ui.theme.ChargeGreen
import com.muzrc.voltpulse.ui.theme.DarkOutline
import com.muzrc.voltpulse.ui.theme.DarkSurface
import com.muzrc.voltpulse.ui.theme.DischargeAmber
import com.muzrc.voltpulse.ui.theme.TextPrimary
import com.muzrc.voltpulse.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Sejarah Kitaran Cas & Nyahcas",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "Belum ada rekod sesi lengkap. Biarkan VoltPulse memantau kitaran cas dan nyahcas peranti anda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            items(sessions, key = { it.sessionId }) { session ->
                SessionHistoryCard(session = session)
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: ChargeSessionEntity) {
    val isCharge = session.sessionType == "CHARGE"
    val accentColor = if (isCharge) ChargeGreen else DischargeAmber
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val durationHours = ((session.endTime - session.startTime).coerceAtLeast(0L)) / 3_600_000f
    val sotHours = session.screenOnDurationMs / 3_600_000f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isCharge) "SESI PENGECASAN" else "SESI NYAHCAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
                Text(
                    text = "${dateFormat.format(Date(session.startTime))} → ${dateFormat.format(Date(session.endTime))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${session.startLevel}% → ${session.endLevel}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = String.format(Locale.US, "%.0f mAh", session.mahTransferred),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tempoh: ${formatHoursToHm(durationHours)}" +
                            if (!isCharge) " • SOT: ${formatHoursToHm(sotHours)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = String.format(Locale.US, "Suhu Puncak: %.1f°C", session.maxTemperatureC),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
