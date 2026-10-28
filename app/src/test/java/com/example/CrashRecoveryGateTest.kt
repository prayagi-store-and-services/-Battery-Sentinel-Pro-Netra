package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class CrashRecoveryGateTest {
    private val c:Context=ApplicationProvider.getApplicationContext()
    @Before fun clear(){c.getSharedPreferences("netra_crash_recovery",0).edit().clear().commit()}
    @Test fun ordinaryStartsAndOsDeathsDoNotIncrementCrashes() {
        repeat(50){assertTrue(CrashRecoveryGate.allowed(c,1000))}
        assertEquals(0,c.getSharedPreferences("netra_crash_recovery",0).getInt("count",0))
    }
    @Test fun persistedPauseStickyCheckAndExplicitRetry() {
        repeat(3){CrashRecoveryGate.recordCrash(c,1000L+it)}
        assertFalse(CrashRecoveryGate.allowed(c,2000));assertTrue(CrashRecoveryGate.pauseUntil.value>0)
        assertEquals(3,c.getSharedPreferences("netra_crash_recovery",0).getInt("count",0))
        assertTrue(CrashRecoveryGate.retry(c));assertTrue(CrashRecoveryGate.allowed(c,2000))
    }
    @Test fun pauseExpiresWithoutTimerAndRebasesClock() {
        repeat(3){CrashRecoveryGate.recordCrash(c,1000L+it)}
        assertTrue(CrashRecoveryGate.allowed(c,121002));assertEquals(0L,CrashRecoveryGate.pauseUntil.value)
        repeat(3){CrashRecoveryGate.recordCrash(c,999999L+it)}
        assertTrue(CrashRecoveryGate.allowed(c,1000))
    }
    @Test fun pausedStickyServicesStopWithoutCollectorsAndTeardownSafely() {
        val now=System.currentTimeMillis()
        repeat(3){CrashRecoveryGate.recordCrash(c,now+it)}
        val monitor=org.robolectric.Robolectric.buildService(BatteryMonitorService::class.java).create()
        assertNotNull(org.robolectric.Shadows.shadowOf(monitor.get()).lastForegroundNotification)
        assertTrue(org.robolectric.Shadows.shadowOf(monitor.get()).isStoppedBySelf)
        assertEquals(android.app.Service.START_NOT_STICKY,monitor.get().onStartCommand(null,0,1))
        val monitorShadow=org.robolectric.Shadows.shadowOf(monitor.get())
        assertNotNull(monitorShadow.lastForegroundNotification)
        assertTrue(monitorShadow.isStoppedBySelf)
        assertTrue(monitorShadow.lastForegroundNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("paused"))
        monitor.destroy()
        val tracker=org.robolectric.Robolectric.buildService(BatteryForegroundService::class.java).create()
        assertNotNull(org.robolectric.Shadows.shadowOf(tracker.get()).lastForegroundNotification)
        assertTrue(org.robolectric.Shadows.shadowOf(tracker.get()).isStoppedBySelf)
        assertEquals(android.app.Service.START_NOT_STICKY,tracker.get().onStartCommand(null,0,1))
        val trackerShadow=org.robolectric.Shadows.shadowOf(tracker.get())
        assertNotNull(trackerShadow.lastForegroundNotification)
        assertTrue(trackerShadow.isStoppedBySelf)
        tracker.destroy()
    }
    @Test fun pausedForegroundPromotionPrecedesStop() {
        val order=mutableListOf<String>()
        PausedServiceStop.inOrder({order.add("foreground")},{order.add("stop")})
        assertEquals(listOf("foreground","stop"),order)
    }
    @Test fun pausedPromotionFailureStillStopsWithoutRethrowing() {
        val order=mutableListOf<String>()
        PausedServiceStop.inOrder({order.add("foreground");throw AssertionError("test refusal")},{order.add("stop")})
        assertEquals(listOf("foreground","stop"),order)
    }
    @Test fun foregroundRefusalDoesNotRetryOrdinaryServiceStart() {
        var ordinary=0;var foreground=0
        val rejecting=object:android.content.ContextWrapper(c){
            override fun startForegroundService(intent:android.content.Intent):android.content.ComponentName? {
                foreground++;throw IllegalStateException("test refusal")
            }
            override fun startService(intent:android.content.Intent):android.content.ComponentName? {
                ordinary++;return null
            }
        }
        BatteryMonitorService.startService(rejecting)
        BatteryForegroundService.startService(rejecting)
        assertEquals(2,foreground);assertEquals(0,ordinary)
        assertTrue(CrashRecoveryGate.startFailures.value.containsAll(listOf("battery monitor","battery tracker")))
        assertEquals(0,c.getSharedPreferences("netra_crash_recovery",0).getInt("count",0))
    }
    @Test fun refusedStartsAreVisibleButNotCrashes() {
        CrashRecoveryGate.startResult("battery monitor",false)
        assertTrue(CrashRecoveryGate.startFailures.value.contains("battery monitor"))
        assertTrue(CrashRecoveryGate.allowed(c,2000))
        CrashRecoveryGate.startResult("battery tracker",true)
        assertTrue(CrashRecoveryGate.startFailures.value.contains("battery monitor"))
        CrashRecoveryGate.startResult("battery monitor",true)
        assertFalse(CrashRecoveryGate.startFailures.value.contains("battery monitor"))
    }
}
