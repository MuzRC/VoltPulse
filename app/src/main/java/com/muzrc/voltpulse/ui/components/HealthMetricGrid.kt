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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muzrc.voltpulse.core.sensor.BatteryEstimates
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
fun HealthMetricGrid(
    snapshot: BatteryTelemetrySnapshot,
    estimates: BatteryEstimates,
    modifier: Modifier = Modifier
) {
    val healthColor = when {
        estimates.healthPercentage >= 85f -> ChargeGreen
        estimates.healthPercentage >= 70f -> DischargeAmber
        else -> OverheatRed
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricBox(
                modifier = Modifier.weight(1f),
                title = "KESIHATAN BATERI",
                primaryValue = String.format(Locale.US, "%.1f%%", estimates.healthPercentage),
                subtitle = String.format(
                    Locale.US,
                    "%.0f / %.0f mAh",
                    estimates.actualCapacityMah,
                    estimates.designCapacityMah
                ),
                accentColor = healthColor
            )
            MetricBox(
                modifier = Modifier.weight(1f),
                title = "RINTANGAN DALAMAN",
                primaryValue = estimates.internalResistanceMohm?.let {
                    String.format(Locale.US, "%.0f mΩ", it)
                } ?: "Menunggu Beban",
                subtitle = "Baki: ${String.format(Locale.US, "%.0f mAh", estimates.remainingCapacityMah)}",
                accentColor = ElectricCyan
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricBox(
                modifier = Modifier.weight(1f),
                title = "ANGGARAN SKRIN AKTIF",
                primaryValue = formatHoursToHm(estimates.remainingSotHours),
                subtitle = "Baki Screen-On Time",
                accentColor = DischargeAmber
            )
            MetricBox(
                modifier = Modifier.weight(1f),
                title = "ANGGARAN GABUNGAN",
                primaryValue = formatHoursToHm(estimates.remainingCombinedHours),
                subtitle = "Standby: ${formatHoursToHm(estimates.remainingStandbyHours)}",
                accentColor = ChargeGreen
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkOutline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Teknologi: ${snapshot.technology} • Status: ${snapshot.healthStatus}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (snapshot.cycleCount >= 0) {
                    Text(
                        text = "Kitaran: ${snapshot.cycleCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    primaryValue: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.headlineSmall,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

fun formatHoursToHm(hoursFloat: Float): String {
    if (hoursFloat.isNaN() || hoursFloat.isInfinite() || hoursFloat <= 0f) return "--"
    val totalMinutes = (hoursFloat * 60f).toInt().coerceAtMost(999 * 60)
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}j ${m}m"
}
