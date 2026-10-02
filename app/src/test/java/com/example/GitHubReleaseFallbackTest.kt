package com.example

import com.example.update.GitHubReleasePolicy as Policy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GitHubReleaseFallbackTest {
    private val hash = "cd".repeat(32)
    private fun json(tag: String = "v1.1.10", code: Long = 12, sha: String = hash, size: Long = 1000) =
        JSONObject().put("tag", tag).put("versionCode", code).put("sha256", sha).put("size", size).toString()

    @Test fun newerBackupReleaseIsEligibleWithRepoDownloadUrl() {
        val c = Policy.parseFallback(json(), 11)!!
        assertEquals(12L, c.versionCode)
        assertEquals("https://github.com/${Policy.OWNER}/${Policy.REPO}/releases/download/v1.1.10/app-release.apk", c.url)
    }
    @Test fun sameOrOlderVersionIsNotOffered() {
        assertNull(Policy.parseFallback(json(), 12))
        assertNull(Policy.parseFallback(json(), 13))
    }
    @Test fun malformedBackupIsRejected() {
        assertNull(Policy.parseFallback(json(tag = "latest"), 1))
        assertNull(Policy.parseFallback(json(sha = "xyz"), 1))
        assertNull(Policy.parseFallback(json(size = 0), 1))
        assertNull(Policy.parseFallback("not json", 1))
    }
}
