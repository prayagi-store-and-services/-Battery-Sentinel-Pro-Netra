package com.example

import com.example.service.BatteryNotificationIds as Ids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BatteryNotificationIdsTest {
    @Test fun allCoreNotificationsHaveDifferentIdentities() {
        val ids = listOf(Ids.STATUS, Ids.TARGET_ALARM, Ids.OVERHEAT, Ids.THRESHOLD, Ids.TARGET_TEST)
        assertEquals(ids.size, ids.toSet().size)
    }
    @Test fun testingCannotReplaceAnOverheatWarning() {
        assertNotEquals(Ids.OVERHEAT, Ids.TARGET_TEST)
    }
    @Test fun existingIdentitiesStayCompatibleWithCancellationPaths() {
        assertEquals(listOf(2001, 2002, 2003, 2004), listOf(Ids.STATUS, Ids.TARGET_ALARM, Ids.OVERHEAT, Ids.THRESHOLD))
    }
}
