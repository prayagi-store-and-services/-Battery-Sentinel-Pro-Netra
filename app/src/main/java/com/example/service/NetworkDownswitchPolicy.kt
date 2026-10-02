package com.example.service

enum class RadioClass { NR_5G, LTE_4G, THREE_G_OR_LOWER, UNKNOWN }

/**
 * Pure decision logic for the screen-off "switch one network level down" suggestion.
 * No Android calls here so it can be unit tested.
 */
object NetworkDownswitchPolicy {
    /** Heavy-use rule (user set 2 MB): this much mobile data inside the window means do not suggest a switch. */
    const val HEAVY_DATA_THRESHOLD_BYTES = 2L * 1024L * 1024L
    const val HEAVY_DATA_WINDOW_MS = 10L * 60L * 1000L

    sealed class Decision {
        data class Suggest(val from: RadioClass, val to: RadioClass) : Decision()
        data class Skip(val reason: String) : Decision()
    }

    fun targetFor(current: RadioClass): RadioClass? = when (current) {
        RadioClass.NR_5G -> RadioClass.LTE_4G
        RadioClass.LTE_4G -> RadioClass.THREE_G_OR_LOWER
        else -> null
    }

    /**
     * @param recentMobileBytes mobile data used inside [HEAVY_DATA_WINDOW_MS], or null when it cannot be read.
     * When usage cannot be read the rule cannot be verified, so no suggestion is made.
     */
    fun decide(current: RadioClass, recentMobileBytes: Long?): Decision {
        val target = targetFor(current) ?: return Decision.Skip("Current network type is not 5G or 4G")
        if (recentMobileBytes == null) return Decision.Skip("Mobile data use unreadable (Usage Access needed)")
        if (recentMobileBytes >= HEAVY_DATA_THRESHOLD_BYTES) return Decision.Skip("Heavy data use in progress")
        return Decision.Suggest(current, target)
    }

    // Preferred-network-mode values from AOSP RILConstants (verified against source).
    private const val MODE_WCDMA_PREF = 0
    private const val MODE_LTE_GSM_WCDMA = 9
    private const val MODE_LTE_CDMA_EVDO_GSM_WCDMA = 10
    private const val MODE_LTE_ONLY = 11
    private const val MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA = 22
    private const val MODE_NR_LTE = 24
    private const val MODE_NR_LTE_GSM_WCDMA = 26
    private const val MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA = 27
    private const val MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA = 33

    /** One level down for the experimental setting write. Unknown modes return null (never guessed). */
    fun downMode(currentMode: Int): Int? = when (currentMode) {
        MODE_NR_LTE_GSM_WCDMA -> MODE_LTE_GSM_WCDMA
        MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA -> MODE_LTE_CDMA_EVDO_GSM_WCDMA
        MODE_NR_LTE -> MODE_LTE_ONLY
        MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA -> MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
        MODE_LTE_GSM_WCDMA -> MODE_WCDMA_PREF
        else -> null
    }
}
