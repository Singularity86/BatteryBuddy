package com.batterybuddy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.BatteryManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.batterybuddy.ui.dashboard.DashboardScreen
import com.batterybuddy.ui.dashboard.DashboardViewModel
import com.batterybuddy.ui.education.EducationScreen
import com.batterybuddy.ui.trends.TrendsScreen
import com.batterybuddy.ui.trends.TrendsViewModel
import com.batterybuddy.ui.chargers.ChargerIntelligenceScreen
import com.batterybuddy.ui.chargers.ChargerIntelligenceViewModel
import com.batterybuddy.ui.onboarding.OnboardingScreen
import com.batterybuddy.ui.main.MainViewModel
import com.batterybuddy.ui.settings.SettingsScreen
import com.batterybuddy.ui.settings.SettingsViewModel
import com.batterybuddy.service.BatteryPollingService
import com.batterybuddy.worker.BatteryDataWorker
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8BC34A),
    secondary = Color(0xFFCDDC39),
    tertiary = Color(0xFF4CAF50),
    background = Color(0xFF1A1C18),
    surface = Color(0xFF1A1C18),
    onPrimary = Color(0xFF1E3600),
    onSecondary = Color(0xFF323500),
    onBackground = Color(0xFFE2E3D8),
    onSurface = Color(0xFFE2E3D8),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3F6A00),
    secondary = Color(0xFF5D6200),
    tertiary = Color(0xFF006E1C),
    background = Color(0xFFFDFCF4),
    surface = Color(0xFFFDFCF4),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1C18),
    onSurface = Color(0xFF1A1C18),
)

@Composable
fun BatteryBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val trendsViewModel: TrendsViewModel by viewModels()
    private val chargerViewModel: ChargerIntelligenceViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        syncTrackingState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Modern Android (targeting SDK 35+) enforces edge-to-edge.
        // We must enable it and use Scaffold's innerPadding to avoid clipping.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            val hasCompletedOnboarding by mainViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
            var selectedTab by remember { mutableIntStateOf(0) }

            BatteryBuddyTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (hasCompletedOnboarding) {
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
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Trends") },
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
                                NavigationBarItem(
                                    selected = selectedTab == 4,
                                    onClick = { selectedTab = 4 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (!hasCompletedOnboarding) {
                            OnboardingScreen { model, mah ->
                                mainViewModel.completeOnboarding(model, mah)
                            }
                        } else {
                            when (selectedTab) {
                                0 -> DashboardScreen(dashboardViewModel) { selectedTab = 3 }
                                1 -> TrendsScreen(trendsViewModel) { selectedTab = 3 }
                                2 -> ChargerIntelligenceScreen(chargerViewModel) { selectedTab = 3 }
                                3 -> EducationScreen()
                                4 -> SettingsScreen(settingsViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            syncTrackingState()
        }
    }

    private fun syncTrackingState() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        
        if (plugged > 0) {
            val serviceIntent = Intent(this, BatteryPollingService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            WorkManager.getInstance(this).enqueueUniqueWork(
                LIVE_REFRESH_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BatteryDataWorker>().build()
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            syncTrackingState()
            return
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            syncTrackingState()
        } else {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val LIVE_REFRESH_WORK_NAME = "battery_live_refresh"
    }
}
