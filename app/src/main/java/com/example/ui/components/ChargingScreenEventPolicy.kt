package com.example.ui.components

/** Screen broadcasts are not battery readings. Never interpret their absent battery extras as unplugging. */
object ChargingScreenEventPolicy {
    enum class Event { UNPLUGGED, BATTERY_CHANGED, SCREEN_OFF, OTHER }
    enum class Decision { KEEP, CLOSE, CLOSE_AND_LOCK_IF_ENABLED }

    fun decide(event: Event, chargingAllowed: Boolean, modeAllowsHold: Boolean, alwaysOn: Boolean): Decision =
        when (event) {
            Event.UNPLUGGED -> Decision.CLOSE_AND_LOCK_IF_ENABLED
            Event.BATTERY_CHANGED -> when {
                !chargingAllowed -> Decision.CLOSE_AND_LOCK_IF_ENABLED
                !modeAllowsHold -> Decision.CLOSE
                else -> Decision.KEEP
            }
            Event.SCREEN_OFF -> if (alwaysOn) Decision.KEEP else Decision.CLOSE
            Event.OTHER -> Decision.KEEP
        }
}
