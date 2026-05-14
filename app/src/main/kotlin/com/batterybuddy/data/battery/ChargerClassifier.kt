package com.batterybuddy.data.battery

import kotlin.math.abs

data class ChargerProfile(
    val label: String,           // human-readable: "25W Super Fast", "18W QC3", "5W Standard"
    val negotiatedWatts: Float,  // chargerVoltage × chargerCurrentMax when available, else battery-side estimate
    val fingerprint: String      // stable class key: "USB_PD|9000|2777|Super Fast"
)

object ChargerClassifier {

    fun classify(info: ChargerInfo, batteryVoltageMv: Int, currentMicroAmps: Int): ChargerProfile {
        val watts = computeWatts(info, batteryVoltageMv, currentMicroAmps)
        val fingerprint = buildFingerprint(info, watts)
        
        return ChargerProfile(
            label       = buildLabel(info, watts),
            negotiatedWatts = watts,
            fingerprint = fingerprint
        )
    }

    private fun computeWatts(info: ChargerInfo, batteryVoltageMv: Int, currentMicroAmps: Int): Float {
        val cv = info.chargerVoltageMillivolts
        val ci = info.chargerCurrentMaxMilliamps
        return if (cv != null && ci != null && cv > 0 && ci > 0)
            cv * ci / 1_000_000f
        else
            batteryVoltageMv * abs(currentMicroAmps) / 1_000_000_000f
    }

    private fun buildLabel(info: ChargerInfo, watts: Float): String {
        val w = watts.toInt()
        val wLabel = if (w > 0) " (${w}W)" else ""
        return when {
            info.chargeProtocolLabel != null                              -> "${info.chargeProtocolLabel}$wLabel"
            info.chargerType == "USB_PD" || info.chargerType == "PD"     -> "USB-PD$wLabel"
            info.chargerType?.contains("WIRELESS", ignoreCase = true) == true -> "Wireless$wLabel"
            (info.chargerVoltageMillivolts ?: 0) > 5500                  -> "Fast Charge$wLabel"
            w > 15                                                        -> "Fast Charger$wLabel"
            w > 0                                                         -> "Standard$wLabel"
            else                                                          -> "Unknown"
        }
    }

    // Stable string key — identifies charger class, not a specific physical unit.
    private fun buildFingerprint(info: ChargerInfo, estimatedWatts: Float): String {
        // If kernel info is missing, use the wattage bracket as a fallback fingerprint.
        val type = info.chargerType ?: "GENERIC"
        val volt = info.chargerVoltageMillivolts ?: "AUTO"
        val curr = info.chargerCurrentMaxMilliamps ?: "AUTO"
        val prot = info.chargeProtocolLabel ?: "NONE"
        
        // If everything is null, we create a bucket based on the observed power.
        if (info.chargerType == null && info.chargeProtocolLabel == null) {
            val powerBucket = when {
                estimatedWatts > 20 -> "HighPower"
                estimatedWatts > 10 -> "MedPower"
                else -> "LowPower"
            }
            return "VIRTUAL|$powerBucket"
        }

        return "$type|$volt|$curr|$prot"
    }
}
