package com.muzrc.voltpulse.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muzrc.voltpulse.ui.components.HealthMetricGrid
import com.muzrc.voltpulse.ui.components.RealtimePowerCard
import com.muzrc.voltpulse.ui.components.TelemetryChartCanvas
import com.muzrc.voltpulse.ui.theme.TextPrimary

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val snapshot by viewModel.telemetry.collectAsState()
    val estimates by viewModel.estimates.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "VoltPulse",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        item {
            RealtimePowerCard(snapshot = snapshot)
        }

        item {
            HealthMetricGrid(
                snapshot = snapshot,
                estimates = estimates
            )
        }

        item {
            TelemetryChartCanvas(logs = recentLogs)
        }
    }
}
