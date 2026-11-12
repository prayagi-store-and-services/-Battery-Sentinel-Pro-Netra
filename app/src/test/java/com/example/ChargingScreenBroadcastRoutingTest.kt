package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.ui.ChargingScreenActivity
import com.example.ui.components.CHARGING_SCREEN_PREFS
import com.example.ui.components.ChargingScreenGuard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChargingScreenBroadcastRoutingTest {
    private fun prepare(mode: ChargingScreenGuard.Mode): Context {
        val c: Context = ApplicationProvider.getApplicationContext()
        c.getSharedPreferences(CHARGING_SCREEN_PREFS, Context.MODE_PRIVATE).edit().putString(ChargingScreenGuard.Mode.KEY, mode.name).commit()
        c.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
            .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING))
        return c
    }

    @Test fun screenOffWhileChargingInOnModeDoesNotFinishActivity() {
        val c = prepare(ChargingScreenGuard.Mode.ON)
        val controller = Robolectric.buildActivity(ChargingScreenActivity::class.java).create()
        val a = controller.get()
        assertFalse(a.isFinishing)
        c.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(a.isFinishing)
        controller.destroy()
    }

    @Test fun actualUnplugStillFinishesActivity() {
        val c = prepare(ChargingScreenGuard.Mode.ON)
        val controller = Robolectric.buildActivity(ChargingScreenActivity::class.java).create()
        val a = controller.get()
        c.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(a.isFinishing)
        controller.destroy()
    }
}
