package com.batterybuddy.ui.education

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EducationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Battery School",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        EducationSection(
            title = "What are 'Wear Units'?",
            content = "A Wear Unit measures the physical degradation of your battery's internal chemistry. A standard 500-cycle battery has roughly 500 Wear Units of 'life'. Charging from 0% to 100% at high heat might cost 1.5 units, while a cool 20% to 80% top-up might only cost 0.3 units."
        )

        EducationSection(
            title = "The Heat Factor",
            content = "Heat is the #1 enemy of lithium-ion batteries. When your battery stays above 40°C, the chemical reactions inside become unstable, causing permanent capacity loss. Keeping your phone cool while charging is the best way to extend its life."
        )

        EducationSection(
            title = "Why 80% is the Sweet Spot",
            content = "Charging the last 20% of a battery requires higher voltage, which causes more 'stress' on the cells. By unplugging at 80%, you avoid this high-stress phase, often doubling the total lifespan of the battery."
        )
    }
}

@Composable
fun EducationSection(title: String, content: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
