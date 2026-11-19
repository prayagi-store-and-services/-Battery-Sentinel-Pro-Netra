package com.example

import android.os.BatteryManager
import com.example.model.CanonicalChargingSpeed
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class EnergyCounterCentralIntegrationTest {
  @Test fun centralUsesEnergyNotSignedCurrentAndMissingEvidenceClearsCategory() = runBlocking {
    var elapsed=0L
    val center=NetraCentralDataCenter { 1_000_000L+elapsed }
    center.elapsedClock={elapsed}
    for(i in 0L..20L) {
      elapsed=i*1000
      center.processRawInput(50,100,BatteryManager.BATTERY_STATUS_CHARGING,1,300,4000,-2_000_000,null,null,
        energyCounterNwh=1_000_000_000L+i*2_500_000L)
    }
    assertEquals(CanonicalChargingSpeed.NORMAL,center.centralState.value.chargingSpeed)
    assertEquals(CanonicalChargingSpeed.NORMAL,center.centralState.value.announcementSpeed)
    assertEquals(9f,center.centralState.value.estimatedStoredEnergyRateWatts!!,0.001f)
    assertEquals(8f,center.centralState.value.powerWatts!!,0.001f)
    elapsed=21_000
    center.processRawInput(50,100,BatteryManager.BATTERY_STATUS_CHARGING,1,300,4000,20_000_000,null,null)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,center.centralState.value.chargingSpeed)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,center.centralState.value.announcementSpeed)
    assertNull(center.centralState.value.estimatedStoredEnergyRateWatts)
  }
  @Test fun energySpeedDoesNotRequireVoltageCurrentAndExpiresOnExistingFreshnessPass() = runBlocking {
    var elapsed=0L
    val center=NetraCentralDataCenter { 1_000_000L+elapsed }
    center.elapsedClock={elapsed}
    for(i in 0L..20L) {
      elapsed=i*1000
      center.processRawInput(50,100,BatteryManager.BATTERY_STATUS_CHARGING,1,300,0,Int.MIN_VALUE,null,null,
        energyCounterNwh=1_000_000_000L+i*2_500_000L)
    }
    assertEquals(CanonicalChargingSpeed.NORMAL,center.centralState.value.chargingSpeed)
    assertNull(center.centralState.value.powerWatts)
    elapsed=31_001
    center.expireTelemetryFreshness()
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,center.centralState.value.chargingSpeed)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,center.centralState.value.announcementSpeed)
    assertNull(center.centralState.value.estimatedStoredEnergyRateWatts)
  }
  @Test fun fullAndNewProcessNeverRetainCategory() = runBlocking {
    val center=NetraCentralDataCenter()
    center.elapsedClock={10_000}
    center.processRawInput(100,100,BatteryManager.BATTERY_STATUS_FULL,1,300,4000,100_000,null,null,
      energyCounterNwh=2_000_000_000)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,center.centralState.value.chargingSpeed)
    assertNull(center.centralState.value.estimatedStoredEnergyRateWatts)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,NetraCentralDataCenter().centralState.value.chargingSpeed)
  }
}
