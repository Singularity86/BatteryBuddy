package com.batterybuddy.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeState

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToEducation: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Battery Status",
                style = MaterialTheme.typography.headlineMedium
            )
            TextButton(onClick = onNavigateToEducation) {
                Text("Learn More")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is DashboardUiState.Loading -> CircularProgressIndicator()
            is DashboardUiState.Empty -> Text("No data available. Plug in to start monitoring.")
            is DashboardUiState.Content -> DashboardContent(state.reading)
        }
    }
}

@Composable
fun DashboardContent(reading: BatteryReading) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Battery Percentage Large
        Text(
            text = "${reading.batteryPercent}%",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = getPercentageColor(reading.batteryPercent)
        )

        Text(
            text = reading.chargeState.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem("Temperature", "${reading.temperatureCelsius}°C", getTempColor(reading.temperatureCelsius))
            MetricItem("Power", "%.1f W".format(reading.chargingPowerWatts), MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem("Voltage", "${reading.voltageMillivolts} mV", MaterialTheme.colorScheme.outline)
            MetricItem("Current", "${reading.currentMicroAmps / 1000} mA", MaterialTheme.colorScheme.outline)
        }
        
        if (reading.isPdActive == true) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "PD Fast Charging Active",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelLarge
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
