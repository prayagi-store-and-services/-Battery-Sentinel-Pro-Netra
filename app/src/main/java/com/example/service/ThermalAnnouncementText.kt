package com.example.service

import java.util.Locale

/**
 * The exact lines spoken for thermal events. The number is the BATTERY temperature sensor
 * reading - never the phone's internal or CPU temperature - and the app cannot cool the
 * phone, so no line may claim cooling. Keep every thermal announcement here so the wording
 * stays honest in one place.
 */
internal object ThermalAnnouncementText {
    fun warning(tempC: Float): String =
        "Battery temperature high, ${format(tempC)} degrees."

    fun critical(tempC: Float): String =
        "Battery temperature critical, ${format(tempC)} degrees."

    const val PROTECTION_ON: String = "Thermal protection on."

    private fun format(tempC: Float): String = String.format(Locale.US, "%.1f", tempC)
}
