package jibaro.etherdrive.reserve.ui.education

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
            title = "What Low / Medium / High impact means",
            content = "Every charge is scored for how hard it was on the battery. The score combines how much charge " +
                "was added — a 20% top-up is far gentler than a 0-to-100 — with how hot the battery got while it " +
                "happened. It's a relative comparison between your own charges, not a measurement of exactly how " +
                "much capacity you lost. Most charges land in Low, and that's the point: it's there to help you " +
                "spot the unusual ones."
        )

        EducationSection(
            title = "Heat matters more than anything else",
            content = "Heat is the main driver of permanent capacity loss in lithium-ion batteries. Above about 40°C " +
                "the chemistry degrades noticeably faster. If a charge shows up as hot, the fixes are usually simple: " +
                "take the case off while charging, keep the phone out of direct sun, and don't game while it's plugged in."
        )

        EducationSection(
            title = "Why 80% is the sweet spot",
            content = "Filling the last 20% requires a higher voltage, which puts more stress on the cells. Unplugging " +
                "around 80% avoids that phase entirely and can meaningfully extend how long the battery lasts. Charging " +
                "to 100% occasionally is fine — and BatteryTruth needs one full charge now and then to measure capacity."
        )

        EducationSection(
            title = "Why capacity needs a full charge to measure",
            content = "Your phone reports how much charge is left in the battery, not how much it could hold. That figure " +
                "only equals total capacity at the very top of a charge. That's why the health estimate stays locked until " +
                "you've charged to 100% at least once, and why it gets more reliable after a few."
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
