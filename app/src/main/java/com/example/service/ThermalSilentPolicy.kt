package com.example.service

/**
 * Thermal Guardian runs on its own and does not announce itself. Automatic runs never speak
 * and never post a notification; only a run the user started with the TEST button may speak.
 */
object ThermalSilentPolicy {
    fun mayAnnounce(userTest: Boolean): Boolean = userTest
}
