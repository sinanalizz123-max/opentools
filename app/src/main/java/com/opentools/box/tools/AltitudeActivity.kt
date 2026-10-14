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
import kotlin.math.pow

/** Altimeter from barometer (or GPS altitude fallback note). Original implementation. */
class AltitudeActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var altitudeView: TextView
    private lateinit var pressureView: TextView
    private var seaLevel = 1013.25f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        altitudeView = TextView(this).apply { textSize = 52f; gravity = Gravity.CENTER }
        pressureView = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Altimeter",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(altitudeView); addView(pressureView)
        }))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            altitudeView.text = "No barometer"
            pressureView.text = "This device has no pressure sensor"
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        val p = e.values[0]
        // Barometric formula.
        val alt = 44330.0 * (1 - (p / seaLevel).toDouble().pow(1 / 5.255))
        altitudeView.text = "%.1f m".format(alt)
        pressureView.text = "%.1f hPa".format(p)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
}
