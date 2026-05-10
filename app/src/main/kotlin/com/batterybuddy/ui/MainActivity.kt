package com.batterybuddy.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.batterybuddy.ui.dashboard.DashboardScreen
import com.batterybuddy.ui.dashboard.DashboardViewModel
import com.batterybuddy.ui.education.EducationScreen
import com.batterybuddy.ui.trends.TrendsScreen
import com.batterybuddy.ui.trends.TrendsViewModel
import com.batterybuddy.ui.chargers.ChargerIntelligenceScreen
import com.batterybuddy.ui.chargers.ChargerIntelligenceViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val trendsViewModel: TrendsViewModel by viewModels()
    private val chargerViewModel: ChargerIntelligenceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selectedTab by remember { mutableIntStateOf(0) }

            MaterialTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Live") },
                                label = { Text("Live") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.List, contentDescription = "Trends") },
                                label = { Text("Trends") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Star, contentDescription = "Chargers") },
                                label = { Text("Chargers") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Info, contentDescription = "School") },
                                label = { Text("School") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (selectedTab) {
                            0 -> DashboardScreen(dashboardViewModel) { selectedTab = 3 }
                            1 -> TrendsScreen(trendsViewModel) { selectedTab = 3 }
                            2 -> ChargerIntelligenceScreen(chargerViewModel) { selectedTab = 3 }
                            3 -> EducationScreen()
                        }
                    }
                }
            }
        }
    }
}
