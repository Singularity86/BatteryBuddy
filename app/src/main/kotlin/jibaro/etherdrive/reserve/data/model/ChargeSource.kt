package jibaro.etherdrive.reserve.data.model

enum class ChargeSource {
    USB, WIRELESS, NONE;

    companion object {
        fun fromString(value: String): ChargeSource =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}
