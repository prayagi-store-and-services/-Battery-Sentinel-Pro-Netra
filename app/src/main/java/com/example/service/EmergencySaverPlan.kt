package com.example.service

/**
 * Emergency Saver planner: the 30%-for-6-hours plan, pure logic.
 *
 * Honest model: an app cannot force-stop other apps, switch Battery Saver, toggle
 * airplane mode, location, hotspot, dark mode, refresh rate or haptics by itself on a
 * normal phone. So every step is labelled truthfully as either "applied in the app"
 * (brightness and screen timeout, only with the Modify-system-settings switch the user
 * granted) or "you do this in Settings - tap to open the right screen". The per-hour
 * budget is a target computed from the numbers given, never a promise: real drain
 * depends on the phone, the network and what runs.
 */
object EmergencySaverPlan {

    enum class Brand(val label: String) { REALME("realme"), SAMSUNG("Samsung"), REDMI_XIAOMI("Redmi/Xiaomi"), VIVO("vivo"), OPPO_ONEPLUS("OPPO/OnePlus"), OTHER("Other") }
    enum class NetworkNeed(val label: String) { CALLS_SMS("Calls + SMS only"), MOBILE_DATA("Mobile data needed"), NONE("No network") }
    enum class Usage(val label: String) { ESSENTIAL("Essential only"), NORMAL("Normal") }

    enum class StepAction { APPLY_IN_APP, OPEN_SETTINGS, GUIDE_ONLY }

    data class PlanStep(
        val title: String,
        val detail: String,
        val action: StepAction,
        /** android.provider.Settings action string to open, when action == OPEN_SETTINGS */
        val settingsAction: String? = null
    )

    data class Budget(
        val startPercent: Int,
        val hours: Int,
        val reservePercent: Int,
        val usablePercent: Int,
        val targetPerHour: Float
    )

    const val DEFAULT_RESERVE_PERCENT = 10

    fun budget(startPercent: Int, hours: Int, reservePercent: Int = DEFAULT_RESERVE_PERCENT): Budget {
        val usable = (startPercent - reservePercent).coerceAtLeast(0)
        val perHour = if (hours > 0) usable.toFloat() / hours else 0f
        return Budget(startPercent, hours, reservePercent, usable, perHour)
    }

    fun batterySaverPath(brand: Brand): String = when (brand) {
        Brand.REALME -> "Settings > Battery > Power saving mode"
        Brand.SAMSUNG -> "Settings > Battery and device care > Battery > Power saving"
        Brand.REDMI_XIAOMI -> "Settings > Battery > Battery saver"
        Brand.VIVO -> "Settings > Battery > Power saving"
        Brand.OPPO_ONEPLUS -> "Settings > Battery > Power saving mode"
        Brand.OTHER -> "Settings > Battery > Battery Saver (name varies by phone)"
    }

    fun refreshRatePath(brand: Brand): String = when (brand) {
        Brand.REALME -> "Settings > Display & brightness > Screen refresh rate > 60Hz (Standard)"
        Brand.SAMSUNG -> "Settings > Display > Motion smoothness > Standard (60Hz)"
        Brand.REDMI_XIAOMI -> "Settings > Display > Refresh rate > 60Hz"
        Brand.VIVO -> "Settings > Display & brightness > Screen refresh rate > 60Hz"
        Brand.OPPO_ONEPLUS -> "Settings > Display & brightness > Screen refresh rate > Standard (60Hz)"
        Brand.OTHER -> "Settings > Display > Refresh rate > 60Hz (name varies by phone)"
    }

    fun darkModePath(brand: Brand): String = when (brand) {
        Brand.SAMSUNG -> "Settings > Display > Dark"
        Brand.REDMI_XIAOMI -> "Settings > Display > Dark mode"
        else -> "Settings > Display > Dark theme / Dark mode"
    }

    /**
     * The ordered plan. Steps the app can do itself come first and are marked APPLY_IN_APP;
     * everything else opens the exact Settings screen or is a guided instruction.
     * [canWriteSettings] decides whether brightness/timeout apply in-app or become guided.
     */
    fun plan(
        brand: Brand,
        network: NetworkNeed,
        usage: Usage,
        canWriteSettings: Boolean,
        heavyApps: List<String> = emptyList()
    ): List<PlanStep> {
        val steps = ArrayList<PlanStep>()

        // 1. Battery Saver - Android allows no direct toggle; open the battery screen.
        steps += PlanStep(
            title = "Turn Battery Saver ON",
            detail = batterySaverPath(brand) + ". The button below opens your Battery settings.",
            action = StepAction.OPEN_SETTINGS,
            settingsAction = "android.intent.action.POWER_USAGE_SUMMARY"
        )

        // 2. Airplane mode only when no network is needed.
        if (network == NetworkNeed.NONE) {
            steps += PlanStep(
                title = "Airplane mode ON",
                detail = "No network needed, so radios can sleep. Settings > Network & internet > Airplane mode.",
                action = StepAction.OPEN_SETTINGS,
                settingsAction = "android.settings.AIRPLANE_MODE_SETTINGS"
            )
        } else {
            steps += PlanStep(
                title = "Keep network lean",
                detail = if (network == NetworkNeed.CALLS_SMS)
                    "Calls and SMS work without data: turn mobile data OFF (Settings > Network & internet > SIMs > Mobile data off). Prefer Wi-Fi for messaging apps."
                else
                    "Mobile data stays on. Prefer Wi-Fi when it is nearby; 5G drains more, so 4G is the calmer choice while saving.",
                action = StepAction.OPEN_SETTINGS,
                settingsAction = "android.settings.AIRPLANE_MODE_SETTINGS"
            )
        }

        // 3. Brightness + screen timeout - the only steps the app can apply itself.
        steps += if (canWriteSettings) {
            PlanStep(
                title = "Brightness 25% + screen timeout 15s",
                detail = "Applied by the app when you tap Apply. Tap Restore in the Thermal Guardian card later to undo.",
                action = StepAction.APPLY_IN_APP
            )
        } else {
            PlanStep(
                title = "Brightness 10-25% + screen timeout 15-30s",
                detail = "Grant the app's Modify system settings switch once and the app can do this for you; or set it in Settings > Display. Always-on display OFF on OLED screens.",
                action = StepAction.OPEN_SETTINGS,
                settingsAction = "android.settings.DISPLAY_SETTINGS"
            )
        }

        // 4. Location.
        steps += PlanStep(
            title = "Location OFF (if not needed)",
            detail = "Settings > Location > off. Maps and safety apps need it - keep it on if you do.",
            action = StepAction.OPEN_SETTINGS,
            settingsAction = "android.settings.LOCATION_SOURCE_SETTINGS"
        )

        // 5. Bluetooth + hotspot.
        steps += PlanStep(
            title = "Bluetooth and hotspot OFF",
            detail = "Settings > Bluetooth > off. Hotspot: Settings > Network & internet > Hotspot & tethering > off.",
            action = StepAction.OPEN_SETTINGS,
            settingsAction = "android.settings.BLUETOOTH_SETTINGS"
        )

        // 6. Dark mode (OLED only matters).
        steps += PlanStep(
            title = "Dark mode ON (helps OLED screens)",
            detail = darkModePath(brand) + ". Saves real power only on OLED/AMOLED screens.",
            action = StepAction.OPEN_SETTINGS,
            settingsAction = "android.settings.DISPLAY_SETTINGS"
        )

        // 7. Heavy apps background restricted - guided, never calls/messages/security apps.
        val appsLine = if (heavyApps.isEmpty())
            "Open Settings > Battery > Battery usage, tap the top apps, and set them to Restricted."
        else
            "Recently heavy here: ${heavyApps.joinToString(", ")}. Restrict them from Settings > Battery > Battery usage."
        steps += PlanStep(
            title = "Restrict heavy apps in background",
            detail = "$appsLine Never restrict Phone, Messages, WhatsApp (if you need it) or security apps.",
            action = StepAction.OPEN_SETTINGS,
            settingsAction = "android.intent.action.POWER_USAGE_SUMMARY"
        )

        // 8. Refresh rate + haptics.
        steps += PlanStep(
            title = "60Hz + haptics OFF",
            detail = refreshRatePath(brand) + ". Haptics: Settings > Sound & vibration > vibration/haptics off.",
            action = StepAction.OPEN_SETTINGS,
            settingsAction = "android.settings.DISPLAY_SETTINGS"
        )

        if (usage == Usage.ESSENTIAL) {
            steps += PlanStep(
                title = "Essential-only usage",
                detail = "WhatsApp/calls only when needed. No YouTube, Reels, gaming or camera. Lock the screen the moment you finish.",
                action = StepAction.GUIDE_ONLY
            )
        }

        return steps
    }

    val usageGuide: List<String> = listOf(
        "Use WhatsApp only when needed; every screen-on minute costs the most.",
        "No YouTube, Reels, gaming or camera until you reach a charger.",
        "Prefer Wi-Fi over mobile data when it is available.",
        "Postpone big downloads and app updates.",
        "Lock the screen immediately after every use."
    )

    val heatSafetyTips: List<String> = listOf(
        "At 40C: move the phone to shade, stop heavy work, and take it off a bed or any warm surface.",
        "Never put the phone in a fridge or on ice - condensation damages it.",
        "Charging while hot is slower on purpose; that is the phone protecting itself.",
        "Do not ignore thermal warnings; the phone's own safety can shut it down."
    )
}
