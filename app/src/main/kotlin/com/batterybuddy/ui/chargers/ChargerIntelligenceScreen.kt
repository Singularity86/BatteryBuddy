package com.batterybuddy.ui.chargers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChargerIntelligenceScreen(
    viewModel: ChargerIntelligenceViewModel,
    onNavigateToEducation: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = uiState) {
                is ChargerUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ChargerUiState.Empty -> {
                    ChargerEmptyState(modifier = Modifier.align(Alignment.Center))
                }
                is ChargerUiState.Content -> ChargerList(state.chargers)
            }
        }
    }
}

@Composable
private fun ChargerEmptyState(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Collecting charger behavior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Plug in a charger and keep BatteryBuddy open for a few minutes. Unplugging finishes the session so the app can compare speed, heat, and wear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChargerList(chargers: List<ChargerStats>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(chargers) { charger ->
            ChargerCard(charger)
        }
    }
}

@Composable
fun ChargerCard(charger: ChargerStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = charger.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Based on ${charger.sessionCount} samples",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = getScoreColor(charger.efficiencyScore).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${charger.efficiencyScore.toInt()}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = getScoreColor(charger.efficiencyScore)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricDetail("Avg Temp", "${charger.averagePeakTempCelsius.toInt()}°C", getTempColor(charger.averagePeakTempCelsius))
                VerticalDivider(modifier = Modifier.height(24.dp).align(Alignment.CenterVertically).padding(horizontal = 16.dp))
                MetricDetail(
                    "Avg Speed",
                    if (charger.averageWatts > 0f) "%.1f W".format(charger.averageWatts) else "Collecting",
                    MaterialTheme.colorScheme.primary
                )
            }

            if (charger.abusiveSessionCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${charger.abusiveSessionCount} abusive heat events detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun MetricDetail(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

fun getScoreColor(score: Float): Color = when {
    score >= 85 -> Color(0xFF4CAF50)
    score >= 70 -> Color(0xFF8BC34A)
    score >= 50 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

fun getTempColor(temp: Float): Color = when {
    temp > 40 -> Color(0xFFF44336)
    temp > 35 -> Color(0xFFFF9800)
    else -> Color(0xFF4CAF50)
}
