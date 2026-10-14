package com.opentools.box.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

/** Protractor: pitch/roll angle readout with dial. Original implementation. */
class ProtractorActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var dial: Dial
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        dial = Dial(this)
        label = TextView(this).apply { textSize = 32f }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Protractor",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dial, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(label)
        }))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        val rot = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        val o = FloatArray(3)
        SensorManager.getOrientation(rot, o)
        val pitch = Math.toDegrees(o[1].toDouble())
        val roll = Math.toDegrees(o[2].toDouble())
        dial.angle = roll.toFloat()
        label.text = "Pitch %.1f°   Roll %.1f°".format(pitch, roll)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    class Dial(context: Context) : View(context) {
        var angle = 0f
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 36f; strokeWidth = 3f; style = Paint.Style.STROKE
        }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f - 30f
            c.drawCircle(cx, cy, r, paint)
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                c.drawLine(
                    (cx + r * 0.85 * Math.cos(rad)).toFloat(),
                    (cy + r * 0.85 * Math.sin(rad)).toFloat(),
                    (cx + r * Math.cos(rad)).toFloat(),
                    (cy + r * Math.sin(rad)).toFloat(), paint)
            }
            val a = Math.toRadians(angle.toDouble())
            paint.style = Paint.Style.FILL
            c.drawLine(cx, cy,
                (cx + r * 0.9 * Math.sin(a)).toFloat(),
                (cy - r * 0.9 * Math.cos(a)).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            c.drawText("%.1f°".format(abs(angle)), cx - 60f, cy + r + 50f, paint)
        }
    }
}
