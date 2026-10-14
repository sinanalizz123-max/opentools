package com.opentools.box.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.tan

/** Height/distance estimate from device pitch and a known measurement. */
class MeasureActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var pitchDeg = 0.0
    private lateinit var angleView: TextView
    private lateinit var resultView: TextView
    private lateinit var knownInput: EditText
    private lateinit var modeButton: NativeButton
    private var modeHeight = true
    private var hasSensor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        hasSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        angleView = TextView(this).apply {
            text = "—°"
            UiStyle.styleText(this, 42f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 20f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 20f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
        }
        resultView = TextView(this).apply {
            text = if (hasSensor) "Aim your phone and enter a known measurement." else "This device has no rotation sensor."
            UiStyle.styleText(this, 17f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 16f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        knownInput = EditText(this).apply {
            hint = "Eye height in metres"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            UiStyle.styleInput(this)
        }
        modeButton = NativeButton(this, false).apply { text = "Mode: estimate distance" }
        modeButton.setOnClickListener {
            modeHeight = !modeHeight
            modeButton.text = if (modeHeight) "Mode: estimate distance" else "Mode: estimate height"
            knownInput.hint = if (modeHeight) "Eye height in metres" else "Known distance in metres"
            render()
        }
        knownInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Angle measure"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Use the phone's tilt to estimate height or distance."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(angleView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(knownInput, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(modeButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(resultView, LinearLayout.LayoutParams(-1, -2))
        }
        val _sv = android.widget.ScrollView(this).apply { addView(content) }
        try { content.addView(calibSeek("measure", 10f) {}) } catch (_: Exception) { }
        try { addCamToggle(content) } catch (_: Exception) { }
        setContentView(ToolScaffold.wrap(this, "Measure", _sv))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        try { CamPreviewHelper.close() } catch (_: Exception) { }
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotation, orientation)
        pitchDeg = Math.toDegrees(orientation[1].toDouble())
        angleView.text = "%.1f°".format(pitchDeg)
        render()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun render() {
        if (!hasSensor) {
            resultView.text = "This device has no rotation sensor."
            return
        }
        val known = knownInput.text.toString().toDoubleOrNull()
        if (known == null || !known.isFinite() || known <= 0) {
            resultView.text = "Enter a positive measurement in metres."
            return
        }
        val pitch = Math.toRadians(pitchDeg)
        resultView.text = if (modeHeight) {
            if (pitchDeg >= -1) "Tilt the phone down towards the object's base."
            else "Estimated distance\n%.2f m".format(known / tan(-pitch))
        } else {
            if (pitchDeg <= 1) "Tilt the phone up towards the object's top."
            else "Estimated height\n%.2f m".format(known * tan(pitch))
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