package com.example

import com.example.update.GitHubReleasePolicy as Policy
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GitHubReleasePolicyTest {
    private val hash = "ab".repeat(32)
    private fun release() = JSONObject().put("draft", false).put("prerelease", false)
        .put("tag_name", "v1.0.1").put("body", "versionCode: 2").put("assets", JSONArray().put(JSONObject()
            .put("name", "app-release.apk").put("size", 120L).put("digest", "sha256:$hash")
            .put("browser_download_url", "https://github.com/${Policy.OWNER}/${Policy.REPO}/releases/download/v1.0.1/app-release.apk")))

    @Test fun stableNewDigestBearingApkIsEligible() {
        assertEquals(2L, Policy.parse(release().toString(), 1)!!.versionCode)
    }
    @Test fun oldVersionDraftAndPrereleaseAreRejected() {
        assertNull(Policy.parse(release().toString(), 2))
        assertNull(Policy.parse(release().put("draft", true).toString(), 1))
        assertNull(Policy.parse(release().put("prerelease", true).toString(), 1))
    }
    @Test fun untrustedUrlMissingDigestAndOversizeFailClosed() {
        for (field in listOf("browser_download_url", "digest", "size")) {
            val json = release()
            val asset = json.getJSONArray("assets").getJSONObject(0)
            when (field) {
                "browser_download_url" -> asset.put(field, "https://evil.invalid/app-release.apk")
                "digest" -> asset.remove(field)
                "size" -> asset.put(field, Policy.MAX_APK_BYTES + 1)
            }
            assertNull(Policy.parse(json.toString(), 1))
        }
    }
    @Test fun ambiguousApkAssetsAreRejected() {
        val json = release()
        json.getJSONArray("assets").put(json.getJSONArray("assets").getJSONObject(0))
        assertNull(Policy.parse(json.toString(), 1))
    }
    @Test fun everyStagingIdentityAndIntegrityCheckIsRequired() {
        val c = Policy.parse(release().toString(), 1)!!
        assertTrue(Policy.canStage(c, 2, 120, hash, true, true, 1))
        assertFalse(Policy.canStage(c, 3, 120, hash, true, true, 1))
        assertFalse(Policy.canStage(c, 2, 119, hash, true, true, 1))
        assertFalse(Policy.canStage(c, 2, 120, "00".repeat(32), true, true, 1))
        assertFalse(Policy.canStage(c, 2, 120, hash, false, true, 1))
        assertFalse(Policy.canStage(c, 2, 120, hash, true, false, 1))
        assertFalse(Policy.canStage(c, 2, 120, hash, true, true, 2))
    }
    @Test fun malformedMetadataDoesNotCrash() {
        assertNull(Policy.parse("broken", 1))
        assertNull(Policy.parse("{}", 1))
        assertNull(Policy.parse(release().put("tag_name", "v9999999999999999999999").toString(), 1))
    }
}
