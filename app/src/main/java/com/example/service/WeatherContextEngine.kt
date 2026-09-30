package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.WeatherCondition
import com.example.model.WeatherContextState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class WeatherContextEngine(private val context: Context) {

    private var cachedWeather: WeatherContextState? = null
    private var lastFetchTimestamp: Long = 0L
    private val cacheDurationMs: Long = 30 * 60 * 1000L // 30 minutes cache

    // Hardware Ambient Temperature sensor fallback if present
    private var hardwareAmbientCelsius: Float? = null

    init {
        initAmbientSensor()
    }

    private fun initAmbientSensor() {
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val ambientSensor = sm?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
            if (ambientSensor != null) {
                sm.registerListener(
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event != null && event.values.isNotEmpty()) {
                                hardwareAmbientCelsius = event.values[0]
                            }
                        }
                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    },
                    ambientSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        } catch (_: Exception) {}
    }

    suspend fun fetchWeatherContext(
        latitude: Double?,
        longitude: Double?,
        forceRefresh: Boolean = false
    ): WeatherContextState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Return cached weather if fresh
        if (!forceRefresh && cachedWeather != null && (now - lastFetchTimestamp) < cacheDurationMs) {
            return@withContext cachedWeather!!
        }

        // 2. If no coordinates, check if hardware ambient sensor is available
        if (latitude == null || longitude == null) {
            val hwAmbient = hardwareAmbientCelsius
            if (hwAmbient != null) {
                val hwState = WeatherContextState(
                    temperatureCelsius = hwAmbient,
                    condition = WeatherCondition.UNKNOWN,
                    conditionText = "Ambient Hardware Sensor",
                    weatherSource = "Device Sensor.TYPE_AMBIENT_TEMPERATURE",
                    lastUpdated = now,
                    isAvailable = true
                )
                cachedWeather = hwState
                lastFetchTimestamp = now
                return@withContext hwState
            }
            return@withContext WeatherContextState(
                isAvailable = false,
                lastUpdated = now,
                weatherSource = "Location required for weather fetch"
            )
        }

        // 3. Fetch from Open-Meteo REST API
        try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BatterySentinelPro-Nethra/1.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val current = json.optJSONObject("current")

                if (current != null) {
                    val temp = current.optDouble("temperature_2m", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val feelsLike = current.optDouble("apparent_temperature", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val humidity = current.optInt("relative_humidity_2m", -1).takeIf { it in 0..100 }
                    val precip = current.optDouble("precipitation", 0.0) > 0.0
                    val wind = current.optDouble("wind_speed_10m", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val weatherCode = current.optInt("weather_code", -1)

                    val (condition, conditionText) = mapWmoWeatherCode(weatherCode, temp)

                    var severeAlert: String? = null
                    if (temp != null && temp >= 42.0f) {
                        severeAlert = "Severe Extreme Heat Advisory (${String.format("%.1f", temp)}°C ambient)"
                    } else if (temp != null && temp <= 0.0f) {
                        severeAlert = "Freezing Temperature Warning (${String.format("%.1f", temp)}°C ambient)"
                    }

                    val result = WeatherContextState(
                        temperatureCelsius = temp ?: hardwareAmbientCelsius,
                        feelsLikeCelsius = feelsLike,
                        humidityPercent = humidity,
                        condition = condition,
                        conditionText = conditionText,
                        isPrecipitating = precip,
                        windSpeedKmh = wind,
                        severeWeatherAlert = severeAlert,
                        weatherSource = "Open-Meteo Forecast API",
                        lastUpdated = now,
                        isAvailable = true
                    )

                    cachedWeather = result
                    lastFetchTimestamp = now
                    return@withContext result
                }
            }
        } catch (_: Exception) {}

        // Fallback to hardware ambient if online fetch failed
        val hwAmbient = hardwareAmbientCelsius
        if (hwAmbient != null) {
            val hwState = WeatherContextState(
                temperatureCelsius = hwAmbient,
                condition = WeatherCondition.UNKNOWN,
                conditionText = "Ambient Hardware Sensor (Offline fallback)",
                weatherSource = "Device Sensor.TYPE_AMBIENT_TEMPERATURE",
                lastUpdated = now,
                isAvailable = true
            )
            return@withContext hwState
        }

        // Return previous cached weather or unavailable
        cachedWeather ?: WeatherContextState(
            isAvailable = false,
            lastUpdated = now,
            weatherSource = "Weather network fetch unavailable"
        )
    }

    internal fun mapWmoWeatherCode(code: Int, tempCelsius: Float?): Pair<WeatherCondition, String> {
        if (tempCelsius != null && tempCelsius >= 40.0f) {
            return Pair(WeatherCondition.EXTREME_HEAT, "Extreme Heat")
        }
        if (tempCelsius != null && tempCelsius <= -2.0f) {
            return Pair(WeatherCondition.EXTREME_COLD, "Freezing Cold")
        }

        return when (code) {
            0 -> Pair(WeatherCondition.CLEAR, "Clear Sky")
            1, 2, 3 -> Pair(WeatherCondition.CLOUDY, "Partly Cloudy")
            45, 48 -> Pair(WeatherCondition.CLOUDY, "Foggy")
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Pair(WeatherCondition.RAIN, "Rain / Drizzle")
            56, 57 -> Pair(WeatherCondition.RAIN, "Freezing Drizzle")
            66, 67 -> Pair(WeatherCondition.RAIN, "Freezing Rain")
            71, 73, 75, 77, 85, 86 -> Pair(WeatherCondition.SNOW, "Snow")
            95, 96, 99 -> Pair(WeatherCondition.STORM, "Thunderstorm")
            else -> Pair(WeatherCondition.UNKNOWN, "Unavailable")
        }
    }
}
