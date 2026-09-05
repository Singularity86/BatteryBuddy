package jibaro.etherdrive.reserve.ui.dashboard

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
import jibaro.etherdrive.reserve.data.analysis.DrainSummary
import jibaro.etherdrive.reserve.data.model.BatteryReading
import jibaro.etherdrive.reserve.data.model.ChargeSource
import jibaro.etherdrive.reserve.data.model.ChargeState

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
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
                        drain = state.drain,
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
    drain: DrainSummary? = null,
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

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildStatusDescription(reading, isCharging),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
            // Sign is normalized by charge direction: the raw sensor value is negative
            // while charging on some devices (e.g. Samsung), which was showing a
            // confusing negative number. "+" = flowing into the battery, "-" = draining.
            val currentMa = kotlin.math.abs(reading.currentMicroAmps) / 1000
            MetricItem(
                "Current",
                if (isCharging) "+$currentMa mA" else "-$currentMa mA",
                MaterialTheme.colorScheme.outline
            )
        }
        
        if (drain != null) {
            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))
            DrainSinceFullCard(drain)
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

/**
 * Drain split by screen state. Screen-off drain is the number worth looking at:
 * it's what the phone costs while you aren't using it, and the place a
 * misbehaving app actually shows up.
 */
@Composable
private fun DrainSinceFullCard(drain: DrainSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (drain.anchoredToFullCharge) "Since last full charge" else "Recent usage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${drain.percentUsed}% used over ${formatMinutes(drain.screenOnMinutes + drain.screenOffMinutes)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            drain.estimatedHoursRemaining?.let { hours ->
                Text(
                    text = "About ${formatHours(hours)} left at this rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DrainStat(
                    label = "Screen on",
                    time = formatMinutes(drain.screenOnMinutes),
                    rate = drain.screenOnDrainPerHour
                )
                DrainStat(
                    label = "Screen off",
                    time = formatMinutes(drain.screenOffMinutes),
                    rate = drain.screenOffDrainPerHour
                )
            }
        }
    }
}

@Composable
private fun DrainStat(label: String, time: String, rate: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = rate?.let { "%.1f%%/h".format(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0             -> "${hours}h"
        else                  -> "${mins}m"
    }
}

private fun formatHours(hours: Float): String {
    val whole = hours.toInt()
    val mins = ((hours - whole) * 60).toInt()
    return when {
        whole > 0 && mins > 0 -> "${whole}h ${mins}m"
        whole > 0             -> "${whole}h"
        else                  -> "${mins}m"
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

/** Human-readable description of what the battery is doing right now. */
fun buildStatusDescription(reading: BatteryReading, isCharging: Boolean): String {
    if (isCharging) {
        if (reading.batteryPercent >= 100 || reading.chargeState == ChargeState.FULL) {
            return "Fully charged — topping off. Unplugging now reduces long-term wear."
        }
        val w = reading.chargingPowerWatts
        val tier = when {
            w >= 20f -> "Fast charging"
            w >= 7f  -> "Charging"
            w > 0f   -> "Trickle charging"
            else     -> "Charging"
        }
        val powerStr = if (w > 0f) " at %.0f W".format(w) else ""
        val proto = reading.chargeProtocolLabel ?: reading.chargerType
        return if (proto != null) "$tier$powerStr · $proto" else "$tier$powerStr"
    }

    val drainMa = kotlin.math.abs(reading.currentMicroAmps) / 1000
    val tier = when {
        drainMa >= 800 -> "High drain"
        drainMa >= 250 -> "Discharging"
        else           -> "Low drain"
    }
    val screen = if (reading.isScreenOn) "screen on" else "screen off"
    return "$tier · $drainMa mA · $screen"
}
