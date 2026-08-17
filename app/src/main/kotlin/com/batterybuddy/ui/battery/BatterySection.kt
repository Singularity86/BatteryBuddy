package com.batterybuddy.ui.battery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Active-battery selector + comparison card. Shown at the top of the History screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatterySection(viewModel: BatteryViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeBatteryId.collectAsStateWithLifecycle()
    val comparisons by viewModel.comparisons.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showReassign by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    if (profiles.isEmpty()) return
    val activeProfile = profiles.firstOrNull { it.id == activeId }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Active battery",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            profiles.forEach { p ->
                FilterChip(
                    selected = p.id == activeId,
                    onClick = { viewModel.setActiveBattery(p.id) },
                    label = { Text(p.label) }
                )
            }
            AssistChip(onClick = { showAdd = true }, label = { Text("+ Add") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (activeProfile != null) {
                TextButton(onClick = { showRename = true }) { Text("Rename") }
            }
            if (profiles.size >= 2) {
                TextButton(onClick = { showReassign = true }) {
                    Text("Fix attribution…")
                }
            }
        }

        if (comparisons.size >= 2) {
            Spacer(Modifier.height(12.dp))
            BatteryCompareCard(comparisons)
        }
    }

    if (showAdd) {
        AddBatteryDialog(
            onAdd = { label, mah, installedNow ->
                viewModel.addBattery(label, mah, installedNow)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    if (showRename && activeProfile != null) {
        RenameBatteryDialog(
            currentLabel = activeProfile.label,
            onRename = { label ->
                viewModel.renameBattery(activeProfile.id, label)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    if (showReassign) {
        ReassignDialog(
            profiles = profiles,
            onPick = { id ->
                viewModel.attributeSinceRestart(id)
                showReassign = false
            },
            onDismiss = { showReassign = false }
        )
    }
}

@Composable
private fun RenameBatteryDialog(
    currentLabel: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename battery") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(label) },
                enabled = label.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReassignDialog(
    profiles: List<com.batterybuddy.data.model.BatteryProfile>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attribute data since last restart") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Move everything recorded since your device last restarted to the battery that was actually installed, and make it active.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                profiles.forEach { p ->
                    OutlinedButton(
                        onClick = { onPick(p.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(p.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BatteryCompareCard(comparisons: List<BatteryComparison>) {
    val bestId = comparisons.filter { it.healthPercent != null }
        .maxByOrNull { it.healthPercent!! }?.profile?.id

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Compare batteries",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            comparisons.forEach { c ->
                BatteryCompareRow(c, isBest = c.profile.id == bestId)
            }
            Text(
                "\"Better\" = higher estimated capacity. A battery still calibrating needs about 5 full charges before it compares fairly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryCompareRow(c: BatteryComparison, isBest: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (if (isBest) "★ " else "") + c.profile.label + if (c.isActive) "  • active" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (c.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = c.healthPercent?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        val detail = if (c.isCalibrating) {
            "Calibrating · ${c.completedSessions}/5 charges"
        } else {
            buildString {
                append("${c.capacityMah} mAh")
                append(" · %.1f cycles".format(c.cyclesUsed))
                c.avgPeakTempC?.let { append(" · avg %.0f°C".format(it)) }
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddBatteryDialog(onAdd: (String, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var mah by remember { mutableStateOf("") }
    var installedNow by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add battery") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name (e.g. Spare, OEM)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = mah,
                    onValueChange = { input -> mah = input.filter { it.isDigit() } },
                    label = { Text("Rated mAh") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { installedNow = !installedNow },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(checked = installedNow, onCheckedChange = { installedNow = it })
                    Text(
                        "This battery is installed now — attribute data since the last restart to it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(label.ifBlank { "Battery" }, mah.toIntOrNull() ?: 4500, installedNow) },
                enabled = label.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Reboot-gated prompt asking which battery is installed. Rendered at app level. */
@Composable
fun SwapPromptDialog(viewModel: BatteryViewModel) {
    val show by viewModel.swapPrompt.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeBatteryId.collectAsStateWithLifecycle()
    if (!show) return

    AlertDialog(
        onDismissRequest = { viewModel.dismissSwap() },
        title = { Text("Which battery is installed?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Your device restarted. If you swapped batteries, pick the one that's in now so its stats stay accurate.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                profiles.forEach { p ->
                    OutlinedButton(
                        onClick = { viewModel.confirmSwap(p.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(p.label + if (p.id == activeId) "  (current)" else "")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.dismissSwap() }) { Text("Not sure / no change") }
        }
    )
}
