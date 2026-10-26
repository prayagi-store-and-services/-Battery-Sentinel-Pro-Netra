package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.permissions.Perm
import com.example.permissions.PermissionCheck
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BatteryOptimizationPolicyTest {
    @Test fun directExemptionPermissionIsAbsent() {
        val parser = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = parser.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val nodes = doc.getElementsByTagName("uses-permission")
        assertFalse((0 until nodes.length).any {
            (nodes.item(it) as org.w3c.dom.Element).getAttributeNS("http://schemas.android.com/apk/res/android", "name") ==
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        })
    }
    @Test fun batterySettingsNeverTriggerOnboarding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(PermissionCheck.isRelevant(context, Perm.BATTERY_OPT))
    }
    @Test fun optionalBatteryChoiceOpensSettingsWithoutPackageExemptionRequest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = PermissionCheck.settingsIntent(context, Perm.BATTERY_OPT)
        assertNotNull(intent)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent!!.action)
        assertNull(intent.data)
    }
    @Test fun noProductionSourceInvokesDirectExemptionAction() {
        val root = File("src/main/java")
        assertTrue(root.isDirectory)
        assertFalse(root.walkTopDown().filter { it.isFile && it.extension == "kt" }.any {
            it.readText().contains("ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        })
    }
}
