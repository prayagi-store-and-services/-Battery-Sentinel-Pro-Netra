package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.repository.CacheStorageStats
import com.example.ui.components.CacheStorageDetails
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class CacheStorageRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun honestCachePolicyFits360dp() {
        rule.setContent { MaterialTheme { Surface { Column(Modifier.fillMaxSize().padding(16.dp)) {
            CacheStorageDetails(CacheStorageStats(scanAvailable=true,internalCacheBytes=1024,externalCacheBytes=2048,totalCacheBytes=3072,lastCleanupTimestamp=1793147400000,lastFreedBytes=12345))
            Spacer(Modifier.height(24.dp))
            CacheStorageDetails(CacheStorageStats(scanAvailable=false))
        } } } }
        rule.onAllNodesWithText("Disposable cache file payload")[0].assertIsDisplayed()
        rule.onNodeWithText("Cache size: Unavailable (scan incomplete or not yet run). Automatic cleanup skipped.").assertIsDisplayed()
        rule.runOnIdle {
            val v=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/cache-storage-policy.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
        }
    }
}
