package com.opentools.box.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.sqrt

/** Shared native readout screen for individual device sensors. */
abstract class SensorValueActivity(
    private val sensorType: Int,
    private val title: String,
) : AppCompatActivity(), SensorEventListener {

    protected lateinit var sensorManager: SensorManager
    protected lateinit var valueView: TextView
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        valueView = TextView(this).apply {
            text = "—"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 48f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 26f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 26f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
            contentDescription = "$title reading"
        }
        statusView = TextView(this).apply {
            text = "Waiting for sensor…"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 12f), 0, 0)
        }
        val description = when (sensorType) {
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer reading. This measures magnetic flux density, not electric fields."
            Sensor.TYPE_ACCELEROMETER -> "Acceleration includes gravity and motion."
            Sensor.TYPE_PRESSURE -> "Barometric pressure varies with altitude and weather."
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative humidity measured by the device sensor."
            Sensor.TYPE_LIGHT -> "Ambient illumination near the device sensor."
            Sensor.TYPE_PROXIMITY -> "Distance estimate from the proximity sensor; range varies by device."
            else -> "Live reading from the device's built-in sensor."
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = title
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(valueView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(statusView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(TextView(context).apply {
                text = description
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                    UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
                background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            })
        }
        setContentView(ToolScaffold.wrap(this, title, content))
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            valueView.text = "Unavailable"
            statusView.text = "This device does not have a compatible sensor."
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        statusView.text = "Reading sensor…"
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    protected fun showValue(value: String) {
        valueView.text = value
        statusView.text = "Sensor active"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Magnetic field strength (µT); often referred to as an EMF meter. */
class EmfActivity : SensorValueActivity(Sensor.TYPE_MAGNETIC_FIELD, "Magnetic Field") {
    override fun onSensorChanged(e: SensorEvent) {
        val magnitude = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble())
        showValue("%.1f µT".format(magnitude))
    }
}

/** G-force meter (accelerometer, total g). */
class GMeterActivity : SensorValueActivity(Sensor.TYPE_ACCELEROMETER, "G-force") {
    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()) / SensorManager.GRAVITY_EARTH
        showValue("%.2f g".format(g))
    }
}

/** Barometric pressure (hPa). */
class PressureActivity : SensorValueActivity(Sensor.TYPE_PRESSURE, "Pressure") {
    override fun onSensorChanged(e: SensorEvent) { showValue("%.1f hPa".format(e.values[0])) }
}

/** Relative humidity (%). */
class HumidityActivity : SensorValueActivity(Sensor.TYPE_RELATIVE_HUMIDITY, "Humidity") {
    override fun onSensorChanged(e: SensorEvent) { showValue("%.1f %%".format(e.values[0])) }
}

/** Ambient light (lux). */
class LightSensorActivity : SensorValueActivity(Sensor.TYPE_LIGHT, "Ambient Light") {
    override fun onSensorChanged(e: SensorEvent) { showValue("%.0f lx".format(e.values[0])) }
}

/** Proximity (cm). */
class ProximityActivity : SensorValueActivity(Sensor.TYPE_PROXIMITY, "Proximity") {
    override fun onSensorChanged(e: SensorEvent) { showValue("%.1f cm".format(e.values[0])) }
}
