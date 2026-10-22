package com.example.service

/**
 * Silent (Night Protection) window rule for the charge-target and continuous charge alerts.
 * Inside the window the full-charge alert makes no sound at all: no spoken line, no repeat, no ringtone loop, no
 * vibration. A silent notification may still be shown. Outside the window nothing changes.
 */
object FullChargeSilence {
    /** True when spoken lines, the ringtone loop and vibration are allowed. */
    fun soundAllowed(nightProtectionActive: Boolean): Boolean = !nightProtectionActive
}
