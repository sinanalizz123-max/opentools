package com.opentools.box.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/** Bubble level (accelerometer). Original implementation. */
class LevelActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var view: LevelView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        view = LevelView(this)
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        // Tilt angle from gravity vector (portrait assumed).
        val xAngle = Math.toDegrees(Math.atan2(e.values[0].toDouble(), e.values[2].toDouble()))
        val yAngle = Math.toDegrees(Math.atan2(e.values[1].toDouble(), e.values[2].toDouble()))
        view.tiltX = xAngle.toFloat()
        view.tiltY = yAngle.toFloat()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    class LevelView(context: Context) : View(context) {
        var tiltX = 0f
            set(v) { field = v; invalidate() }
        var tiltY = 0f
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f - 40f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            c.drawCircle(cx, cy, r, paint)
            c.drawCircle(cx, cy, 30f, paint)
            // Bubble moves opposite to tilt, scaled.
            val bx = cx - (tiltX / 45f * r).coerceIn(-r, r)
            val by = cy + (tiltY / 45f * r).coerceIn(-r, r)
            paint.style = Paint.Style.FILL
            paint.color = if (Math.hypot(tiltX.toDouble(), tiltY.toDouble()) < 1.0) Color.GREEN else Color.RED
            c.drawCircle(bx, by, 44f, paint)
            paint.color = Color.BLACK
            c.drawText("X %.1f°  Y %.1f°".format(tiltX, tiltY), 24f, height - 48f, paint)
        }
    }
}
