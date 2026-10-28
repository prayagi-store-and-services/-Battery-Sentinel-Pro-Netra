package com.example

import com.example.service.*
import org.junit.Assert.*
import org.junit.Test

class CrashRecoveryPolicyTest {
    @Test fun oneAndTwoCrashesNeverPause() {
        val one=CrashRecoveryPolicy.record(CrashRecoveryState(),1000)
        val two=CrashRecoveryPolicy.record(one,2000)
        assertEquals(0L,one.pauseUntilMs);assertEquals(0L,two.pauseUntilMs)
    }
    @Test fun thirdCrashWithinFiveMinutesPausesTwoMinutes() {
        var s=CrashRecoveryState()
        for(t in listOf(1000L,2000L,3000L)) s=CrashRecoveryPolicy.record(s,t)
        assertEquals(123000L,s.pauseUntilMs)
        assertEquals(s,CrashRecoveryPolicy.record(s,4000))
        assertEquals(CrashRecoveryState(),CrashRecoveryPolicy.normalized(s,123000))
    }
    @Test fun windowExpiresAndClockRollbackResets() {
        val s=CrashRecoveryPolicy.record(CrashRecoveryState(),1000)
        assertEquals(1,CrashRecoveryPolicy.record(s,302000).crashes)
        assertEquals(CrashRecoveryState(),CrashRecoveryPolicy.normalized(s,999))
        val future=CrashRecoveryState(100,3,99999999)
        assertEquals(CrashRecoveryState(),CrashRecoveryPolicy.normalized(future,1000))
    }
}
