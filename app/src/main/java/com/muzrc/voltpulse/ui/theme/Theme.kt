package com.muzrc.voltpulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AmoledColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = AmoledBlack,
    secondary = ChargeGreen,
    onSecondary = AmoledBlack,
    tertiary = DischargeAmber,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkOutline,
    error = OverheatRed
)

@Composable
fun VoltPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColorScheme,
        typography = VoltPulseTypography,
        content = content
    )
}
