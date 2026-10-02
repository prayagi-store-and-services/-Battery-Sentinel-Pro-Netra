package com.example

import com.example.update.UpdateNotifyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotifyPolicyTest {
    @Test fun notifiesOnceForANewerVersion() {
        assertTrue(UpdateNotifyPolicy.shouldNotify(lastNotifiedCode = 0, candidateCode = 5, installedCode = 4))
        assertFalse(UpdateNotifyPolicy.shouldNotify(lastNotifiedCode = 5, candidateCode = 5, installedCode = 4))
    }

    @Test fun neverNotifiesForSameOrOlderVersion() {
        assertFalse(UpdateNotifyPolicy.shouldNotify(0, 4, 4))
        assertFalse(UpdateNotifyPolicy.shouldNotify(0, 3, 4))
    }

    @Test fun buildsTheReleasePageFromTheApkUrl() {
        assertEquals(
            "https://github.com/prayagideepak-collab/-Battery-Sentinel-Pro-Netra/releases/tag/v1.1.3",
            UpdateNotifyPolicy.releasePageUrl("https://github.com/prayagideepak-collab/-Battery-Sentinel-Pro-Netra/releases/download/v1.1.3/app-release.apk")
        )
        assertNull(UpdateNotifyPolicy.releasePageUrl("https://example.com/x/app-release.apk"))
    }
}
