package com.example

import com.example.stats.UsagePing
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UsagePingTest {
    @Test fun requestBodyOnlyIncrementsOneCounterAndCarriesNothingElse() {
        val body = JSONObject(UsagePing.commitBody("battery-sentinel_20261003"))
        val writes = body.getJSONArray("writes")
        assertEquals(1, writes.length())
        val t = writes.getJSONObject(0).getJSONObject("transform")
        assertEquals("projects/netra-ai-jan/databases/(default)/documents/netra_active/battery-sentinel_20261003", t.getString("document"))
        val ft = t.getJSONArray("fieldTransforms")
        assertEquals(1, ft.length())
        assertEquals("count", ft.getJSONObject(0).getString("fieldPath"))
        assertEquals("1", ft.getJSONObject(0).getJSONObject("increment").getString("integerValue"))
        assertEquals(setOf("writes"), body.keys().asSequence().toSet())
    }
    @Test fun dayAndMonthIdsAreUtcDates() {
        val d = Date(1790000000000L)
        assertTrue(UsagePing.dayId(d).matches(Regex("[0-9]{8}")))
        assertTrue(UsagePing.monthId(d).matches(Regex("[0-9]{6}")))
        assertEquals(UsagePing.dayId(d).substring(0, 6), UsagePing.monthId(d))
    }
}
