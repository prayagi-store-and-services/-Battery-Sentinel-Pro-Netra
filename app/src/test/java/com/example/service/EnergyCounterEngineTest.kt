package com.example.service

import com.example.model.CanonicalChargingSpeed
import org.junit.Assert.*
import org.junit.Test

class EnergyCounterEngineTest {
  @Test fun allApprovedBandsComeOnlyFromStableEnergyObservations() {
    val cases=listOf(4.9 to CanonicalChargingSpeed.SLOW,5.0 to CanonicalChargingSpeed.NORMAL,
      9.9 to CanonicalChargingSpeed.NORMAL,10.0 to CanonicalChargingSpeed.FAST,19.9 to CanonicalChargingSpeed.FAST,
      20.0 to CanonicalChargingSpeed.ULTRA_FAST,49.9 to CanonicalChargingSpeed.ULTRA_FAST,
      50.0 to CanonicalChargingSpeed.HYPER_FAST,99.9 to CanonicalChargingSpeed.HYPER_FAST,100.0 to CanonicalChargingSpeed.EXTREME_FAST)
    for((watts,expected) in cases) {
      val engine=ChargingSpeedEngine()
      var result:SpeedEngineResult?=null
      // 3.6 seconds makes every synthetic energy delta exact in integer nWh.
      for(i in 0L..7L) result=engine.calculate(true,null,null,energyCounterNwh=1_000_000_000L+(watts*1_000_000.0*i).toLong(),observedAtElapsedMillis=i*3600)
      assertEquals("$watts",expected,result!!.speedCategory)
      assertEquals(expected,result.announcementCategory)
      assertEquals(watts.toFloat(),result.estimatedStoredEnergyRateWatts!!,0.0001f)
      assertNull(result.rawPowerWatts)
    }
  }
  @Test fun speedNeverUsesVoltageCurrentOrOldCategory() {
    val e=ChargingSpeedEngine()
    for(i in 0L..20L)e.calculate(true,4000,2000,energyCounterNwh=1_000_000_000L+i*2_500_000,observedAtElapsedMillis=i*1000)
    val unavailable=e.calculate(true,4000,25000,energyCounterNwh=null,observedAtElapsedMillis=21_000)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,unavailable.speedCategory)
    assertNull(unavailable.estimatedStoredEnergyRateWatts)
    assertEquals(100f,unavailable.rawPowerWatts!!,0.001f)
  }
  @Test fun fullResetAndStoppedHaveNoCategory() {
    val e=ChargingSpeedEngine()
    for(i in 0L..20L)e.calculate(true,4000,2000,energyCounterNwh=1_000_000_000L+i*2_500_000,observedAtElapsedMillis=i*1000)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,e.calculate(true,4000,2000,energyCounterNwh=1_060_000_000,observedAtElapsedMillis=21_000,isFull=true).speedCategory)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,e.calculate(false,4000,-1000,energyCounterNwh=1_070_000_000,observedAtElapsedMillis=22_000).speedCategory)
    assertEquals(CanonicalChargingSpeed.UNAVAILABLE,ChargingSpeedEngine().calculate(true,4000,2000,energyCounterNwh=1_070_000_000,observedAtElapsedMillis=22_000).speedCategory)
  }
}
