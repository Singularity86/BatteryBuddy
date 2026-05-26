package com.batterybuddy.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToEducation: () -> Unit,
    onViewGraph: (sessionId: Long, isCharge: Boolean) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is DashboardUiState.Empty -> {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Awaiting First Reading",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Plug in or use your device for a moment.\nMonitoring is active in the background.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is DashboardUiState.Content -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    DashboardContent(
                        reading = state.reading,
                        chargeSessionId = state.chargeSessionId,
                        dischargeEventId = state.dischargeEventId,
                        onViewGraph = onViewGraph
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    reading: BatteryReading,
    chargeSessionId: Long? = null,
    dischargeEventId: Long? = null,
    onViewGraph: (sessionId: Long, isCharge: Boolean) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isCharging = reading.chargeSource != ChargeSource.NONE &&
        (reading.chargeState == ChargeState.CHARGING || reading.chargeState == ChargeState.FULL)
    val graphId = if (isCharging) chargeSessionId else dischargeEventId

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${reading.batteryPercent}%",
            fontSize = if (isLandscape) 52.sp else 84.sp,
            fontWeight = FontWeight.Bold,
            color = getPercentageColor(reading.batteryPercent)
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isCharging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = if (graphId != null) Modifier.clickable { onViewGraph(graphId, isCharging) } else Modifier
        ) {
            Text(
                text = if (isCharging) "CHARGING ↗" else "DISCHARGING ↗",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCharging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 20.dp else 48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isCharging) {
                MetricItem("Input Power", "%.1f W".format(reading.chargingPowerWatts), MaterialTheme.colorScheme.primary)
            } else {
                val drainMa = kotlin.math.abs(reading.currentMicroAmps / 1000)
                MetricItem("Drain Speed", "${drainMa} mA", MaterialTheme.colorScheme.error)
            }
            MetricItem("Temperature", "${reading.temperatureCelsius}°C", getTempColor(reading.temperatureCelsius))
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem("Voltage", "${reading.voltageMillivolts} mV", MaterialTheme.colorScheme.outline)
            MetricItem("Physical Current", "${reading.currentMicroAmps / 1000} mA", MaterialTheme.colorScheme.outline)
        }
        
        if (isCharging) {
            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))
            val label = reading.chargeProtocolLabel ?: reading.chargerType ?: "Standard Charger"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (reading.isPdActive == true) MaterialTheme.colorScheme.tertiaryContainer 
                                     else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (reading.isPdActive == true) MaterialTheme.colorScheme.onTertiaryContainer 
                            else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

fun getPercentageColor(percent: Int): Color = when {
    percent > 80 -> Color(0xFF4CAF50)
    percent > 20 -> Color(0xFF8BC34A)
    else -> Color(0xFFF44336)
}

fun getTempColor(temp: Float): Color = when {
    temp > 42 -> Color(0xFFF44336)
    temp > 35 -> Color(0xFFFF9800)
    else -> Color(0xFF4CAF50)
}
