package com.muzrc.voltpulse.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.muzrc.voltpulse.ui.screens.apps.AppUsageScreen
import com.muzrc.voltpulse.ui.screens.dashboard.DashboardScreen
import com.muzrc.voltpulse.ui.screens.history.HistoryScreen
import com.muzrc.voltpulse.ui.screens.settings.SettingsScreen
import com.muzrc.voltpulse.ui.theme.AmoledBlack
import com.muzrc.voltpulse.ui.theme.DarkSurface
import com.muzrc.voltpulse.ui.theme.ElectricCyan
import com.muzrc.voltpulse.ui.theme.TextSecondary

private data class NavTab(
    val title: String,
    val icon: ImageVector
)

@Composable
fun VoltPulseMainScaffold() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        NavTab("Dashboard", Icons.Outlined.BatteryChargingFull),
        NavTab("Sejarah", Icons.Outlined.History),
        NavTab("Aplikasi", Icons.Outlined.Apps),
        NavTab("Tetapan", Icons.Outlined.Settings)
    )

    Scaffold(
        containerColor = AmoledBlack,
        bottomBar = {
            NavigationBar(containerColor = DarkSurface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            selectedTextColor = ElectricCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = ElectricCyan.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen()
                1 -> HistoryScreen()
                2 -> AppUsageScreen()
                3 -> SettingsScreen()
            }
        }
    }
}
