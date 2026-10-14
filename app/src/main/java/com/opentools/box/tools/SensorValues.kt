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
import kotlin.math.sqrt

/** Base class for single-value sensor readouts. */
abstract class SensorValueActivity(
    private val sensorType: Int,
    private val title: String,
) : AppCompatActivity(), SensorEventListener {

    protected lateinit var sensorManager: SensorManager
    protected lateinit var valueView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val titleView = TextView(this).apply { text = title; textSize = 20f; gravity = Gravity.CENTER }
        valueView = TextView(this).apply { textSize = 48f; gravity = Gravity.CENTER; text = "--" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(titleView); addView(valueView)
        })
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) valueView.text = "No sensor"
        else sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
}

/** EMF meter (magnetometer, µT). */
class EmfActivity : SensorValueActivity(Sensor.TYPE_MAGNETIC_FIELD, "EMF") {
    override fun onSensorChanged(e: SensorEvent) {
        val mag = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble())
        valueView.text = "%.1f µT".format(mag)
    }
}

/** G-force meter (accelerometer, total g). */
class GMeterActivity : SensorValueActivity(Sensor.TYPE_ACCELEROMETER, "G-force") {
    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()) / SensorManager.GRAVITY_EARTH
        valueView.text = "%.2f g".format(g)
    }
}

/** Barometric pressure (hPa). */
class PressureActivity : SensorValueActivity(Sensor.TYPE_PRESSURE, "Pressure") {
    override fun onSensorChanged(e: SensorEvent) {
        valueView.text = "%.1f hPa".format(e.values[0])
    }
}

/** Relative humidity (%). */
class HumidityActivity : SensorValueActivity(Sensor.TYPE_RELATIVE_HUMIDITY, "Humidity") {
    override fun onSensorChanged(e: SensorEvent) {
        valueView.text = "%.1f %%".format(e.values[0])
    }
}

/** Ambient light (lux). */
class LightSensorActivity : SensorValueActivity(Sensor.TYPE_LIGHT, "Light") {
    override fun onSensorChanged(e: SensorEvent) {
        valueView.text = "%.0f lx".format(e.values[0])
    }
}

/** Proximity (cm). */
class ProximityActivity : SensorValueActivity(Sensor.TYPE_PROXIMITY, "Proximity") {
    override fun onSensorChanged(e: SensorEvent) {
        valueView.text = "%.1f cm".format(e.values[0])
    }
}
