package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import android.os.SystemClock
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
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ChargingScreenNightIntegrationTest {
    @org.junit.After fun restoreClock() {
        com.example.ui.components.ChargingScreenVisibility.wallClock = { System.currentTimeMillis() }
        com.example.ui.components.ChargingScreenVisibility.elapsedClock = { SystemClock.elapsedRealtime() }
        com.example.ui.components.ChargingScreenVisibility.stateSource = { com.example.ui.components.ChargingScreenVisibility.currentState() }
        com.example.ui.components.ChargingScreenVisibility.reset()
    }
    private fun hour(h:Int) {
        val calendar=Calendar.getInstance().apply { set(2026,10,12,h,0,0);set(Calendar.MILLISECOND,0) }
        com.example.ui.components.ChargingScreenVisibility.wallClock = { calendar.timeInMillis }
    }
    private fun prepare():Context {
        com.example.ui.components.ChargingScreenVisibility.reset()
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences(CHARGING_SCREEN_PREFS,Context.MODE_PRIVATE).edit().putString(ChargingScreenGuard.Mode.KEY,"ON").commit()
        c.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_PLUGGED,BatteryManager.BATTERY_PLUGGED_AC)
            .putExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_CHARGING))
        return c
    }
    @Test fun night23PreventsAlwaysOnActivity() {
        hour(23);prepare();val controller=Robolectric.buildActivity(ChargingScreenActivity::class.java).create()
        assertTrue(controller.get().isFinishing);controller.destroy()
    }
    @Test fun before6PreventsAlwaysOnActivity() {
        hour(5);prepare();val controller=Robolectric.buildActivity(ChargingScreenActivity::class.java).create()
        assertTrue(controller.get().isFinishing);controller.destroy()
    }
    @Test fun dayActivityRemainsOpen() {
        hour(6);prepare();val controller=Robolectric.buildActivity(ChargingScreenActivity::class.java).create()
        assertFalse(controller.get().isFinishing);controller.destroy()
    }
    @Test fun timeChangeIntoNightClosesAlreadyOpenActivity() {
        hour(22);val c=prepare();val controller=Robolectric.buildActivity(ChargingScreenActivity::class.java).create()
        assertFalse(controller.get().isFinishing)
        hour(23);c.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED));shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing);controller.destroy()
    }
    @Test fun confirmedSlowDurationClosesActivityWithoutForcedLock() {
        hour(12);val c=prepare()
        val wall=com.example.ui.components.ChargingScreenVisibility.wallClock()
        var elapsed=100L
        com.example.ui.components.ChargingScreenVisibility.elapsedClock={elapsed}
        fun sample(at:Long)=com.example.model.NetraCentralState(isCharging=true,isChargerConnected=true,
            chargerConnectedAt=wall-120_000,isDataFresh=true,lastUpdateTimestamp=at,
            chargingSpeed=com.example.model.CanonicalChargingSpeed.SLOW,
            fieldStates=com.example.model.TelemetryFieldState(powerStatus=com.example.model.FieldStatus.LIVE,powerObservedAt=at))
        var live=sample(wall-60_000)
        com.example.ui.components.ChargingScreenVisibility.stateSource={live}
        assertTrue(com.example.ui.components.ChargingScreenVisibility.allowed())
        val controller=Robolectric.buildActivity(ChargingScreenActivity::class.java).create()
        assertFalse(controller.get().isFinishing)
        elapsed+=60_000
        live=sample(wall)
        assertFalse(com.example.ui.components.ChargingScreenVisibility.allowed())
        c.sendBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_PLUGGED,BatteryManager.BATTERY_PLUGGED_AC)
            .putExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_CHARGING))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
        controller.destroy()
    }

}
