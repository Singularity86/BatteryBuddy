package jibaro.etherdrive.reserve.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import jibaro.etherdrive.reserve.data.repository.BatteryRepository
import jibaro.etherdrive.reserve.ui.dashboard.DashboardScreen
import jibaro.etherdrive.reserve.ui.dashboard.DashboardViewModel
import jibaro.etherdrive.reserve.ui.education.EducationScreen
import jibaro.etherdrive.reserve.ui.graph.SessionGraphScreen
import jibaro.etherdrive.reserve.ui.graph.SessionGraphViewModel
import jibaro.etherdrive.reserve.ui.trends.TrendsScreen
import jibaro.etherdrive.reserve.ui.trends.TrendsViewModel
import jibaro.etherdrive.reserve.ui.appdrain.AppDrainViewModel
import jibaro.etherdrive.reserve.ui.battery.BatteryViewModel
import jibaro.etherdrive.reserve.ui.battery.SwapPromptDialog
import jibaro.etherdrive.reserve.ui.chargers.ChargerIntelligenceScreen
import jibaro.etherdrive.reserve.ui.chargers.ChargerIntelligenceViewModel
import jibaro.etherdrive.reserve.ui.onboarding.OnboardingScreen
import jibaro.etherdrive.reserve.ui.main.MainViewModel
import jibaro.etherdrive.reserve.ui.settings.SettingsScreen
import jibaro.etherdrive.reserve.ui.settings.SettingsViewModel
import jibaro.etherdrive.reserve.service.BatteryPollingService
import jibaro.etherdrive.reserve.worker.BatterySchedule
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    @Inject lateinit var repository: BatteryRepository

    private val mainViewModel: MainViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val trendsViewModel: TrendsViewModel by viewModels()
    private val chargerViewModel: ChargerIntelligenceViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val sessionGraphViewModel: SessionGraphViewModel by viewModels()
    private val batteryViewModel: BatteryViewModel by viewModels()
    private val appDrainViewModel: AppDrainViewModel by viewModels()
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

        syncTrackingState()

        setContent {
            val hasCompletedOnboarding by mainViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
            var destination by remember { mutableStateOf(AppDestination.Live) }
            var showNotificationRationale by remember { mutableStateOf(shouldRequestNotificationPermission()) }

            BatteryBuddyTheme {
                var graphSessionId by remember { mutableStateOf(-1L) }
                var graphIsCharge by remember { mutableStateOf(true) }
                var backDestination by remember { mutableStateOf(AppDestination.Live) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (hasCompletedOnboarding) {
                            AppTopBar(
                                destination = destination,
                                onBack = { destination = backDestination },
                                onOpenSchool = { destination = AppDestination.School },
                                onOpenSettings = { destination = AppDestination.Settings }
                            )
                        }
                    },
                    bottomBar = {
                        if (hasCompletedOnboarding) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = destination == AppDestination.Live,
                                    onClick = { destination = AppDestination.Live },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Live") },
                                    label = { Text("Live") }
                                )
                                NavigationBarItem(
                                    selected = destination == AppDestination.History,
                                    onClick = { destination = AppDestination.History },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History") },
                                    label = { Text("History") }
                                )
                                NavigationBarItem(
                                    selected = destination == AppDestination.Chargers,
                                    onClick = { destination = AppDestination.Chargers },
                                    icon = { Icon(Icons.Default.Star, contentDescription = "Chargers") },
                                    label = { Text("Chargers") }
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
                            when (destination) {
                                AppDestination.Live -> DashboardScreen(
                                    viewModel = dashboardViewModel,
                                    onViewGraph = { id, isCharge ->
                                        graphSessionId = id
                                        graphIsCharge = isCharge
                                        backDestination = AppDestination.Live
                                        destination = AppDestination.SessionGraph
                                    }
                                )
                                AppDestination.History -> TrendsScreen(
                                    viewModel = trendsViewModel,
                                    batteryViewModel = batteryViewModel,
                                    appDrainViewModel = appDrainViewModel,
                                    onViewGraph = { id, isCharge ->
                                        graphSessionId = id
                                        graphIsCharge = isCharge
                                        backDestination = AppDestination.History
                                        destination = AppDestination.SessionGraph
                                    }
                                )
                                AppDestination.Chargers -> ChargerIntelligenceScreen(chargerViewModel)
                                AppDestination.School -> EducationScreen()
                                AppDestination.Settings -> SettingsScreen(settingsViewModel)
                                AppDestination.SessionGraph -> SessionGraphScreen(
                                    viewModel = sessionGraphViewModel,
                                    sessionId = graphSessionId,
                                    isCharge = graphIsCharge
                                )
                            }
                        }
                    }
                }

                if (showNotificationRationale) {
                    NotificationPermissionDialog(
                        onGrant = {
                            showNotificationRationale = false
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onDismiss = {
                            showNotificationRationale = false
                            syncTrackingState()
                        }
                    )
                }

                if (hasCompletedOnboarding) {
                    SwapPromptDialog(batteryViewModel)
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
        // Usage access is granted in system settings, so re-check on the way back.
        appDrainViewModel.refresh()
    }

    private fun syncTrackingState() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val percent = getBatteryPercent(batteryStatus)

        if (plugged > 0) {
            // Starting a foreground service can be refused when the system's
            // daily budget for the type is exhausted; sampling resumes on the
            // next plug-in rather than taking the app down with it.
            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, BatteryPollingService::class.java)
                )
            }
            return
        }

        // Not plugged in, so any charge session still marked open is stale —
        // the service died or the device rebooted mid-charge. Close it quietly;
        // the unplug receiver owns closes that deserve a summary notification.
        lifecycleScope.launch { repository.closeOpenChargeSessions(percent) }
        BatterySchedule.requestImmediateSample(this)
    }

    private fun getBatteryPercent(batteryStatus: Intent?): Int {
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) level * 100 / scale else {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceAtLeast(0)
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
}

private enum class AppDestination(val title: String, val isPrimary: Boolean) {
    Live("Live", true),
    History("History", true),
    Chargers("Chargers", true),
    School("Battery School", false),
    Settings("Settings", false),
    SessionGraph("Session Graph", false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    destination: AppDestination,
    onBack: () -> Unit,
    onOpenSchool: () -> Unit,
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = destination.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (!destination.isPrimary) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSchool) {
                Icon(Icons.Default.Info, contentDescription = "Battery School")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    )
}

@Composable
private fun NotificationPermissionDialog(
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow battery alerts?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BatteryBuddy uses notifications for monitoring that needs to keep running while you charge.")
                Text("Granting this lets the app show:")
                PermissionReason("A quiet monitoring notification while charging")
                PermissionReason("High-temperature warnings before heat damages long-term capacity")
                PermissionReason("Overnight full-charge alerts so you can unplug sooner")
                Text(
                    text = "BatteryBuddy does not use this permission for ads or marketing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onGrant) {
                Text("Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

@Composable
private fun PermissionReason(text: String) {
    Text(
        text = "- $text",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
}
