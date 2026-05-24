package com.batterybuddy.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deviceModel by remember { mutableStateOf(state.deviceModel) }
    var ratedMah by remember { mutableStateOf(state.ratedMahOverride?.toString().orEmpty()) }
    var tempThreshold by remember { mutableStateOf(state.tempAlertThresholdCelsius.toString()) }
    var holdThreshold by remember { mutableStateOf(state.overnightHoldThresholdMinutes.toString()) }
    var pollingInterval by remember { mutableStateOf(state.backgroundPollingIntervalMinutes.toString()) }
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(state.deviceModel, state.ratedMahOverride, state.tempAlertThresholdCelsius, state.overnightHoldThresholdMinutes, state.backgroundPollingIntervalMinutes) {
        deviceModel = state.deviceModel
        ratedMah = state.ratedMahOverride?.toString().orEmpty()
        tempThreshold = state.tempAlertThresholdCelsius.toString()
        holdThreshold = state.overnightHoldThresholdMinutes.toString()
        pollingInterval = state.backgroundPollingIntervalMinutes.toString()
    }

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
                text = "Found in your phone's spec sheet. Used to calculate health %. " +
                    "Currently using: ${ratedMah.toIntOrNull() ?: 4500} mAh.",
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
                Text("Save Device")
            }
        }

        SettingsCard {
            NumberField("High temperature alert (C)", tempThreshold) { tempThreshold = it }
            Spacer(Modifier.height(8.dp))
            NumberField("Overnight hold alert (minutes)", holdThreshold) { holdThreshold = it }
            Spacer(Modifier.height(8.dp))
            NumberField("Background polling interval (minutes)", pollingInterval) { pollingInterval = it }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    tempThreshold.toIntOrNull()?.let(viewModel::updateTempThreshold)
                    holdThreshold.toIntOrNull()?.let(viewModel::updateOvernightThreshold)
                    pollingInterval.toIntOrNull()?.let(viewModel::updatePollingInterval)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Monitoring")
            }
        }

        TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
            Text(if (showDiagnostics) "Hide Diagnostics" else "Show Diagnostics")
        }

        if (showDiagnostics) {
            DiagnosticsCard(state)
        }
    }
}

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
        DiagnosticRow("Latest reading", reading?.timestamp?.formatTimestamp() ?: "None")
        DiagnosticRow("Battery", reading?.let { "${it.batteryPercent}% ${it.chargeState.name}" } ?: "Unknown")
        DiagnosticRow("Power", reading?.let { "%.1f W, ${it.currentMicroAmps / 1000} mA".format(it.chargingPowerWatts) } ?: "Unknown")
        DiagnosticRow("Temperature", reading?.let { "%.1f C".format(it.temperatureCelsius) } ?: "Unknown")
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
