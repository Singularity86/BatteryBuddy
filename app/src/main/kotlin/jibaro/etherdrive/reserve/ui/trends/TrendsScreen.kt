package jibaro.etherdrive.reserve.ui.trends

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
import jibaro.etherdrive.reserve.data.analysis.ChargeMath
import jibaro.etherdrive.reserve.data.analysis.WearBand
import jibaro.etherdrive.reserve.data.battery.NativeHealthStatus
import jibaro.etherdrive.reserve.data.model.ChargeSession
import jibaro.etherdrive.reserve.data.model.DischargeEvent
import jibaro.etherdrive.reserve.data.model.HealthSummary
import jibaro.etherdrive.reserve.data.model.HealthVerdict
import jibaro.etherdrive.reserve.ui.appdrain.AppDrainSection
import jibaro.etherdrive.reserve.ui.appdrain.AppDrainViewModel
import jibaro.etherdrive.reserve.ui.battery.BatterySection
import jibaro.etherdrive.reserve.ui.battery.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel,
    batteryViewModel: BatteryViewModel,
    appDrainViewModel: AppDrainViewModel,
    onViewGraph: (sessionId: Long, isCharge: Boolean) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BatterySection(batteryViewModel)
        Spacer(modifier = Modifier.height(12.dp))

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
            is TrendsUiState.Content -> TrendsContent(state, appDrainViewModel, onViewGraph)
        }
    }
}

@Composable
fun TrendsContent(
    state: TrendsUiState.Content,
    appDrainViewModel: AppDrainViewModel,
    onViewGraph: (sessionId: Long, isCharge: Boolean) -> Unit = { _, _ -> }
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            DeviceFaultCard(state)
            CalibrationGuidanceCard(state.healthSummary, state.completedChargeSessionCount)
            Spacer(modifier = Modifier.height(12.dp))
            HealthSummaryCard(state)
            Spacer(modifier = Modifier.height(8.dp))
            LifetimeWearCard(state)
            Spacer(modifier = Modifier.height(8.dp))
            ReplacementOutlookCard(state)
            Spacer(modifier = Modifier.height(8.dp))
            OvernightHoldCard(state)
            Spacer(modifier = Modifier.height(8.dp))
            AppDrainSection(appDrainViewModel)
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
                        isCurrent = item.session.id == state.currentChargeSessionId,
                        onViewGraph = { onViewGraph(item.session.id, true) }
                    )
                    is HistoryItem.Discharge -> DischargeEventItem(
                        event = item.event,
                        isCurrent = item.event.id == state.currentDischargeEventId,
                        onViewGraph = { onViewGraph(item.event.id, false) }
                    )
                }
            }
        }
    }
}

@Composable
fun CalibrationGuidanceCard(health: HealthSummary?, completedSessions: Int) {
    // Capacity can only be read at the top of a charge, so say so plainly rather
    // than showing a blank card or a number we haven't earned yet.
    val body = when {
        health == null && completedSessions == 0 ->
            "Charge to 100% once and BatteryTruth can measure your battery's real capacity."
        health == null ->
            "No full charge recorded yet. Capacity can only be read at 100%, so charge all the way up once to unlock the health estimate."
        health.fullChargeCount < FULL_CHARGES_FOR_CONFIDENCE ->
            "Based on ${health.fullChargeCount} full ${if (health.fullChargeCount == 1) "charge" else "charges"}. The estimate settles after about $FULL_CHARGES_FOR_CONFIDENCE."
        else -> return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Still calibrating",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Surfaces overnight holds, which were being recorded and never shown.
 * Framed as a habit over a window rather than a running guilt tally.
 */
@Composable
fun OvernightHoldCard(state: TrendsUiState.Content) {
    val nights = state.overnightNights
    if (nights == 0) return

    val longest = state.recentOvernightHolds.maxOfOrNull { it.durationMinutes } ?: 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Overnight charging", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$nights of the last ${state.overnightWindowNights} nights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Longest stretch sitting at 100%: ${formatDurationMinutes(longest)}. " +
                    "Unplugging once it's full — or charging before bed instead of overnight — is the easiest habit to change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val FULL_CHARGES_FOR_CONFIDENCE = 5

@Composable
fun SessionWearItem(
    session: ChargeSession,
    isCurrent: Boolean,
    onViewGraph: () -> Unit = {}
) {
    val isInterrupted = session.isOpen && !isCurrent
    var expanded by remember { mutableStateOf(false) }
    val cost = session.displayWearCost()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewGraph() },
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
                        text = "${session.chargerLabel ?: session.chargeSource.name} · ${formatDurationMinutes(session.displayDurationMinutes())}",
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
                    // Chevron to toggle detail drill-down (completed sessions only)
                    if (!isCurrent && !session.isOpen) {
                        androidx.compose.material3.IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(if (expanded) 180f else 0f)
                            )
                        }
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

        val chargedPercent = ((session.endPercent ?: 0) - session.startPercent).coerceAtLeast(0)
        DetailRow("Charge added", "$chargedPercent%")

        if (cost != null) {
            DetailRow("Temp factor", ChargeMath.temperatureFactorLabel(session.peakTempTenthsCelsius))
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
fun DischargeEventItem(
    event: DischargeEvent,
    isCurrent: Boolean,
    onViewGraph: () -> Unit = {}
) {
    val isInterrupted = event.isOpen && !isCurrent
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewGraph() },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.hasAnomalousBackground) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "High Background Drain",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    if (!isCurrent && !event.isOpen) {
                        androidx.compose.material3.IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(if (expanded) 180f else 0f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    DischargeDetailGrid(event)
                }
            }
        }
    }
}

@Composable
private fun DischargeDetailGrid(event: DischargeEvent) {
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault()) }
    val startStr = remember(event.startTimestamp) { dateFormat.format(java.util.Date(event.startTimestamp)) }
    val endStr = event.endTimestamp?.let { remember(it) { dateFormat.format(java.util.Date(it)) } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow("Started", startStr)
        if (endStr != null) DetailRow("Ended", endStr)
        event.percentDepleted?.let { DetailRow("Depleted", "$it%") }
        event.durationMinutes?.let { DetailRow("Duration", formatDurationMinutes(it)) }
        event.depletionRateMahPerHour?.let {
            DetailRow("Drain rate", "%.0f mAh/h".format(it))
        }
        event.averageCurrentMicroAmps?.let {
            if (it != 0) DetailRow("Avg current", "${Math.abs(it / 1000)} mA")
        }
        if (event.hasAnomalousBackground) {
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    "Anomalous background drain detected",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
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
                text = "Estimated capacity: ${health.currentCapacityMah} mAh of ${health.ratedMah} mAh rated",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Measured from ${health.fullChargeCount} full " +
                    if (health.fullChargeCount == 1) "charge" else "charges",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Battery life used, framed against a horizon rather than as a running damage
 * tally. A bare "cycles used" counter with no achievable target is the kind of
 * thing that makes people anxious about charging their own phone; a percentage
 * of an expected lifespan, with a plain-language reading, is actionable.
 */
@Composable
fun LifetimeWearCard(state: TrendsUiState.Content) {
    val wear = state.lifetimeWear
    if (wear <= 0f && state.completedChargeSessionCount == 0) return

    // Where the device counts real cycles, our weighted heuristic steps aside.
    val measuredCycles = state.nativeCycleCount?.toFloat()
    val cycles = measuredCycles ?: wear
    val usedFraction = (cycles / RATED_CYCLES).coerceIn(0f, 1f)
    val usedPercent = (usedFraction * 100).toInt()
    val accent = when {
        usedFraction > 0.85f -> Color(0xFFF44336)
        usedFraction > 0.6f  -> Color(0xFFFF9800)
        else                 -> Color(0xFF4CAF50)
    }
    val reading = when {
        usedFraction > 0.85f -> "Approaching the end of a typical lifespan. Worth planning a replacement."
        usedFraction > 0.6f  -> "Past the halfway mark, which is normal at this much use."
        else                 -> "Comfortably within normal. Nothing here needs your attention."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Battery life used", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$usedPercent%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = "of a typical ${RATED_CYCLES.toInt()}-cycle lifespan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = reading,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (measuredCycles != null) {
                    "Your device reports ${measuredCycles.toInt()} charge cycles."
                } else {
                    "Estimated from ${state.completedChargeSessionCount} charges on this battery."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.nativeHealthStatus == NativeHealthStatus.GOOD) {
                Text(
                    text = "Device self-check: no faults reported.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Answers "should I replace this?" with a measured trend rather than a vibe.
 * Absent until there are enough full charges over enough weeks for a line
 * through them to mean anything.
 */
@Composable
fun ReplacementOutlookCard(state: TrendsUiState.Content) {
    val projection = state.capacityProjection ?: return

    val decline = -projection.changeMahPerMonth
    val headline = when {
        projection.monthsUntilThreshold == null ->
            "No measurable decline yet"
        projection.monthsUntilThreshold > 24f ->
            "More than 2 years of normal use left"
        else ->
            "About ${formatMonths(projection.monthsUntilThreshold)} before replacement is worth considering"
    }
    val detail = if (projection.monthsUntilThreshold == null) {
        "Capacity has held steady across ${projection.observationCount} full charges over " +
            "${projection.spanDays} days. Nothing to act on."
    } else {
        "Losing roughly ${decline.toInt()} mAh per month. At that rate you'd reach " +
            "${projection.thresholdMah} mAh — the point most people replace at — from today's " +
            "${projection.currentCapacityMah} mAh."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Replacement outlook", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Projected from ${projection.observationCount} full charges over ${projection.spanDays} days.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMonths(months: Float): String {
    val rounded = months.toInt()
    return when {
        rounded >= 24 -> "${rounded / 12} years"
        rounded >= 12 -> "a year and ${rounded - 12} months"
        rounded <= 1  -> "a month"
        else          -> "$rounded months"
    }
}

/**
 * The OS's own fault verdict, shown only when it is actually reporting a problem.
 * This is a hardware fault flag, not a capacity figure — worth surfacing loudly
 * precisely because it is measured rather than estimated.
 */
@Composable
fun DeviceFaultCard(state: TrendsUiState.Content) {
    val status = state.nativeHealthStatus ?: return
    if (status == NativeHealthStatus.GOOD) return

    val (headline, advice) = when (status) {
        NativeHealthStatus.OVERHEAT ->
            "Your device is reporting the battery as overheated" to
                "Unplug it, take any case off, and let it cool. If this persists when the phone is idle and cool, have it looked at."
        NativeHealthStatus.DEAD ->
            "Your device is reporting the battery as failed" to
                "This is a hardware fault reported by the phone itself, not an estimate. The battery needs replacing."
        NativeHealthStatus.OVER_VOLTAGE ->
            "Your device is reporting an over-voltage fault" to
                "Try a different charger and cable. If it continues, stop using that charger and have the phone checked."
        NativeHealthStatus.COLD ->
            "Your device is reporting the battery as too cold" to
                "Charging in the cold is hard on a battery. Let it warm up to room temperature first."
        NativeHealthStatus.UNSPECIFIED_FAILURE ->
            "Your device is reporting a battery fault" to
                "The phone flagged a problem without naming it. Worth a service check if it keeps appearing."
        NativeHealthStatus.GOOD -> return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = advice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private const val RATED_CYCLES = 500f

fun getVerdictColor(verdict: HealthVerdict): Color = when (verdict) {
    HealthVerdict.HEALTHY          -> Color(0xFF4CAF50)
    HealthVerdict.WATCH_IT         -> Color(0xFFFF9800)
    HealthVerdict.PLAN_REPLACEMENT -> Color(0xFFFF5722)
    HealthVerdict.REPLACE_NOW      -> Color(0xFFF44336)
}

fun getWearLabel(cost: Float): Pair<String, Color> {
    val band = ChargeMath.wearBand(cost)
    val color = when (band) {
        WearBand.HIGH   -> Color(0xFFF44336)
        WearBand.MEDIUM -> Color(0xFFFF9800)
        WearBand.LOW    -> Color(0xFF4CAF50)
    }
    return band.label to color
}

private fun ChargeSession.displayDurationMinutes(): Int {
    durationMinutes?.let { return it }
    val end = endTimestamp ?: System.currentTimeMillis()
    return max(0L, (end - startTimestamp) / 60_000L).toInt()
}

/** Stored cost once the session is closed; the same model applied live before that. */
private fun ChargeSession.displayWearCost(): Float? {
    weightedCycleCost?.let { return it }
    val end = endPercent ?: return null
    return ChargeMath.cycleCost(
        ChargeMath.chargeFraction(startPercent, end),
        peakTempTenthsCelsius
    )
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
