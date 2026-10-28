package com.example.community

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi", sdk=[34], application=Application::class)
class CommunityCardTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun render(open: (String)->Unit, large: Boolean = false) {
        rule.setContent { MaterialTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if(large) 1.5f else 1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { CommunityCard(open) }
            }
        } }
    }
    private fun shot(name: String) = rule.runOnIdle {
        val v=rule.activity.window.decorView
        val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        java.io.File("build/outputs/community-$name.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        b.recycle()
    }
    @Test fun officialLinksOpenOnlyAfterTap() {
        val opened=mutableListOf<String>()
        render({opened.add(it)})
        rule.runOnIdle { assertTrue(opened.isEmpty()) }
        CommunityLinks.social.forEach { link ->
            rule.onNodeWithText(link.icon+"  "+link.label).performScrollTo().performClick()
            rule.runOnIdle { assertEquals(link.url,opened.last()) }
        }
        rule.onNodeWithText("Battery website").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(CommunityLinks.WEBSITE,opened.last()) }
        rule.onNodeWithText("Private complaint or feedback").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(CommunityLinks.CONTACT,opened.last()); assertEquals(7,opened.size) }
        shot("feedback")
    }
    @Test fun unavailableBrowserShowsHonestFailure() {
        render({throw IllegalStateException("fixture")})
        rule.onNodeWithText("Battery website").performScrollTo().performClick()
        rule.onNodeWithText("Could not open this link. Check that a browser is available.").performScrollTo().assertIsDisplayed()
        shot("unavailable")
    }
    @Test fun largeFontKeepsLabelsReadable() {
        render({},true)
        rule.onNodeWithText("Connect with Netra Ecosystem").assertIsDisplayed()
        shot("large-top")
        rule.onNodeWithText("Private complaint or feedback").performScrollTo().assertIsDisplayed()
        shot("large-bottom")
    }
    @Test fun destinationsStayFixedHttps() {
        assertEquals(5,CommunityLinks.social.size)
        assertEquals(5,CommunityLinks.social.map{it.url}.distinct().size)
        assertTrue((CommunityLinks.social.map{it.url}+listOf(CommunityLinks.WEBSITE,CommunityLinks.CONTACT)).all{it.startsWith("https://")})
        assertEquals("https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/",CommunityLinks.WEBSITE)
        assertEquals("https://prayagi-store-and-services.github.io/netra-eco/contact.html?app=battery-sentinel",CommunityLinks.CONTACT)
    }
}
