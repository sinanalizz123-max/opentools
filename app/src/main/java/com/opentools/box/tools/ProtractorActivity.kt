package com.opentools.box.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.abs
import kotlin.math.min

/** Pitch and roll protractor with a responsive native dial. */
class ProtractorActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var dial: Dial
    private lateinit var label: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        dial = Dial(this)
        label = TextView(this).apply {
            text = "X —°   Y —°   Z —°"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 22f, true)
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 14f))
        }
        status = TextView(this).apply {
            text = "Rotate the phone to measure its angle."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 8f), 0, UiStyle.dp(context, 8f))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dial, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Protractor", content))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            status.text = "Keep the phone steady for a stable reading."
        } ?: run {
            dial.sensorAvailable = false
            status.text = "This device has no rotation-vector sensor."
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotation, orientation)
        val pitch = Math.toDegrees(orientation[1].toDouble())
        val roll = Math.toDegrees(orientation[2].toDouble())
        var azimuth = Math.toDegrees(orientation[0].toDouble())
        if (azimuth < 0) azimuth += 360
        // Dial follows the edge pointing up: use roll in portrait,
        // pitch when the device is turned sideways.
        val sideways = kotlin.math.abs(pitch) > 45
        dial.angle = (if (sideways) pitch else roll).toFloat()
        label.text = "X %.1f°   Y %.1f°   Z %.1f°".format(pitch, roll, azimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    class Dial(context: Context) : View(context) {
        var angle = 0f
            set(value) { field = value; invalidate() }
        var sensorAvailable = true
            set(value) { field = value; invalidate() }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.border(context)
            style = Paint.Style.STROKE
            strokeWidth = UiStyle.dp(context, 2f).toFloat()
        }
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.muted(context)
            strokeWidth = UiStyle.dp(context, 1.5f).toFloat()
        }
        private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.accent(context)
            strokeWidth = UiStyle.dp(context, 3f).toFloat()
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.text(context)
            textSize = UiStyle.dp(context, 16f).toFloat()
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.38f
            canvas.drawCircle(cx, cy, radius, ring)
            if (!sensorAvailable) {
                canvas.drawText("Sensor unavailable", cx, cy + text.textSize, text)
                return
            }
            for (degree in 0 until 360 step 15) {
                val angleRad = Math.toRadians(degree.toDouble())
                val inner = radius * if (degree % 45 == 0) 0.82f else 0.9f
                canvas.drawLine(
                    cx + (inner * kotlin.math.cos(angleRad)).toFloat(),
                    cy + (inner * kotlin.math.sin(angleRad)).toFloat(),
                    cx + (radius * kotlin.math.cos(angleRad)).toFloat(),
                    cy + (radius * kotlin.math.sin(angleRad)).toFloat(), tick
                )
            }
            canvas.save()
            canvas.rotate(angle, cx, cy)
            canvas.drawLine(cx - radius * 0.75f, cy, cx + radius * 0.75f, cy, needle)
            canvas.drawCircle(cx, cy, UiStyle.dp(context, 5f).toFloat(), needle)
            canvas.restore()
            canvas.drawText("%.1f°".format(abs(angle)), cx, cy + radius + UiStyle.dp(context, 28f), text)
        }
    }
}
