package com.example.service

/** Automatic controls stay silent below critical heat. Critical heat is a safety warning. */
object ThermalSilentPolicy {
    fun mayAnnounce(userTest: Boolean, criticalHeat: Boolean = false): Boolean = userTest || criticalHeat
}
