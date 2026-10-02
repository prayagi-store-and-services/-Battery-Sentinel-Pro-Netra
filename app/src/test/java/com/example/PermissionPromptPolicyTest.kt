package com.example

import com.example.permissions.Choice
import com.example.permissions.Prompt
import com.example.permissions.PermissionPromptPolicy
import com.example.permissions.Saved
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionPromptPolicyTest {
    private fun d(granted: Boolean = false, relevant: Boolean = true, s: Saved = Saved(), launch: Int = 1) =
        PermissionPromptPolicy.decide(granted, relevant, s, launch)

    @Test fun grantedIsNeverAsked() = assertEquals(Prompt.NONE, d(granted = true))
    @Test fun notRelevantIsNeverAsked() = assertEquals(Prompt.NONE, d(relevant = false))
    @Test fun firstTimeAsksOnce() = assertEquals(Prompt.FIRST, d())
    @Test fun skippedIsNotAskedAgainInTheSameLaunch() =
        assertEquals(Prompt.NONE, d(s = Saved(Choice.SKIPPED, skippedAtLaunch = 2), launch = 2))
    @Test fun skippedIsReaskedOnALaterLaunch() =
        assertEquals(Prompt.REASK, d(s = Saved(Choice.SKIPPED, skippedAtLaunch = 2), launch = 3))
    @Test fun remindLaterWaitsThenReasks() {
        val s = Saved(Choice.REMIND_LATER, skippedAtLaunch = 2, remindAtLaunch = 5)
        assertEquals(Prompt.NONE, d(s = s, launch = 4))
        assertEquals(Prompt.REASK, d(s = s, launch = 5))
    }
    @Test fun neverIsPermanent() = assertEquals(Prompt.NONE, d(s = Saved(Choice.NEVER), launch = 99))
}
