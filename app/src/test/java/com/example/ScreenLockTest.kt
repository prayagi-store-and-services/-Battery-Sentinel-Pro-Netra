package com.example

import com.example.service.ScreenLock
import org.junit.Assert.*
import org.junit.Test

class ScreenLockTest {
    @Test fun locksOnlyWhenAllThree() = assertTrue(ScreenLock.shouldLock(true, true, true))
    @Test fun notWhenOptedOut() = assertFalse(ScreenLock.shouldLock(false, true, true))
    @Test fun notWithoutPermission() = assertFalse(ScreenLock.shouldLock(true, false, true))
    @Test fun notWhenChargingScreenWasNotShowing() = assertFalse(ScreenLock.shouldLock(true, true, false))
}
