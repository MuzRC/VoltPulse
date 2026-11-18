package com.muzrc.voltpulse.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import com.muzrc.voltpulse.ui.theme.DarkOutline
import com.muzrc.voltpulse.ui.theme.DarkSurface
import com.muzrc.voltpulse.ui.theme.ElectricCyan
import com.muzrc.voltpulse.ui.theme.OverheatRed
import com.muzrc.voltpulse.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TelemetryChartCanvas(
    logs: List<BatteryLogEntity>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LENGKUNG PERATUS BATERI & SUHU (24 JAM)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "${logs.size} titik",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (logs.size < 2) {
                Text(
                    text = "Mengumpul titik data sejarah. Graf akan terlukis secara automatik apabila log mencukupi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // Garis grid mendatar (25%, 50%, 75%)
                    for (step in 1..3) {
                        val y = height * (step / 4f)
                        drawLine(
                            color = DarkOutline,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val count = logs.size
                    val stepX = width / (count - 1).coerceAtLeast(1)

                    // 1. Lukis lengkung peratus bateri (0..100%)
                    val levelPath = Path()
                    val fillPath = Path()

                    logs.forEachIndexed { index, log ->
                        val x = index * stepX
                        val normalizedY = 1f - (log.level.coerceIn(0, 100) / 100f)
                        val y = normalizedY * height

                        if (index == 0) {
                            levelPath.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            levelPath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )

                    drawPath(
                        path = levelPath,
                        color = ElectricCyan,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 2. Lukis lengkung suhu (skala 20°C hingga 50°C)
                    val tempPath = Path()
                    logs.forEachIndexed { index, log ->
                        val x = index * stepX
                        val normTemp = ((log.temperatureC - 20f) / 30f).coerceIn(0f, 1f)
                        val y = (1f - normTemp) * height
                        if (index == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
                    }

                    drawPath(
                        path = tempPath,
                        color = OverheatRed.copy(alpha = 0.85f),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val minTemp = logs.minOf { it.temperatureC }
                val maxTemp = logs.maxOf { it.temperatureC }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "● Peratus Bateri (%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "● Suhu (%.1f°C – %.1f°C)",
                            minTemp,
                            maxTemp
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = OverheatRed
                    )
                }
            }
        }
    }
}
