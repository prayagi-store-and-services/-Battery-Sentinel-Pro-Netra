package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.EnvironmentalHeatDiagnosis
import java.util.Locale

data class ThermalCauseDiagnosisResult(
    val state: EnvironmentalHeatDiagnosis,
    val message: String
)

/**
 * Investigates potential root causes of high temperature when critical thermal control is active (>40°C).
 * Distinguishes external/environmental heat vs internal device-generated heat using public Android Sensor APIs & Weather context.
 * Only activated conditionally on thermal events to avoid battery drain, and stopped upon recovery (<=35°C).
 */
class ThermalCauseInvestigator(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val ambientTempSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

    private var isInvestigating = false
    private var lastAmbientReading: Float? = null

    val isSensorAvailable: Boolean
        get() = ambientTempSensor != null

    fun startInvestigation() {
        if (!isInvestigating && ambientTempSensor != null) {
            isInvestigating = true
            sensorManager?.registerListener(this, ambientTempSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopInvestigation() {
        if (isInvestigating) {
            isInvestigating = false
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            val ambient = event.values.firstOrNull()
            if (ambient != null && ambient > -50f && ambient < 100f) {
                lastAmbientReading = ambient
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun diagnoseThermalCause(
        batteryTempCelsius: Float,
        weatherAmbientTempCelsius: Float? = null,
        isCharging: Boolean = false,
        isHeavyLoad: Boolean = false
    ): ThermalCauseDiagnosisResult {
        val ambient = (lastAmbientReading ?: weatherAmbientTempCelsius)?.takeIf { it.isFinite() }

        return when {
            ambient == null || !batteryTempCelsius.isFinite() -> {
                ThermalCauseDiagnosisResult(
                    state = EnvironmentalHeatDiagnosis.INSUFFICIENT_SENSOR_DATA,
                    message = "Ambient thermal telemetry unavailable on this device. Internal protection remains authoritative."
                )
            }
            ambient >= 36.0f && (isCharging || isHeavyLoad) -> {
                ThermalCauseDiagnosisResult(
                    state = EnvironmentalHeatDiagnosis.MIXED_HEAT_CONTEXT,
                    message = "Mixed Heat Context: High ambient climate (${String.format(Locale.US, "%.1f", ambient)}°C) combined with active device charging or workload."
                )
            }
            ambient >= 35.0f -> {
                ThermalCauseDiagnosisResult(
                    state = EnvironmentalHeatDiagnosis.ENVIRONMENTAL_HEAT_LIKELY,
                    message = "Environmental Heat Likely: Elevated ambient temperature (${String.format(Locale.US, "%.1f", ambient)}°C). Move device to shade or cooler area."
                )
            }
            ambient < 30.0f && batteryTempCelsius >= 40.0f -> {
                ThermalCauseDiagnosisResult(
                    state = EnvironmentalHeatDiagnosis.INTERNAL_HEAT_LIKELY,
                    message = "Internal Heat Likely: Ambient temperature is moderate (${String.format(Locale.US, "%.1f", ambient)}°C). Elevated temperature is driven by internal processor or charging workload."
                )
            }
            batteryTempCelsius <= 35.0f -> {
                ThermalCauseDiagnosisResult(
                    state = EnvironmentalHeatDiagnosis.NORMAL_ENVIRONMENTAL_CONTEXT,
                    message = "Thermal operating baseline normal for current environmental context."
                )
            }
            else -> {
                ThermalCauseDiagnosisResult(
                    state = EnvironmentalHeatDiagnosis.NORMAL_ENVIRONMENTAL_CONTEXT,
                    message = "Thermal conditions operating within standard environmental parameters."
                )
            }
        }
    }
}
