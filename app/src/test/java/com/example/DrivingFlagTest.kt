package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.service.ActivePowerSaver
import com.example.service.DrivingFlag
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrivingFlagTest {
    @Test fun noHubInstalledMeansNotDriving() {
        assertFalse(DrivingFlag.isDriving(ApplicationProvider.getApplicationContext()))
    }
    @Test fun activeSaverRuleUnchangedByFlagItself() {
        // The pure rule is unchanged: forced window or 15 minutes screen-off. Driving is applied on top in tick().
        assertFalse(ActivePowerSaver.shouldBeActive(true, 12, true, 0L))
    }
}
