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
        return ChargerProfile(
            label       = buildLabel(info, watts),
            negotiatedWatts = watts,
            fingerprint = buildFingerprint(info)
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
            w > 0                                                         -> "Standard$wLabel"
            else                                                          -> "Unknown"
        }
    }

    // Stable string key — identifies charger class, not a specific physical unit.
    // Two identical 25W Samsung chargers produce the same fingerprint.
    private fun buildFingerprint(info: ChargerInfo): String =
        "${info.chargerType ?: "null"}|" +
        "${info.chargerVoltageMillivolts ?: "null"}|" +
        "${info.chargerCurrentMaxMilliamps ?: "null"}|" +
        "${info.chargeProtocolLabel ?: "null"}"
}
