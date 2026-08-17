package com.batterybuddy.data.battery

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ChargerInfo(
    val chargerVoltageMillivolts: Int?,    // sysfs usb/voltage_now ÷ 1000
    val chargerCurrentMaxMilliamps: Int?,  // sysfs usb/current_max ÷ 1000
    val isPdActive: Boolean?,              // sysfs usb/pd_active == "1"
    val chargerType: String?,              // sysfs usb/type e.g. "USB_PD", "USB_DCP"
    val chargeProtocolLabel: String?       // sysfs battery/charge_type e.g. "Super Fast"
)

@Singleton
class ChargerInfoReader @Inject constructor() {

    // Multiple paths per field — kernel naming varies by OEM and Android version.
    // firstNotNullOfOrNull returns the first readable result; all others are skipped.

    private val voltagePaths = listOf(
        "/sys/class/power_supply/usb/voltage_now",
        "/sys/class/power_supply/USB/voltage_now"
    )
    private val currentMaxPaths = listOf(
        "/sys/class/power_supply/usb/current_max",
        "/sys/class/power_supply/USB/current_max"
    )
    private val pdActivePaths = listOf(
        "/sys/class/power_supply/usb/pd_active"
    )
    private val typePaths = listOf(
        "/sys/class/power_supply/usb/real_type",  // preferred — some OEMs expose cleaner value here
        "/sys/class/power_supply/usb/type"
    )
    private val protocolPaths = listOf(
        "/sys/class/power_supply/battery/charge_type"
    )

    fun read(): ChargerInfo = ChargerInfo(
        chargerVoltageMillivolts   = readInt(voltagePaths)?.let { it / 1000 },
        chargerCurrentMaxMilliamps = readInt(currentMaxPaths)?.let { it / 1000 },
        // null means "the kernel didn't tell us", which is not the same as "not PD".
        isPdActive                 = readString(pdActivePaths)?.trim()?.let { it == "1" },
        chargerType                = readString(typePaths)?.trim()
            ?.takeIf { it.isNotEmpty() && it != "Unknown" },
        chargeProtocolLabel        = readString(protocolPaths)?.trim()
            ?.takeIf { it.isNotEmpty() && it != "N/A" && it != "None" }
    )

    private fun readInt(paths: List<String>): Int? =
        paths.firstNotNullOfOrNull { path ->
            try { File(path).readText().trim().toIntOrNull() } catch (_: Exception) { null }
        }

    private fun readString(paths: List<String>): String? =
        paths.firstNotNullOfOrNull { path ->
            try { File(path).readText().takeIf { it.isNotBlank() } } catch (_: Exception) { null }
        }
}
