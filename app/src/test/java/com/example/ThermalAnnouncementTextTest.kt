package com.example

import com.example.service.ThermalAnnouncementText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard for the thermal guardian label task: spoken thermal lines must name the battery
 * temperature (never "phone temperature") and must never claim the app cools the device.
 */
class ThermalAnnouncementTextTest {

    private val lines = listOf(
        ThermalAnnouncementText.warning(41.5f),
        ThermalAnnouncementText.critical(45.2f),
        ThermalAnnouncementText.PROTECTION_ON
    )

    @Test fun `no spoken line claims the app cools the phone`() {
        lines.forEach { line ->
            assertFalse("cooling claim leaked: $line", line.contains("cool", ignoreCase = true))
        }
    }

    @Test fun `temperature lines identify the battery sensor`() {
        assertTrue(ThermalAnnouncementText.warning(41.5f).startsWith("Battery temperature high"))
        assertTrue(ThermalAnnouncementText.critical(45.2f).startsWith("Battery temperature critical"))
        lines.forEach { line ->
            assertFalse("phone-temperature framing leaked: $line", line.contains("phone temperature", ignoreCase = true))
        }
    }

    @Test fun `temperature values render with one decimal`() {
        assertEquals("Battery temperature high, 41.5 degrees.", ThermalAnnouncementText.warning(41.5f))
        assertEquals("Battery temperature critical, 45.2 degrees.", ThermalAnnouncementText.critical(45.2f))
    }

    @Test fun `protection start is named protection not cooling`() {
        assertEquals("Thermal protection on.", ThermalAnnouncementText.PROTECTION_ON)
    }
}
