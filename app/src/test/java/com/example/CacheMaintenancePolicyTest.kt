package com.example

import com.example.data.repository.CacheMaintenancePolicy
import org.junit.Assert.*
import org.junit.Test

class CacheMaintenancePolicyTest {
    @Test fun thresholdIsStrictlyAbove200MiB() {
        assertFalse(CacheMaintenancePolicy.overThreshold(50L * 1024 * 1024))
        assertFalse(CacheMaintenancePolicy.overThreshold(CacheMaintenancePolicy.THRESHOLD_BYTES))
        assertTrue(CacheMaintenancePolicy.overThreshold(CacheMaintenancePolicy.THRESHOLD_BYTES + 1))
    }
    @Test fun intervalUsesSixHoursAndFirstAttempt() {
        assertTrue(CacheMaintenancePolicy.due(100, 0))
        assertFalse(CacheMaintenancePolicy.due(101, 100))
        assertFalse(CacheMaintenancePolicy.due(99, 100))
        assertFalse(CacheMaintenancePolicy.due(100 + CacheMaintenancePolicy.AUTO_INTERVAL_MS - 1, 100))
        assertTrue(CacheMaintenancePolicy.due(100 + CacheMaintenancePolicy.AUTO_INTERVAL_MS, 100))
    }
}
