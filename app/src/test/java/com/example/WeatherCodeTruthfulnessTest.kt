package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.model.WeatherCondition
import com.example.service.WeatherContextEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeatherCodeTruthfulnessTest {
    private fun engine() = WeatherContextEngine(ApplicationProvider.getApplicationContext<Application>())

    @Test fun missingAndInvalidCodesAreNotClearWeather() {
        for (code in listOf(-1, -999, 4, 100, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(WeatherCondition.UNKNOWN to "Unavailable", engine().mapWmoWeatherCode(code, null))
        }
    }

    @Test fun freezingDrizzleCodesAreNotClearWeather() {
        for (code in listOf(56, 57)) {
            assertEquals(WeatherCondition.RAIN to "Freezing Drizzle", engine().mapWmoWeatherCode(code, 3f))
        }
    }

    @Test fun freezingRainCodesAreNotClearWeather() {
        for (code in listOf(66, 67)) {
            assertEquals(WeatherCondition.RAIN to "Freezing Rain", engine().mapWmoWeatherCode(code, 3f))
        }
    }

    @Test fun recognizedClearAndThermalWarningsRemainIntact() {
        assertEquals(WeatherCondition.CLEAR to "Clear Sky", engine().mapWmoWeatherCode(0, 20f))
        assertEquals(WeatherCondition.STORM to "Thunderstorm", engine().mapWmoWeatherCode(95, 20f))
        assertEquals(WeatherCondition.EXTREME_HEAT to "Extreme Heat", engine().mapWmoWeatherCode(-1, 42f))
        assertEquals(WeatherCondition.EXTREME_COLD to "Freezing Cold", engine().mapWmoWeatherCode(-1, -3f))
    }
}
