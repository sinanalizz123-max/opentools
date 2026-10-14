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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Responsive compass using the rotation-vector sensor. */
class CompassActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var dial: DialView
    private lateinit var heading: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        dial = DialView(this)
        heading = TextView(this).apply {
            text = "—°"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 28f, true)
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 14f))
        }
        status = TextView(this).apply {
            text = "Keep the phone flat and away from magnetic interference."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(dial, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(heading, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 6f)
                bottomMargin = UiStyle.dp(context, 8f)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Compass", content))
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            status.text = "Keep the phone flat and away from magnetic interference."
        } else {
            heading.text = "Unavailable"
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
        var azimuth = Math.toDegrees(orientation[0].toDouble())
        if (azimuth < 0) azimuth += 360
        dial.angle = azimuth.toFloat()
        heading.text = "${azimuth.roundToInt()}°  ${directionName(azimuth)}"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun directionName(azimuth: Double): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((azimuth + 22.5) / 45).toInt() % 8]
    }

    private class DialView(context: Context) : View(context) {
        var angle = 0f
            set(value) { field = value; invalidate() }
        private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.surface(context)
            style = Paint.Style.FILL
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.border(context)
            style = Paint.Style.STROKE
            strokeWidth = UiStyle.dp(context, 2f).toFloat()
        }
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.muted(context)
            strokeWidth = UiStyle.dp(context, 1.5f).toFloat()
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.text(context)
            textSize = UiStyle.dp(context, 18f).toFloat()
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UiStyle.accent(context) }
        private val triangle = android.graphics.Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.42f
            canvas.drawCircle(cx, cy, radius, face)
            canvas.drawCircle(cx, cy, radius, ring)
            canvas.save()
            canvas.rotate(-angle, cx, cy)
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val major = i % 5 == 0
                val inner = radius * if (major) 0.82f else 0.89f
                val x1 = cx + sin(a).toFloat() * inner
                val y1 = cy - cos(a).toFloat() * inner
                val x2 = cx + sin(a).toFloat() * radius * 0.96f
                val y2 = cy - cos(a).toFloat() * radius * 0.96f
                tick.strokeWidth = UiStyle.dp(context, if (major) 2f else 1f).toFloat()
                canvas.drawLine(x1, y1, x2, y2, tick)
            }
            val labels = arrayOf("N", "E", "S", "W")
            labels.forEachIndexed { index, label ->
                val a = Math.toRadians(index * 90.0)
                val x = cx + sin(a).toFloat() * radius * 0.68f
                val y = cy - cos(a).toFloat() * radius * 0.68f + text.textSize * 0.35f
                text.color = if (index == 0) UiStyle.accent(context) else UiStyle.text(context)
                canvas.drawText(label, x, y, text)
            }
            canvas.restore()
            val tipY = cy - radius * 0.58f
            val baseY = cy + radius * 0.12f
            val halfWidth = radius * 0.11f
            val triangle = this.triangle.apply {
                reset()
                moveTo(cx, tipY)
                lineTo(cx - halfWidth, baseY)
                lineTo(cx + halfWidth, baseY)
                close()
            }
            canvas.drawPath(triangle, needle)
            canvas.drawCircle(cx, cy, radius * 0.045f, needle)
        }
    }
}
