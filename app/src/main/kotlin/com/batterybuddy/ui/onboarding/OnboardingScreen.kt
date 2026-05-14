package com.batterybuddy.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onComplete: (deviceModel: String, ratedMah: Int) -> Unit
) {
    var deviceModel by remember { mutableStateOf(android.os.Build.MODEL) }
    var ratedMah by remember { mutableStateOf("4500") }
    var step by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            1 -> {
                Text("Welcome to BatteryBuddy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "To calculate your battery health accurately, we need to know your device's design specifications.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { step = 2 }) {
                    Text("Get Started")
                }
            }
            2 -> {
                Text("Device Model", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deviceModel,
                    onValueChange = { deviceModel = it },
                    label = { Text("Model Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { step = 3 }) {
                    Text("Next")
                }
            }
            3 -> {
                Text("Battery Capacity", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enter the 'Rated Capacity' in mAh (e.g., 4500). You can find this in your phone's official specs.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = ratedMah,
                    onValueChange = { ratedMah = it.filter { char -> char.isDigit() } },
                    label = { Text("Capacity (mAh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { 
                        onComplete(deviceModel, ratedMah.toIntOrNull() ?: 4500) 
                    },
                    enabled = ratedMah.isNotEmpty()
                ) {
                    Text("Complete Setup")
                }
            }
        }
    }
}
