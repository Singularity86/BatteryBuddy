package jibaro.etherdrive.reserve.data.model

enum class ChargeState {
    CHARGING, DISCHARGING, FULL, NOT_CHARGING;

    companion object {
        fun fromString(value: String): ChargeState =
            entries.firstOrNull { it.name == value } ?: DISCHARGING
    }
}
