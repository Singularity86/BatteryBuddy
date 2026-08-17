package com.batterybuddy.data.battery

import com.batterybuddy.data.analysis.ChargeMath
import com.batterybuddy.data.model.BatteryReading

data class ChargerProfile(
    val label: String,       // human-readable: "25W Super Fast", "18W QC3", "5W Standard"
    val fingerprint: String  // stable class key: "USB_PD|9000|2777|Super Fast"
)

/**
 * Turns raw charger data into a stable identity.
 *
 * This is the single source of charger fingerprints. Both the charge-session
 * path and the live-reading path must go through here — deriving fingerprints
 * separately made one physical charger appear as two entries.
 */
object ChargerClassifier {

    fun classify(info: ChargerInfo, batteryVoltageMv: Int, currentMicroAmps: Int): ChargerProfile {
        val watts = computeWatts(info, batteryVoltageMv, currentMicroAmps)
        return ChargerProfile(
            label       = buildLabel(info, watts),
            fingerprint = buildFingerprint(info, watts)
        )
    }

    /** Classifies a stored reading, so live and session paths agree on identity. */
    fun classify(reading: BatteryReading): ChargerProfile =
        classify(reading.toChargerInfo(), reading.voltageMillivolts, reading.currentMicroAmps)

    fun computeWatts(info: ChargerInfo, batteryVoltageMv: Int, currentMicroAmps: Int): Float =
        ChargeMath.chargerSideWatts(info.chargerVoltageMillivolts, info.chargerCurrentMaxMilliamps)
            ?: ChargeMath.batterySideWatts(batteryVoltageMv, currentMicroAmps)

    private fun buildLabel(info: ChargerInfo, watts: Float): String {
        val w = watts.toInt()
        val wLabel = if (w > 0) " (${w}W)" else ""
        return when {
            info.chargeProtocolLabel != null                                 -> "${info.chargeProtocolLabel}$wLabel"
            info.chargerType == "USB_PD" || info.chargerType == "PD"          -> "USB-PD$wLabel"
            info.chargerType?.contains("WIRELESS", ignoreCase = true) == true -> "Wireless$wLabel"
            (info.chargerVoltageMillivolts ?: 0) > 5500                       -> "Fast Charge$wLabel"
            w > 15                                                            -> "Fast Charger$wLabel"
            w > 0                                                             -> "Standard$wLabel"
            else                                                              -> "Unknown"
        }
    }

    // Stable string key — identifies a charger class, not a specific physical unit.
    private fun buildFingerprint(info: ChargerInfo, estimatedWatts: Float): String {
        // With no kernel identity at all, bucket by observed power so a user can
        // still tell their fast brick from their laptop port.
        if (info.chargerType == null && info.chargeProtocolLabel == null) {
            val powerBucket = when {
                estimatedWatts > 20 -> "HighPower"
                estimatedWatts > 10 -> "MedPower"
                else                -> "LowPower"
            }
            return "VIRTUAL|$powerBucket"
        }

        val type = info.chargerType ?: "GENERIC"
        val volt = info.chargerVoltageMillivolts ?: "AUTO"
        val curr = info.chargerCurrentMaxMilliamps ?: "AUTO"
        val prot = info.chargeProtocolLabel ?: "NONE"
        return "$type|$volt|$curr|$prot"
    }
}

/** The charger-side fields a reading carries, in the shape the classifier expects. */
fun BatteryReading.toChargerInfo(): ChargerInfo = ChargerInfo(
    chargerVoltageMillivolts   = chargerVoltageMillivolts,
    chargerCurrentMaxMilliamps = chargerCurrentMaxMilliamps,
    isPdActive                 = isPdActive,
    chargerType                = chargerType,
    chargeProtocolLabel        = chargeProtocolLabel
)
