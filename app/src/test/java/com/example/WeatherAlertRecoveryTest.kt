package com.example

import android.app.Application
import com.example.model.LocationContextState
import com.example.model.WeatherCondition
import com.example.model.WeatherContextState
import com.example.service.NetraCentralDataCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeatherAlertRecoveryTest {
    private val location = LocationContextState(countryCode = "IN")
    private fun hot() = WeatherContextState(temperatureCelsius = 43f,
        condition = WeatherCondition.EXTREME_HEAT, severeWeatherAlert = "Severe Extreme Heat Advisory", isAvailable = true)

    @Test fun successfulNormalWeatherClearsOldHeatAlert() {
        val center = NetraCentralDataCenter()
        center.updateLocationAndWeatherDirect(location, hot())
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            temperatureCelsius = 25f, condition = WeatherCondition.CLEAR, isAvailable = true))
        assertNull(center.centralState.value.weatherContext.severeWeatherAlert)
        assertEquals(25f, center.centralState.value.weatherContext.temperatureCelsius)
    }

    @Test fun failedWeatherRetainsLastKnownAlert() {
        val center = NetraCentralDataCenter()
        center.updateLocationAndWeatherDirect(location, hot())
        center.updateLocationAndWeatherDirect(location, WeatherContextState(isAvailable = false))
        assertEquals("Severe Extreme Heat Advisory", center.centralState.value.weatherContext.severeWeatherAlert)
        assertEquals(43f, center.centralState.value.weatherContext.temperatureCelsius)
    }

    @Test fun newAvailableAlertReplacesOldAlertThenClears() {
        val center = NetraCentralDataCenter()
        center.updateLocationAndWeatherDirect(location, hot())
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            temperatureCelsius = -3f, condition = WeatherCondition.EXTREME_COLD,
            severeWeatherAlert = "Freezing Temperature Warning", isAvailable = true))
        assertEquals("Freezing Temperature Warning", center.centralState.value.weatherContext.severeWeatherAlert)
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            temperatureCelsius = 20f, isAvailable = true))
        assertNull(center.centralState.value.weatherContext.severeWeatherAlert)
    }
}
