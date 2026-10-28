package com.example.community

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CommunityBrowserTest {
    private fun handler(pkg: String, browser: Boolean) = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main" }
    }
    @Test fun verifiedBatteryLinkCannotLoopIntoBattery() {
        val url = CommunityLinks.WEBSITE
        val options = CommunityBrowser.candidates(url, "com.aistudio.batterysentinel.ntra", listOf(
            handler("com.aistudio.batterysentinel.ntra", false), handler("com.example.browser", true)))
        assertEquals(1, options.size)
        assertEquals("com.example.browser", options.single().component!!.packageName)
        assertEquals(url, options.single().dataString)
        assertEquals(Intent.ACTION_VIEW, options.single().action)
        assertTrue(options.single().categories!!.contains(Intent.CATEGORY_BROWSABLE))
    }
    @Test fun noBrowserNeverFallsBackToEcoOrPlainView() {
        assertTrue(CommunityBrowser.candidates(CommunityLinks.WEBSITE, "com.aistudio.batterysentinel.ntra",
            listOf(handler("com.aistudio.batterysentinel.ntra", true))).isEmpty())
    }
    @Test fun realOpenUsesBrowserComponentEvenWhenEcoClaimsDestination() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadowPm = org.robolectric.Shadows.shadowOf(context.packageManager)
        val browserProbe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        shadowPm.addResolveInfoForIntent(browserProbe, handler("real.browser", true))
        val url = CommunityLinks.WEBSITE
        shadowPm.addResolveInfoForIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)), handler(context.packageName, false))
        CommunityBrowser.open(context, url)
        val sent = org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals("real.browser", sent.component!!.packageName)
        assertEquals(url, sent.dataString)
        assertNotEquals(context.packageName, sent.component!!.packageName)
    }
    @Test fun browserChoicesKeepExactDestinationWithoutDuplicates() {
        val url = CommunityLinks.CONTACT
        val options = CommunityBrowser.candidates(url, "com.aistudio.batterysentinel.ntra", listOf(handler("b.one", true), handler("b.two", true), handler("b.one", true)))
        assertEquals(2, options.size)
        assertTrue(options.all { it.dataString == url && it.component != null })
    }
}
