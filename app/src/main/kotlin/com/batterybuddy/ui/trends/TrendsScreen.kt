package com.batterybuddy.ui.trends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.DischargeEvent
import com.batterybuddy.data.model.HealthVerdict
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

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
                    color = if (date == "Currently Tracking") MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary,
                    fontWeight = if (date == "Currently Tracking") FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            items(items) { item ->
                when (item) {
                    is HistoryItem.Charge -> SessionWearItem(
                        session = item.session,
                        isCurrent = item.session.id == state.currentChargeSessionId
                    )
                    is HistoryItem.Discharge -> DischargeEventItem(
                        event = item.event,
                        isCurrent = item.event.id == state.currentDischargeEventId
                    )
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
fun SessionWearItem(session: ChargeSession, isCurrent: Boolean) {
    val isInterrupted = session.isOpen && !isCurrent
    var expanded by remember { mutableStateOf(false) }
    val cost = session.displayWearCost()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent && !session.isOpen) { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 2.dp),
        colors = if (isCurrent) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // --- Summary row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${session.startPercent}% → ${session.endPercent ?: if (isCurrent) "Active" else "Unknown"}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isCurrent || isInterrupted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (isCurrent) "LIVE" else "INTERRUPTED",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = "${session.chargeSource.name} · ${formatDurationMinutes(session.displayDurationMinutes())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (cost != null) {
                            val (label, color) = getWearLabel(cost)
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        } else if (isCurrent) {
                            Text(
                                text = "Tracking…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (session.hasAbusiveTemp) {
                            Text(
                                text = "High Heat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    // Expand chevron only for completed sessions
                    if (!isCurrent && !session.isOpen) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (expanded) 180f else 0f)
                        )
                    }
                }
            }

            // --- Drill-down ---
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    SessionDetailGrid(session, cost)
                }
            }
        }
    }
}

@Composable
private fun SessionDetailGrid(session: ChargeSession, cost: Float?) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val startStr = remember(session.startTimestamp) { dateFormat.format(Date(session.startTimestamp)) }
    val endStr = session.endTimestamp?.let { remember(it) { dateFormat.format(Date(it)) } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow("Started", startStr)
        if (endStr != null) DetailRow("Ended", endStr)

        val chargerName = session.chargerLabel
            ?: session.chargerFingerprint?.substringBefore("|")
            ?: session.chargeSource.name.lowercase().replaceFirstChar { it.uppercase() }
        DetailRow("Charger", chargerName)

        session.peakTemperatureCelsius?.let {
            DetailRow("Peak temp", "${it.toInt()}°C", if (it > 38f) MaterialTheme.colorScheme.error else null)
        }

        session.energyAddedWattHours?.let {
            DetailRow("Energy added", "%.1f Wh".format(it))
        }

        val dod = ((session.endPercent ?: 0) - session.startPercent).coerceAtLeast(0)
        DetailRow("Depth of charge", "$dod%")

        if (cost != null) {
            val tempC = session.peakTemperatureCelsius ?: 25f
            val factorStr = when {
                tempC > 45f -> "1.5× (very hot)"
                tempC > 38f -> "1.2× (warm)"
                tempC < 10f -> "1.1× (cold)"
                else -> "1.0× (normal)"
            }
            DetailRow("Temp factor", factorStr)
        }

        if (session.isOvernightHold) {
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    "Overnight hold detected",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DischargeEventItem(event: DischargeEvent, isCurrent: Boolean) {
    val isInterrupted = event.isOpen && !isCurrent
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
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
                        text = "${event.startPercent}% → ${event.endPercent ?: if (isCurrent) "Active" else "Unknown"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isCurrent || isInterrupted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                if (isCurrent) "TRACKING" else "INTERRUPTED",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSecondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = "Discharge · ${formatDurationMinutes(event.displayDurationMinutes())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    HealthVerdict.HEALTHY          -> Color(0xFF4CAF50)
    HealthVerdict.WATCH_IT         -> Color(0xFFFF9800)
    HealthVerdict.PLAN_REPLACEMENT -> Color(0xFFFF5722)
    HealthVerdict.REPLACE_NOW      -> Color(0xFFF44336)
}

fun getWearLabel(cost: Float): Pair<String, Color> = when {
    cost > 0.8f -> "High Impact"   to Color(0xFFF44336)
    cost > 0.4f -> "Medium Impact" to Color(0xFFFF9800)
    else        -> "Low Impact"    to Color(0xFF4CAF50)
}

private fun ChargeSession.displayDurationMinutes(): Int {
    durationMinutes?.let { return it }
    val end = endTimestamp ?: System.currentTimeMillis()
    return max(0L, (end - startTimestamp) / 60_000L).toInt()
}

private fun ChargeSession.displayWearCost(): Float? {
    weightedCycleCost?.let { return it }
    val end = endPercent ?: return null
    val chargedPercent = (end - startPercent).coerceAtLeast(0) / 100f
    if (chargedPercent <= 0f) return 0f
    val tempC = peakTempTenthsCelsius?.div(10f) ?: 25f
    val tempFactor = when {
        tempC > 45f -> 1.5f
        tempC > 38f -> 1.2f
        tempC < 10f -> 1.1f
        else        -> 1.0f
    }
    return chargedPercent * chargedPercent * tempFactor
}

private fun DischargeEvent.displayDurationMinutes(): Int {
    durationMinutes?.let { return it }
    val end = endTimestamp ?: System.currentTimeMillis()
    return max(0L, (end - startTimestamp) / 60_000L).toInt()
}

private fun formatDurationMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0             -> "${hours}h"
        else                  -> "${mins}m"
    }
}
