package com.example

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.service.ChargeCurveLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChargeCurveLearnerTest {

    private lateinit var context: Context
    private lateinit var prefsName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefsName = "curve_test_" + System.nanoTime()
        ChargeCurveLearner.resetForTesting(context, prefsName)
    }

    /** Feeds real level transitions, one percent at a time, [passes] times so each level is observed twice. */
    private fun feedCharging(from: Int, to: Int, secPerPct: Long, temp: Float, passes: Int = 2) {
        var t = 1_000_000L
        repeat(passes) {
            ChargeCurveLearner.observeTransition(from, BatteryManager.BATTERY_STATUS_CHARGING, 1, temp, t)
            for (l in (from + 1)..to) {
                t += secPerPct * 1000
                ChargeCurveLearner.observeTransition(l, BatteryManager.BATTERY_STATUS_CHARGING, 1, temp, t)
            }
        }
    }

    private fun feedDischarging(from: Int, to: Int, secPerPct: Long, temp: Float, passes: Int = 2) {
        var t = 1_000_000L
        repeat(passes) {
            ChargeCurveLearner.observeTransition(from, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, temp, t)
            for (l in (from - 1) downTo to) {
                t += secPerPct * 1000
                ChargeCurveLearner.observeTransition(l, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, temp, t)
            }
        }
    }

    @Test
    fun taperLearnedFromPhoneProducesLongerEtaThanLinear() {
        // Fast 30s per percent up to 80, then the phone's real 180s per percent taper above 80.
        var t = 1_000_000L
        repeat(2) {
            ChargeCurveLearner.observeTransition(50, BatteryManager.BATTERY_STATUS_CHARGING, 1, 25f, t)
            for (l in 51..100) {
                t += (if (l <= 80) 30_000L else 180_000L)
                ChargeCurveLearner.observeTransition(l, BatteryManager.BATTERY_STATUS_CHARGING, 1, 25f, t)
            }
        }
        val eta = ChargeCurveLearner.estimateMinutes(80, 100, true, 25f)
        assertNotNull(eta)
        // Learned: 20 levels x 180s = 60 min. A linear guess from the fast part would say 10 min.
        assertEquals(60, eta!!.minutes)
        assertTrue(eta.confident)
    }

    @Test
    fun temperatureBandsStaySeparate() {
        feedCharging(50, 60, 30, 25f)
        assertNotNull(ChargeCurveLearner.estimateMinutes(50, 60, true, 25f))
        // Nothing learned in the hot band, so hot must not borrow cool numbers.
        assertNull(ChargeCurveLearner.estimateMinutes(50, 60, true, 41f))
    }

    @Test
    fun unknownTemperatureUsesBestKnownBand() {
        feedCharging(50, 60, 30, 25f)
        assertNotNull(ChargeCurveLearner.estimateMinutes(50, 60, true, null))
    }

    @Test
    fun partialCoverageFallsBackToBandAverageAndStaysNotConfident() {
        feedCharging(50, 52, 30, 25f)
        val eta = ChargeCurveLearner.estimateMinutes(40, 60, true, 25f)
        assertNotNull(eta)
        // Only 2 of the 20 summed levels have their own data.
        assertFalse(eta!!.confident)
        assertEquals(10, eta.minutes) // 20 levels x 30s band average
    }

    @Test
    fun dischargingCurveIsLearnedSeparately() {
        feedDischarging(80, 50, 120, 33f)
        val eta = ChargeCurveLearner.estimateMinutes(70, 30, false, 33f)
        assertNotNull(eta)
        assertEquals(80, eta!!.minutes) // 40 levels x 120s
        // Discharge learning must not invent a charge curve.
        assertNull(ChargeCurveLearner.estimateMinutes(50, 80, true, 33f))
    }

    @Test
    fun learnedCurveSurvivesReload() {
        feedCharging(50, 60, 30, 25f)
        ChargeCurveLearner.saveNow()
        ChargeCurveLearner.resetForTesting(context, prefsName)
        val eta = ChargeCurveLearner.estimateMinutes(50, 60, true, 25f)
        assertNotNull(eta)
        assertEquals(5, eta!!.minutes) // 10 levels x 30s
        assertTrue(eta.confident)
    }

    @Test
    fun invalidAndMixedInputTeachesNothing() {
        ChargeCurveLearner.observeTransition(null, BatteryManager.BATTERY_STATUS_CHARGING, 1, 25f, 1000L)
        ChargeCurveLearner.observeTransition(50, BatteryManager.BATTERY_STATUS_NOT_CHARGING, 1, 25f, 2000L)
        ChargeCurveLearner.observeTransition(60, BatteryManager.BATTERY_STATUS_CHARGING, 1, null, 3000L)
        assertNull(ChargeCurveLearner.estimateMinutes(50, 60, true, 25f))
    }
}
