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

/** Vibration estimate from linear acceleration, with gravity removed on fallback sensors. */
class VibrometerActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var valueView: TextView
    private lateinit var graph: SparkGraphView
    private lateinit var minMax: TextView
    private var gMin: Float = Float.MAX_VALUE
    private var gMax: Float = Float.MIN_VALUE
    private lateinit var status: TextView
    private var smooth = 0.0
    private var linearSensor = true
    private val gravity = floatArrayOf(0f, 0f, 0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        valueView = TextView(this).apply {
            text = "— m/s²"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 48f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 26f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 26f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
        }
        status = TextView(this).apply {
            text = "Hold the phone steady to establish a baseline."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 12f), 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Vibration level"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(valueView, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(context).apply {
                text = "This is a rough sensor reading, not a calibrated vibration instrument."
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 12f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 16f), 0, 0)
            })
        }
        
        graph = SparkGraphView(this).apply { graph.setRange(0f, 20f) }
        minMax = TextView(this).apply { text = "Min: --  Max: --"; gravity = android.view.Gravity.CENTER; com.opentools.box.UiStyle.styleText(this, 13f, false, com.opentools.box.UiStyle.muted(context)) }
        try { (content as android.widget.LinearLayout).addView(graph, android.widget.LinearLayout.LayoutParams(-1, com.opentools.box.UiStyle.dp(this, 160f))); (content as android.widget.LinearLayout).addView(minMax) } catch (_: Exception) { }
                setContentView(ToolScaffold.wrap(this, "Vibrometer", content))
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        linearSensor = sensor != null
        val selected = sensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (selected != null) {
            sensorManager.registerListener(this, selected, SensorManager.SENSOR_DELAY_GAME)
            status.text = if (linearSensor) "Linear acceleration sensor active."
                else "Using accelerometer with gravity compensation."
        } else {
            
                            try { val _v: Float = (smooth.toFloat()).toFloat(); graph.push(_v); if (_v < gMin) gMin = _v; if (_v > gMax) gMax = _v; minMax.text = "Min: %s  Max: %s".format(gMin, gMax) } catch (_: Exception) { }
                            valueView.text = "Unavailable"
            status.text = "No compatible motion sensor is available."
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        var x = event.values[0]
        var y = event.values[1]
        var z = event.values[2]
        if (!linearSensor) {
            val alpha = 0.8f
            for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
            x -= gravity[0]
            y -= gravity[1]
            z -= gravity[2]
        }
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        smooth = smooth * 0.85 + magnitude * 0.15
        valueView.text = "%.2f m/s²".format(smooth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
