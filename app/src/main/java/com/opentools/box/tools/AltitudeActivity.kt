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
import kotlin.math.pow

/** Altimeter estimate from barometric pressure. */
class AltitudeActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var altitudeView: TextView
    private lateinit var pressureView: TextView
    private lateinit var graph: SparkGraphView
    private lateinit var minMax: TextView
    private var gMin: Float = Float.MAX_VALUE
    private var gMax: Float = Float.MIN_VALUE
    private lateinit var statusView: TextView
    private var seaLevel = 1013.25f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        altitudeView = TextView(this).apply {
            text = "— m"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 48f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 24f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 24f))
        }
        pressureView = TextView(this).apply {
            text = "— hPa"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 20f, true)
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        statusView = TextView(this).apply {
            text = "Altitude is estimated from air pressure."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 12f), 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Current altitude"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(altitudeView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(pressureView, LinearLayout.LayoutParams(-1, -2))
            addView(statusView, LinearLayout.LayoutParams(-1, -2))
        }
        
        graph = SparkGraphView(this).apply { graph.setRange(-500f, 9000f) }
        minMax = TextView(this).apply { text = "Min: --  Max: --"; gravity = android.view.Gravity.CENTER; com.opentools.box.UiStyle.styleText(this, 13f, false, com.opentools.box.UiStyle.muted(context)) }
        try { (content as android.widget.LinearLayout).addView(graph, android.widget.LinearLayout.LayoutParams(-1, com.opentools.box.UiStyle.dp(this, 160f))); (content as android.widget.LinearLayout).addView(minMax) } catch (_: Exception) { }
                setContentView(ToolScaffold.wrap(this, "Altimeter", content))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            statusView.text = "Using barometric pressure and standard sea-level pressure."
        } ?: run {
            altitudeView.text = "Unavailable"
            pressureView.text = "No barometer"
            statusView.text = "This device has no pressure sensor."
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val pressure = event.values[0]
        val altitude = 44330.0 * (1 - (pressure / seaLevel).toDouble().pow(1 / 5.255))
        altitudeView.text = "%.1f m".format(altitude)
        pressureView.text = "%.1f hPa".format(pressure)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
