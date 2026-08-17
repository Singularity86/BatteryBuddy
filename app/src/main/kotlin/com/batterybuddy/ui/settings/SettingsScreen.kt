package com.batterybuddy.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.batterybuddy.worker.BatterySchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var deviceModel by remember { mutableStateOf(state.deviceModel) }
    var ratedMah by remember { mutableStateOf(state.ratedMah?.toString().orEmpty()) }
    var tempThreshold by remember { mutableStateOf(state.tempAlertThresholdCelsius.toString()) }
    var holdThreshold by remember { mutableStateOf(state.overnightHoldThresholdMinutes.toString()) }
    var pollingInterval by remember { mutableStateOf(state.backgroundPollingIntervalMinutes.toString()) }
    var chargeAlarmEnabled by remember { mutableStateOf(state.chargeAlarmPercent > 0) }
    var chargeAlarmPercent by remember { mutableStateOf(state.chargeAlarmPercent.takeIf { it > 0 }?.toString() ?: "80") }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(
        state.deviceModel, state.ratedMah, state.tempAlertThresholdCelsius,
        state.overnightHoldThresholdMinutes, state.backgroundPollingIntervalMinutes,
        state.chargeAlarmPercent
    ) {
        deviceModel = state.deviceModel
        ratedMah = state.ratedMah?.toString().orEmpty()
        tempThreshold = state.tempAlertThresholdCelsius.toString()
        holdThreshold = state.overnightHoldThresholdMinutes.toString()
        pollingInterval = state.backgroundPollingIntervalMinutes.toString()
        chargeAlarmEnabled = state.chargeAlarmPercent > 0
        if (state.chargeAlarmPercent > 0) chargeAlarmPercent = state.chargeAlarmPercent.toString()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(viewModel::exportTo) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        SettingsCard {
            OutlinedTextField(
                value = deviceModel,
                onValueChange = { deviceModel = it },
                label = { Text("Device model") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            NumberField("Battery rated capacity (mAh)", ratedMah) { ratedMah = it }
            Text(
                text = "From your phone's spec sheet. Health % is measured against this, " +
                    "and it belongs to the battery that's currently installed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.updateDeviceModel(deviceModel)
                    viewModel.updateRatedMah(ratedMah.toIntOrNull())
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save device")
            }
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Unplug reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "One notification when the battery reaches your target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = chargeAlarmEnabled,
                    onCheckedChange = { enabled ->
                        chargeAlarmEnabled = enabled
                        viewModel.updateChargeAlarmPercent(
                            if (enabled) chargeAlarmPercent.toIntOrNull() ?: 80 else 0
                        )
                    }
                )
            }
            if (chargeAlarmEnabled) {
                Spacer(Modifier.height(12.dp))
                NumberField("Remind me at (%)", chargeAlarmPercent) { chargeAlarmPercent = it }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        chargeAlarmPercent.toIntOrNull()?.let(viewModel::updateChargeAlarmPercent)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save reminder")
                }
            }
        }

        SettingsCard {
            NumberField("High temperature alert (°C)", tempThreshold) { tempThreshold = it }
            Spacer(Modifier.height(8.dp))
            NumberField("Overnight hold alert (minutes)", holdThreshold) { holdThreshold = it }
            Spacer(Modifier.height(8.dp))
            NumberField("Background sampling interval (minutes)", pollingInterval) { pollingInterval = it }
            Text(
                text = "Android won't sample more often than every " +
                    "${BatterySchedule.MIN_INTERVAL_MINUTES} minutes while unplugged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    tempThreshold.toIntOrNull()?.let(viewModel::updateTempThreshold)
                    holdThreshold.toIntOrNull()?.let(viewModel::updateOvernightThreshold)
                    pollingInterval.toIntOrNull()?.let(viewModel::updatePollingInterval)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save monitoring")
            }
        }

        SettingsCard {
            Text("Your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Everything BatteryTruth records stays on this device. Export it as CSV any " +
                    "time, or delete all of it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch(defaultExportFileName()) }) {
                    Text("Export CSV")
                }
                OutlinedButton(onClick = { showClearConfirm = true }) {
                    Text("Delete all data")
                }
            }
        }

        TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
            Text(if (showDiagnostics) "Hide diagnostics" else "Show diagnostics")
        }

        if (showDiagnostics) {
            DiagnosticsCard(state)
        }

        message?.let { text ->
            Snackbar(
                action = { TextButton(onClick = viewModel::consumeMessage) { Text("OK") } }
            ) { Text(text) }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Delete all recorded data?") },
            text = {
                Text(
                    "This removes every charge session, discharge event and reading. " +
                        "Battery health estimates start again from zero. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearConfirm = false
                }) { Text("Delete everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun defaultExportFileName(): String =
    "batterytruth-${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}.csv"

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DiagnosticsCard(state: SettingsUiState) {
    SettingsCard {
        Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val reading = state.latestReading
        val session = state.latestSession
        DiagnosticRow("Latest stored reading", reading?.timestamp?.formatTimestamp() ?: "None")
        DiagnosticRow("Battery", reading?.let { "${it.batteryPercent}% ${it.chargeState.name}" } ?: "Unknown")
        DiagnosticRow("Power", reading?.let { "%.1f W, ${it.currentMicroAmps / 1000} mA".format(it.chargingPowerWatts) } ?: "Unknown")
        DiagnosticRow("Temperature", reading?.let { "%.1f °C".format(it.temperatureCelsius) } ?: "Unknown")
        DiagnosticRow("Source", reading?.chargeSource?.name ?: "Unknown")
        DiagnosticRow("Latest session", session?.let { "#${it.id} ${if (it.isOpen) "open" else "closed"}" } ?: "None")
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun Long.formatTimestamp(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
