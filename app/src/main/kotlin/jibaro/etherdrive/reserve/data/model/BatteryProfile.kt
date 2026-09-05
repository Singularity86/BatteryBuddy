package jibaro.etherdrive.reserve.data.model

/** A physical battery the user can swap in and out of the device. */
data class BatteryProfile(
    val id: Long,
    val label: String,
    val ratedMah: Int,
    val createdAt: Long
)
