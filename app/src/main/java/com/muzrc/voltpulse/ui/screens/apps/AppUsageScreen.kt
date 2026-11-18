package com.muzrc.voltpulse.ui.screens.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muzrc.voltpulse.core.usage.AppBatteryInsight
import com.muzrc.voltpulse.core.usage.AppUsageTracker
import com.muzrc.voltpulse.ui.components.formatHoursToHm
import com.muzrc.voltpulse.ui.theme.AmoledBlack
import com.muzrc.voltpulse.ui.theme.ChargeGreen
import com.muzrc.voltpulse.ui.theme.DarkOutline
import com.muzrc.voltpulse.ui.theme.DarkSurface
import com.muzrc.voltpulse.ui.theme.DarkSurfaceVariant
import com.muzrc.voltpulse.ui.theme.DischargeAmber
import com.muzrc.voltpulse.ui.theme.ElectricCyan
import com.muzrc.voltpulse.ui.theme.OverheatRed
import com.muzrc.voltpulse.ui.theme.TextPrimary
import com.muzrc.voltpulse.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    viewModel: AppUsageViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.hasPermission) {
        if (uiState.hasPermission) {
            while (true) {
                delay(30_000L)
                viewModel.refreshData()
            }
        }
    }

    uiState.selectedApp?.let { selectedInsight ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectApp(null) },
            sheetState = sheetState,
            containerColor = DarkSurfaceVariant
        ) {
            AppDetailBottomSheetContent(
                insight = selectedInsight,
                isShizukuActive = uiState.isShizukuActive,
                feedbackMessage = uiState.actionFeedbackMessage,
                onRestrictShizuku = {
                    viewModel.restrictAppWithShizuku(
                        selectedInsight.packageName,
                        selectedInsight.appLabel
                    )
                },
                onForceStopShizuku = {
                    viewModel.forceStopAppWithShizuku(
                        selectedInsight.packageName,
                        selectedInsight.appLabel
                    )
                },
                onOpenAppSettings = {
                    AppUsageTracker.openAppInfoSettings(context, selectedInsight.packageName)
                }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Penggunaan Tenaga Aplikasi (24 Jam)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.refreshData() }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Segar Semula",
                        tint = ElectricCyan
                    )
                }
            }
        }

        if (!uiState.hasPermission) {
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
        } else {
            item {
                TotalUsageSummaryCard(uiState = uiState)
            }

            if (uiState.appInsights.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = "Tiada data penggunaan aplikasi direkodkan dalam 24 jam terakhir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                item {
                    Text(
                        text = "Tekan mana-mana aplikasi untuk graf mengikut jam & cadangan penjimatan:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                items(uiState.appInsights, key = { it.packageName }) { item ->
                    AppUsageRowCard(
                        insight = item,
                        onClick = { viewModel.selectApp(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalUsageSummaryCard(uiState: AppUsageUiState) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JUMLAH PENGGUNAAN BATERI (24 JAM)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (uiState.isShizukuActive) ChargeGreen.copy(alpha = 0.16f) else DarkOutline
                ) {
                    Text(
                        text = if (uiState.isShizukuActive) "MOD SHIZUKU" else "MOD STANDARD",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isShizukuActive) ChargeGreen else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%.0f mAh",
                        if (uiState.isShizukuActive) "" else "~",
                        uiState.totalDrainMah
                    ),
                    style = MaterialTheme.typography.displayMedium,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "(%.1f%% kapasiti)", uiState.totalDrainPercentOfBattery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DischargeAmber,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DarkOutline)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SKRIN AKTIF",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "~%.0f mAh", uiState.screenDrainMah),
                        style = MaterialTheme.typography.titleMedium,
                        color = ChargeGreen
                    )
                }
                Column {
                    Text(
                        text = "LATAR BELAKANG",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "~%.0f mAh", uiState.backgroundDrainMah),
                        style = MaterialTheme.typography.titleMedium,
                        color = DischargeAmber
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "AUTO-KEMASKINI",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = timeFormat.format(Date(uiState.lastUpdatedTimestamp)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUsageRowCard(
    insight: AppBatteryInsight,
    onClick: () -> Unit
) {
    val fgHours = insight.foregroundTimeMs / 3_600_000f
    val bgHours = insight.backgroundTimeMs / 3_600_000f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = insight.appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElectricCyan.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f%%", insight.percentageOfTotalDrain),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Skrin: ${formatHoursToHm(fgHours)} • Latar Belakang: ${formatHoursToHm(bgHours)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Puncak: ${insight.peakHourRange} • ${insight.diagnosisTitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bgHours >= 4f) OverheatRed else TextSecondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%.0f mAh",
                        if (insight.isShizukuSystemMeasured) "" else "~",
                        insight.totalEstimatedMah
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = DischargeAmber,
                    fontWeight = FontWeight.Bold
                )
                if (insight.isShizukuSystemMeasured) {
                    Text(
                        text = "Sistem",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChargeGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDetailBottomSheetContent(
    insight: AppBatteryInsight,
    isShizukuActive: Boolean,
    feedbackMessage: String?,
    onRestrictShizuku: () -> Unit,
    onForceStopShizuku: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val fgHours = insight.foregroundTimeMs / 3_600_000f
    val bgHours = insight.backgroundTimeMs / 3_600_000f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.appLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = insight.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%.0f mAh",
                        if (insight.isShizukuSystemMeasured) "" else "~",
                        insight.totalEstimatedMah
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = DischargeAmber
                )
                Text(
                    text = String.format(Locale.US, "%.1f%% daripada jumlah", insight.percentageOfTotalDrain),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkOutline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Skrin Aktif: ${formatHoursToHm(fgHours)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChargeGreen
                    )
                    Text(
                        text = "Latar Belakang: ${formatHoursToHm(bgHours)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DischargeAmber
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = OverheatRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "Jam Paling Banyak Guna Kuasa: %s (~%.0f mAh)",
                            insight.peakHourRange,
                            insight.peakHourMah
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = OverheatRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PECAHAN PENGGUNAAN MENGIKUT JAM (24 JAM)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkOutline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val maxSlotMah = insight.hourlyBreakdown.maxOfOrNull { it.estimatedMah }?.coerceAtLeast(1f) ?: 1f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val barCount = insight.hourlyBreakdown.size
                    if (barCount > 0) {
                        val totalSpacing = 4.dp.toPx() * (barCount - 1)
                        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2f)

                        insight.hourlyBreakdown.forEachIndexed { index, slot ->
                            val ratio = (slot.estimatedMah / maxSlotMah).coerceIn(0.04f, 1f)
                            val barHeight = size.height * ratio
                            val left = index * (barWidth + 4.dp.toPx())
                            val top = size.height - barHeight

                            val isPeak = slot.estimatedMah >= maxSlotMah * 0.85f && slot.estimatedMah > 0.5f
                            val barColor = when {
                                isPeak -> OverheatRed
                                slot.foregroundMinutes > slot.backgroundMinutes -> ElectricCyan
                                else -> DischargeAmber
                            }

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val firstLabel = insight.hourlyBreakdown.firstOrNull()?.hourLabel ?: "00:00"
                    val midLabel = insight.hourlyBreakdown.getOrNull(12)?.hourLabel ?: "12:00"
                    val lastLabel = insight.hourlyBreakdown.lastOrNull()?.hourLabel ?: "23:00"
                    Text(text = firstLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(text = midLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(text = lastLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendDot(color = OverheatRed, text = "Jam Puncak")
                    LegendDot(color = ElectricCyan, text = "Dominan Skrin")
                    LegendDot(color = DischargeAmber, text = "Latar Belakang")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, ChargeGreen.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = ChargeGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cadangan Penjimatan: ${insight.diagnosisTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        color = ChargeGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                insight.recommendations.forEach { tip ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        feedbackMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = ChargeGreen,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        if (isShizukuActive) {
            Button(
                onClick = onRestrictShizuku,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChargeGreen,
                    contentColor = AmoledBlack
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sekat Latar Belakang Terus (Shizuku)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onForceStopShizuku,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OverheatRed)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = null,
                    tint = OverheatRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Henti Paksa Aplikasi (Force Stop)", color = OverheatRed)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onOpenAppSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Buka Tetapan Aplikasi Sistem", color = ElectricCyan)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
