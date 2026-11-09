package com.example.service

/** Unknown resource evidence must never certify the complete Ideal State. */
internal object IdealResourcePolicy {
    fun satisfied(availableRamPercent: Float?, cpuHeadroomPercent: Float?): Boolean =
        availableRamPercent != null && availableRamPercent.isFinite() && availableRamPercent in 40f..100f &&
            cpuHeadroomPercent != null && cpuHeadroomPercent.isFinite() && cpuHeadroomPercent in 60f..100f
}
