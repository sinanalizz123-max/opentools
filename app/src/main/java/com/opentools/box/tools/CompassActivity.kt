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
import kotlin.math.roundToInt

/** Compass using the rotation-vector sensor. Original implementation. */
class CompassActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var dial: DialView
    private lateinit var heading: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        dial = DialView(this)
        heading = TextView(this).apply { textSize = 28f; text = "--°" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dial, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(heading)
        })
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotation, orientation)
        var azimuth = Math.toDegrees(orientation[0].toDouble())
        if (azimuth < 0) azimuth += 360
        dial.angle = azimuth.toFloat()
        heading.text = "${azimuth.roundToInt()}° ${directionName(azimuth)}"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun directionName(azimuth: Double): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((azimuth + 22.5) / 45).toInt() % 8]
    }

    private class DialView(context: Context) : View(context) {
        var angle = 0f
            set(value) { field = value; invalidate() }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 48f }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            canvas.save()
            canvas.rotate(-angle, cx, cy)
            canvas.drawText("N", cx - 16f, cy - 220f, paint)
            canvas.drawText("E", cx + 220f, cy + 16f, paint)
            canvas.drawText("S", cx - 16f, cy + 260f, paint)
            canvas.drawText("W", cx - 260f, cy + 16f, paint)
            canvas.restore()
            // Fixed needle pointing up (device heading).
            canvas.drawText("▲", cx - 16f, cy - 120f, paint)
        }
    }
}
