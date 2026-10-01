package com.example

import android.app.Application
import com.example.model.LocationContextState
import com.example.model.WeatherCondition
import com.example.model.WeatherContextState
import com.example.service.NetraCentralDataCenter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeatherConditionRecoveryTest {
    private val location = LocationContextState(countryCode = "IN")
    @Test fun availableUnknownCodeDoesNotRetainClearSky() {
        val center = NetraCentralDataCenter()
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            temperatureCelsius = 25f, condition = WeatherCondition.CLEAR, isAvailable = true))
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            temperatureCelsius = 26f, condition = WeatherCondition.UNKNOWN,
            conditionText = "Unavailable", isAvailable = true))
        assertEquals(WeatherCondition.UNKNOWN, center.centralState.value.weatherContext.condition)
        assertEquals("Unavailable", center.centralState.value.weatherContext.conditionText)
    }
    @Test fun sensorOnlyObservationDoesNotKeepOldStormCondition() {
        val center = NetraCentralDataCenter()
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            condition = WeatherCondition.STORM, isAvailable = true))
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            temperatureCelsius = 24f, condition = WeatherCondition.UNKNOWN,
            conditionText = "Ambient Hardware Sensor", isAvailable = true))
        assertEquals(WeatherCondition.UNKNOWN, center.centralState.value.weatherContext.condition)
    }
    @Test fun failedWeatherStillRetainsLastKnownCondition() {
        val center = NetraCentralDataCenter()
        center.updateLocationAndWeatherDirect(location, WeatherContextState(
            condition = WeatherCondition.RAIN, isAvailable = true))
        center.updateLocationAndWeatherDirect(location, WeatherContextState(isAvailable = false))
        assertEquals(WeatherCondition.RAIN, center.centralState.value.weatherContext.condition)
    }
}
