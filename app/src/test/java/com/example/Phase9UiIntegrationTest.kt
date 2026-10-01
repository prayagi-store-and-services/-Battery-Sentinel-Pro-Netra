package com.example

import com.example.ui.navigation.BOTTOM_TABS
import com.example.ui.navigation.NetraTab
import com.example.ui.navigation.WIDGET_CATALOGUE_BUTTON_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase9UiIntegrationTest {

    @Test
    fun bottomNavigationContainsExactlyFiveTabsInRequiredOrder() {
        assertEquals(
            listOf(
                NetraTab.HOME,
                NetraTab.BATTERY,
                NetraTab.MONITORING,
                NetraTab.DEVICES,
                NetraTab.SETTINGS
            ),
            BOTTOM_TABS
        )
        assertEquals(5, BOTTOM_TABS.size)
    }

    @Test
    fun settingsIsTheFifthAndFinalBottomNavigationTab() {
        assertEquals(NetraTab.SETTINGS, BOTTOM_TABS.last())
        assertEquals("Settings", BOTTOM_TABS.last().title)
    }

    @Test
    fun bottomNavigationTagsAreUniqueAndStable() {
        val tags = BOTTOM_TABS.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(
            listOf("tab_home", "tab_battery", "tab_monitoring", "tab_devices", "tab_settings"),
            tags
        )
    }

    @Test
    fun widgetsAndLogsAreNotAddedAsExtraBottomNavigationTabs() {
        assertFalse(BOTTOM_TABS.any { it.title.equals("Widgets", ignoreCase = true) })
        assertFalse(BOTTOM_TABS.any { it.title.equals("Logs", ignoreCase = true) })
    }

    @Test
    fun widgetCatalogueUsesTheStableHeaderButtonTag() {
        assertEquals("top_bar_widgets_button", WIDGET_CATALOGUE_BUTTON_TAG)
        assertTrue(WIDGET_CATALOGUE_BUTTON_TAG.isNotBlank())
    }
}
