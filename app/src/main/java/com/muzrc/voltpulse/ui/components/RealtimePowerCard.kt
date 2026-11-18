package com.muzrc.voltpulse.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzrc.voltpulse.core.sensor.BatteryTelemetrySnapshot
import com.muzrc.voltpulse.ui.theme.ChargeGreen
import com.muzrc.voltpulse.ui.theme.DarkOutline
import com.muzrc.voltpulse.ui.theme.DarkSurface
import com.muzrc.voltpulse.ui.theme.DischargeAmber
import com.muzrc.voltpulse.ui.theme.ElectricCyan
import com.muzrc.voltpulse.ui.theme.OverheatRed
import com.muzrc.voltpulse.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun RealtimePowerCard(
    snapshot: BatteryTelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        snapshot.temperatureC >= 42f -> OverheatRed
        snapshot.isCharging -> ChargeGreen
        else -> DischargeAmber
    }

    val currentSign = if (snapshot.currentNowMa > 0f) "+" else ""

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TELEMETRI MASA NYATA",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${snapshot.level}%",
                        style = MaterialTheme.typography.displayMedium,
                        color = accentColor
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (snapshot.isCharging) "MENGECAS (${snapshot.chargeSource})" else "NYAHCAS AKTIF",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%s%.0f mA", currentSign, snapshot.currentNowMa),
                            style = MaterialTheme.typography.headlineSmall,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DarkOutline)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetrySubItem(
                    label = "KUASA",
                    value = String.format(Locale.US, "%.2f W", snapshot.powerWatts),
                    valueColor = ElectricCyan
                )
                TelemetrySubItem(
                    label = "VOLTAN",
                    value = "${snapshot.voltageMv} mV",
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                TelemetrySubItem(
                    label = "SUHU SEL",
                    value = String.format(Locale.US, "%.1f °C", snapshot.temperatureC),
                    valueColor = if (snapshot.temperatureC >= 40f) OverheatRed else ChargeGreen
                )
                TelemetrySubItem(
                    label = "PURATA mA",
                    value = String.format(Locale.US, "%.0f mA", snapshot.currentAverageMa),
                    valueColor = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TelemetrySubItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
    }
}
