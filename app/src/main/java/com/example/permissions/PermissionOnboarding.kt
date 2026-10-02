package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/** Permissions the app can ask about with an explanation first. Order = order of popups. */
enum class Perm(val key: String) {
    NOTIFICATIONS("notifications"),
    BLUETOOTH("bluetooth"),
    BATTERY_OPT("battery_opt"),
    WRITE_SETTINGS("write_settings"),
    LOCATION("location")
}

enum class Choice { NONE, SKIPPED, REMIND_LATER, NEVER }

data class Saved(val choice: Choice = Choice.NONE, val skippedAtLaunch: Int = 0, val remindAtLaunch: Int = 0)

enum class Prompt { NONE, FIRST, REASK }

/** Pure rules, unit tested. A granted permission is never asked again; "never" is permanent. */
internal object PermissionPromptPolicy {
    const val REMIND_AFTER_LAUNCHES = 3

    fun decide(granted: Boolean, relevant: Boolean, saved: Saved, launch: Int): Prompt = when {
        granted || !relevant -> Prompt.NONE
        saved.choice == Choice.NEVER -> Prompt.NONE
        saved.choice == Choice.NONE -> Prompt.FIRST
        saved.choice == Choice.SKIPPED -> if (launch > saved.skippedAtLaunch) Prompt.REASK else Prompt.NONE
        else -> if (launch >= saved.remindAtLaunch) Prompt.REASK else Prompt.NONE
    }
}

/** Real checks against Android; nothing is assumed granted. */
object PermissionCheck {
    fun isGranted(c: Context, p: Perm): Boolean = when (p) {
        Perm.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS)
        Perm.BLUETOOTH ->
            Build.VERSION.SDK_INT < 31 || has(c, Manifest.permission.BLUETOOTH_CONNECT)
        Perm.BATTERY_OPT ->
            (c.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(c.packageName) == true
        Perm.WRITE_SETTINGS -> Settings.System.canWrite(c)
        Perm.LOCATION -> has(c, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /** Only core permissions, or ones tied to a feature the user switched on. */
    fun isRelevant(c: Context, p: Perm): Boolean = when (p) {
        Perm.WRITE_SETTINGS ->
            c.getSharedPreferences("netra_sentinel_prefs", Context.MODE_PRIVATE).getBoolean("screen_off_saver_enabled", false)
        else -> true
    }

    /** Runtime permission string, or null when the permission is a special system page. */
    fun runtimePermission(p: Perm): String? = when (p) {
        Perm.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        Perm.BLUETOOTH -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
        Perm.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        else -> null
    }

    /** The exact system page for special permissions. The user grants it there, never the app. */
    fun settingsIntent(c: Context, p: Perm): Intent? {
        val pkg = Uri.parse("package:" + c.packageName)
        return when (p) {
            Perm.BATTERY_OPT -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Perm.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            else -> null
        }
    }

    fun appDetailsIntent(c: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))

    private fun has(c: Context, perm: String) =
        ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED
}

/** Saved choices (this phone only) and a launch counter. */
class PermissionPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("netra_permission_onboarding", Context.MODE_PRIVATE)

    fun launchCount(): Int = sp.getInt("launches", 0)

    /** Counts once per app process start. */
    fun countLaunchOnce(): Int {
        if (counted.compareAndSet(false, true)) sp.edit().putInt("launches", launchCount() + 1).apply()
        return launchCount()
    }

    fun get(p: Perm): Saved = Saved(
        runCatching { Choice.valueOf(sp.getString(p.key + "_choice", Choice.NONE.name) ?: Choice.NONE.name) }.getOrDefault(Choice.NONE),
        sp.getInt(p.key + "_skipped_at", 0),
        sp.getInt(p.key + "_remind_at", 0)
    )

    fun set(p: Perm, choice: Choice, launch: Int) {
        sp.edit()
            .putString(p.key + "_choice", choice.name)
            .putInt(p.key + "_skipped_at", launch)
            .putInt(p.key + "_remind_at", launch + PermissionPromptPolicy.REMIND_AFTER_LAUNCHES)
            .apply()
    }

    private companion object { val counted = AtomicBoolean(false) }
}

/** Hinglish copy. Says honestly that Android, not the app, asks for the permission. */
object PermissionCopy {
    fun title(p: Perm) = when (p) {
        Perm.NOTIFICATIONS -> "Notifications allow karein"
        Perm.BLUETOOTH -> "Bluetooth devices dekhne ki permission"
        Perm.BATTERY_OPT -> "Battery optimization se chhoot"
        Perm.WRITE_SETTINGS -> "Brightness control ki permission"
        Perm.LOCATION -> "Location permission"
    }

    fun benefit(p: Perm) = when (p) {
        Perm.NOTIFICATIONS -> "Battery alerts aur announcements notification mein dikhane ke liye."
        Perm.BLUETOOTH -> "Connected Bluetooth devices aur unki battery dikhane ke liye (jahan device batata hai)."
        Perm.BATTERY_OPT -> "Taaki Android background mein monitoring ko band na kare. Battery ki readings zyada lagataar milengi."
        Perm.WRITE_SETTINGS -> "Charging + screen off savings mein brightness kam aur wapas karne ke liye. Aapne ye feature on kiya hai."
        Perm.LOCATION -> "Kuch phones par Android Wi-Fi/Bluetooth ki jankari ke liye location permission maangta hai. App aapki location save ya bhejti nahi."
    }

    const val HOW = "Approve dabane par Android ka apna permission screen khulega. Allow aap wahan khud karenge - app khud kuch allow nahi karti."
    const val REASK_TITLE = "Aapne pehle skip kiya tha - ab kya karna chahenge?"
}
