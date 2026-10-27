package com.example

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Explicit notification destination. Only two fixed settings actions are accepted. */
class PowerSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = when (intent?.action) {
            Settings.ACTION_BATTERY_SAVER_SETTINGS -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            Settings.ACTION_DISPLAY_SETTINGS -> Settings.ACTION_DISPLAY_SETTINGS
            else -> null
        }
        if (action != null) {
            try {
                // A fresh intent drops all caller data, extras, flags and grant permissions.
                startActivity(Intent(action))
            } catch (_: ActivityNotFoundException) {
                unavailable()
            } catch (_: SecurityException) {
                unavailable()
            }
        }
        finish()
    }

    private fun unavailable() {
        Toast.makeText(this, "Android settings are unavailable on this device. Open them manually.", Toast.LENGTH_LONG).show()
    }
}
