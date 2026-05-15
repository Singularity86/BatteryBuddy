package com.batterybuddy.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.DischargeEvent
import com.batterybuddy.data.model.HealthVerdict
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel,
    onNavigateToEducation: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Health Trends",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onNavigateToEducation) {
                Text("Learn Why")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is TrendsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TrendsUiState.Empty -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sessions recorded yet. Start charging to see trends.")
                }
            }
            is TrendsUiState.Content -> TrendsContent(state)
        }
    }
}

@Composable
fun TrendsContent(state: TrendsUiState.Content) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            CalibrationGuidanceCard(state.completedChargeSessionCount)
            Spacer(modifier = Modifier.height(12.dp))
            HealthSummaryCard(state)
            Spacer(modifier = Modifier.height(16.dp))
        }

        state.groupedHistory.forEach { (date, items) ->
            item {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (date == "Currently Tracking") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    fontWeight = if (date == "Currently Tracking") FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            items(items) { item ->
                when (item) {
                    is HistoryItem.Charge -> SessionWearItem(item.session)
                    is HistoryItem.Discharge -> DischargeEventItem(item.event)
                }
            }
        }
    }
}

@Composable
fun CalibrationGuidanceCard(completedSessions: Int) {
    if (completedSessions >= 5) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Calibration in progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Battery health estimates get steadier after about 5 completed charge sessions. Current count: $completedSessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun SessionWearItem(session: ChargeSession) {
    val isOpen = session.isOpen
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOpen) 4.dp else 2.dp),
        colors = if (isOpen) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) 
                 else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${session.startPercent}% → ${session.endPercent ?: "Active"}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isOpen) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "LIVE",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Text(
                    text = "${session.chargeSource.name} • ${session.durationMinutes ?: 0} min",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val cost = session.weightedCycleCost ?: 0f
                Text(
                    text = "%.2f Wear Units".format(cost),
                    style = MaterialTheme.typography.bodyMedium,
                    color = getWearColor(cost)
                )
                if (session.hasAbusiveTemp) {
                    Text(
                        text = "High Heat Warning",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun DischargeEventItem(event: DischargeEvent) {
    val isOpen = event.isOpen
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOpen) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isOpen) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${event.startPercent}% → ${event.endPercent ?: "Active"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOpen) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                "TRACKING",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
                Text(
                    text = "Discharge • ${event.durationMinutes ?: 0} min",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (event.hasAnomalousBackground) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "High Background Drain",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun HealthSummaryCard(state: TrendsUiState.Content) {
    val health = state.healthSummary ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = getVerdictColor(health.verdict).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Health Status", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = health.verdict.name.replace("_", " "),
                        style = MaterialTheme.typography.headlineSmall,
                        color = getVerdictColor(health.verdict),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${health.healthPercent.toInt()}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { health.healthPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = getVerdictColor(health.verdict),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Estimated Capacity: ${health.currentCapacityMah} mAh / ${health.ratedMah} mAh",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

fun getVerdictColor(verdict: HealthVerdict): Color = when (verdict) {
    HealthVerdict.HEALTHY -> Color(0xFF4CAF50)
    HealthVerdict.WATCH_IT -> Color(0xFFFF9800)
    HealthVerdict.PLAN_REPLACEMENT -> Color(0xFFFF5722)
    HealthVerdict.REPLACE_NOW -> Color(0xFFF44336)
}

fun getWearColor(cost: Float): Color = when {
    cost > 0.8f -> Color(0xFFF44336)
    cost > 0.4f -> Color(0xFFFF9800)
    else -> Color(0xFF4CAF50)
}
