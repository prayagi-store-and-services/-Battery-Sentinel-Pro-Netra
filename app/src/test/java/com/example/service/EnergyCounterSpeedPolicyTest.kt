package com.example.service

import org.junit.Assert.*
import org.junit.Test

class EnergyCounterSpeedPolicyTest {
  @Test fun exactApprovedBoundaries() {
    val cases = listOf(4.9 to EnergySpeed.SLOW, 5.0 to EnergySpeed.NORMAL, 9.9 to EnergySpeed.NORMAL,
      10.0 to EnergySpeed.FAST, 19.9 to EnergySpeed.FAST, 20.0 to EnergySpeed.ULTRA_FAST,
      49.9 to EnergySpeed.ULTRA_FAST, 50.0 to EnergySpeed.HYPER_FAST, 99.9 to EnergySpeed.HYPER_FAST,
      100.0 to EnergySpeed.EXTREME_FAST)
    cases.forEach { (watts, expected) -> assertEquals(expected, EnergyCounterSpeedPolicy.classify(watts)) }
    listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { assertNull(EnergyCounterSpeedPolicy.classify(it)) }
  }
  @Test fun requiresObservedAverageThenTenSecondsOfStableCategory() {
    val p = EnergyCounterSpeedPolicy()
    for (second in 0L..19L) assertNull(p.observe(true, 1_000_000_000L + second * 2_500_000L, second * 1000).speed)
    val result = p.observe(true, 1_050_000_000L, 20_000)
    assertEquals(9.0, result.watts!!, 0.000001)
    assertEquals(EnergySpeed.NORMAL, result.speed)
  }
  @Test fun missingZeroDecreasingOrStoppedNeverMeansSlow() {
    for (bad in listOf<Long?>(null, 0, -1, Long.MIN_VALUE)) {
      val p = EnergyCounterSpeedPolicy()
      assertNull(p.observe(true, bad, 0).speed)
      assertEquals(0, p.sampleCount)
    }
    val p = EnergyCounterSpeedPolicy()
    p.observe(true, 1_000_000, 0)
    assertNull(p.observe(true, 900_000, 1000).watts)
    assertNull(p.observe(false, 2_000_000, 2000).speed)
    assertEquals(0, p.sampleCount)
  }
  @Test fun sparseEventsAndClockRewindRequireNewObservations() {
    val p = EnergyCounterSpeedPolicy()
    p.observe(true, 1_000_000_000, 0)
    assertNull(p.observe(true, 1_050_000_000, 20_000).speed)
    assertEquals(1, p.sampleCount)
    assertNull(p.observe(true, 1_060_000_000, 1).speed)
    assertEquals(1, p.sampleCount)
  }
  @Test fun duplicateSameObservationDoesNotResetStableState() {
    val p = EnergyCounterSpeedPolicy()
    var r = EnergySpeedReading()
    for (i in 0L..20L) { r = p.observe(true, 1_000_000_000L+i*2_500_000, i*1000); assertEquals(r,p.observe(true,1_000_000_000L+i*2_500_000,i*1000)) }
    assertEquals(EnergySpeed.NORMAL,r.speed)
  }
  @Test fun denseEventFloodIsBoundedAndCannotInventCoverage() {
    val p = EnergyCounterSpeedPolicy()
    repeat(10000) { assertNull(p.observe(true, 1_000_000_000L+it*1000, it.toLong()).speed) }
    assertTrue(p.sampleCount <= EnergyCounterSpeedPolicy.MAX_POINTS)
  }
  @Test fun restartAndIncompleteNewCategoryCannotReuseOldSpeed() {
    val p = EnergyCounterSpeedPolicy()
    for(i in 0L..20L) p.observe(true,1_000_000_000L+i*2_500_000,i*1000)
    assertNull(EnergyCounterSpeedPolicy().observe(true,1_050_000_000,20_000).speed)
    assertNull(p.observe(true, 1_050_000_000,21_000).speed)
  }
}
