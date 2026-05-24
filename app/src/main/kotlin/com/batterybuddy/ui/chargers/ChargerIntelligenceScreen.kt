package com.batterybuddy.ui.chargers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChargerIntelligenceScreen(
    viewModel: ChargerIntelligenceViewModel,
    onNavigateToEducation: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingPrompt by viewModel.pendingLabelPrompt.collectAsStateWithLifecycle()

    pendingPrompt?.let { (fingerprint, autoLabel) ->
        ChargerLabelDialog(
            autoLabel = autoLabel,
            onSave = { label -> viewModel.saveChargerLabel(fingerprint, label) },
            onSkip = { viewModel.dismissLabelPrompt(fingerprint) }
        )
    }

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
                is ChargerUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is ChargerUiState.Empty -> ChargerEmptyState(modifier = Modifier.align(Alignment.Center))
                is ChargerUiState.Content -> ChargerList(
                    chargers = state.chargers,
                    onRename = { fp, label -> viewModel.saveChargerLabel(fp, label) }
                )
            }
        }
    }
}

@Composable
private fun ChargerLabelDialog(
    autoLabel: String,
    onSave: (String) -> Unit,
    onSkip: () -> Unit
) {
    var text by remember { mutableStateOf(autoLabel) }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Name this charger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Detected: $autoLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Charger name") },
                    placeholder = { Text("e.g. Bedroom brick, Car charger") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    )
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
fun ChargerList(
    chargers: List<ChargerStats>,
    onRename: (fingerprint: String, label: String) -> Unit = { _, _ -> }
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(chargers, key = { it.fingerprint }) { charger ->
            ChargerCard(charger, onRename = { label -> onRename(charger.fingerprint, label) })
        }
    }
}

@Composable
fun ChargerCard(
    charger: ChargerStats,
    onRename: (label: String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        ChargerLabelDialog(
            autoLabel = charger.label,
            onSave = { label ->
                onRename(label)
                showRenameDialog = false
            },
            onSkip = { showRenameDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // --- Header ---
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename charger",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (charger.sessions.isNotEmpty()) {
                        IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = if (expanded) "Collapse sessions" else "Show sessions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(if (expanded) 180f else 0f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Metrics ---
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricDetail(
                    "Avg Temp",
                    "${charger.averagePeakTempCelsius.toInt()}°C",
                    getTempColor(charger.averagePeakTempCelsius)
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = 16.dp)
                )
                MetricDetail(
                    "Avg Speed",
                    if (charger.averageWatts > 0f) "%.1f W".format(charger.averageWatts) else "Collecting",
                    MaterialTheme.colorScheme.primary
                )
            }

            if (charger.abusiveSessionCount > 0) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${charger.abusiveSessionCount} abusive heat events detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // --- Session history (expandable) ---
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        "Session History",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    charger.sessions.forEachIndexed { index, session ->
                        SessionRow(session)
                        if (index < charger.sessions.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val dateStr = remember(session.startTimestamp) { dateFormat.format(Date(session.startTimestamp)) }
    val durationStr = session.durationMinutes?.let { m ->
        if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    } ?: "—"
    val percentStr = "${session.startPercent}% → ${session.endPercent ?: "?"}%"
    val wattsStr = if (session.avgWatts > 0f) "%.0f W".format(session.avgWatts) else null
    val tempStr = session.peakTempCelsius?.let { "${it.toInt()}°C" }

    Column {
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(durationStr, style = MaterialTheme.typography.bodySmall)
            Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Text(percentStr, style = MaterialTheme.typography.bodySmall)
            if (wattsStr != null) {
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(wattsStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (tempStr != null) {
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(
                    tempStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.hasAbusiveTemp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    else        -> Color(0xFFF44336)
}

fun getTempColor(temp: Float): Color = when {
    temp > 40 -> Color(0xFFF44336)
    temp > 35 -> Color(0xFFFF9800)
    else      -> Color(0xFF4CAF50)
}
