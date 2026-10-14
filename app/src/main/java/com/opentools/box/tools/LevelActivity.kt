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
import kotlin.math.hypot
import kotlin.math.min

/** Responsive bubble level using the accelerometer. */
class LevelActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var levelView: LevelView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        levelView = LevelView(this)
        status = TextView(this).apply {
            text = "Place your phone on a flat surface."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 10f))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(levelView, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        val _sv = android.widget.ScrollView(this).apply { addView(content) }
        try { content.addView(calibSeek("level", 10f) {}) } catch (_: Exception) { }
        try { addCamToggle(content) } catch (_: Exception) { }
        setContentView(ToolScaffold.wrap(this, "Bubble Level", _sv))
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            levelView.sensorAvailable = true
            status.text = "Green bubble means the phone is level."
        } else {
            levelView.sensorAvailable = false
            status.text = "This device has no accelerometer."
        }
    }

    override fun onPause() {
        try { CamPreviewHelper.close() } catch (_: Exception) { }
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val xAngle = Math.toDegrees(kotlin.math.atan2(event.values[0].toDouble(), event.values[2].toDouble()))
        val yAngle = Math.toDegrees(kotlin.math.atan2(event.values[1].toDouble(), event.values[2].toDouble()))
        levelView.tiltX = xAngle.toFloat()
        levelView.tiltY = yAngle.toFloat()
        status.text = if (hypot(xAngle, yAngle) < 1.0) "Level · within 1°" else "Tilt X %.1f° · Y %.1f°".format(xAngle, yAngle)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    class LevelView(context: Context) : View(context) {
        var tiltX = 0f
            set(value) { field = value; invalidate() }
        var tiltY = 0f
            set(value) { field = value; invalidate() }
        var sensorAvailable = true
            set(value) { field = value; invalidate() }

        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.border(context)
            style = Paint.Style.STROKE
            strokeWidth = UiStyle.dp(context, 2f).toFloat()
        }
        private val bubble = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.text(context)
            textSize = UiStyle.dp(context, 16f).toFloat()
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.36f
            canvas.drawCircle(cx, cy, radius, ring)
            canvas.drawCircle(cx, cy, radius * 0.15f, ring)
            if (!sensorAvailable) {
                canvas.drawText("Sensor unavailable", cx, cy + radius + UiStyle.dp(context, 30f), text)
                return
            }
            val bx = cx - (tiltX / 45f * radius).coerceIn(-radius, radius)
            val by = cy + (tiltY / 45f * radius).coerceIn(-radius, radius)
            bubble.color = if (hypot(tiltX.toDouble(), tiltY.toDouble()) < 1.0) 0xFF2E9D69.toInt() else UiStyle.accent(context)
            canvas.drawCircle(bx, by, radius * 0.13f, bubble)
            canvas.drawText("X %.1f°   Y %.1f°".format(tiltX, tiltY), cx, cy + radius + UiStyle.dp(context, 30f), text)
        }
    }

    private fun calibOf(tool: String): Float { return getPreferences(android.content.Context.MODE_PRIVATE).getFloat(tool + "_calib", 0f) }
    private fun calibSeek(tool: String, range: Float, onCh: () -> Unit): android.widget.SeekBar {
        val prefs = getPreferences(android.content.Context.MODE_PRIVATE)
        return android.widget.SeekBar(this).apply {
            max = 200; progress = ((prefs.getFloat(tool + "_calib", 0f) / range * 100) + 100).toInt().coerceIn(0, 200)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) { prefs.edit().putFloat(tool + "_calib", (p - 100) / 100f * range).apply(); onCh() }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
            })
        }
    }
    private var camView: android.view.TextureView? = null
    private var camToggle: NativeButton? = null
    private fun addCamToggle(content: android.widget.LinearLayout) {
        camToggle = NativeButton(this, false).apply { text = "Camera overlay: off" }
        camView = android.view.TextureView(this).apply { visibility = android.view.View.GONE; layoutParams = android.widget.LinearLayout.LayoutParams(-1, com.opentools.box.UiStyle.dp(context, 180f)) }
        camToggle!!.setOnClickListener {
            val on = camView!!.visibility != android.view.View.VISIBLE
            camView!!.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
            camToggle!!.text = if (on) "Camera overlay: on" else "Camera overlay: off"
            if (on) CamPreviewHelper.open(this, camView!!) else CamPreviewHelper.close()
        }
        content.addView(camToggle); content.addView(camView)
    }

}