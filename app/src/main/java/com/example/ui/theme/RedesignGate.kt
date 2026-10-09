package com.example.ui.theme

import java.util.Calendar
import java.util.GregorianCalendar

/** The new look switches on at 11 Oct 2026 00:00 device-local time. Device clock only, no network. API 24 safe (no java.time). */
object RedesignGate {
    val SWITCH_AT_MILLIS: Long = GregorianCalendar(2026, Calendar.OCTOBER, 11, 0, 0, 0).timeInMillis
    fun isOn(nowMillis: Long): Boolean = nowMillis >= SWITCH_AT_MILLIS
    fun isOn(): Boolean = isOn(System.currentTimeMillis())
}
