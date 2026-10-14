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
import com.google.android.material.button.MaterialButton
import kotlin.math.tan

/**
 * Height/distance measurer via camera pitch angle + known distance/height.
 * Original implementation.
 */
class MeasureActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var pitchDeg = 0.0
    private lateinit var angleView: TextView
    private lateinit var resultView: TextView
    private lateinit var knownInput: EditText
    private var modeHeight = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        angleView = TextView(this).apply { textSize = 40f }
        resultView = TextView(this).apply { textSize = 24f }
        knownInput = EditText(this).apply {
            hint = "Your eye height (m)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val mode = MaterialButton(this).apply { text = "Mode: object height" }
        mode.setOnClickListener {
            modeHeight = !modeHeight
            mode.text = if (modeHeight) "Mode: object height" else "Mode: distance"
            knownInput.hint = if (modeHeight) "Your eye height (m)" else "Object height (m)"
            render()
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Measure",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(angleView); addView(knownInput); addView(mode); addView(resultView)
        }))
        knownInput.setOnEditorActionListener { _, _, _ -> render(); false }
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
        val orient = FloatArray(3)
        SensorManager.getOrientation(rot, orient)
        pitchDeg = Math.toDegrees(orient[1].toDouble())
        angleView.text = "%.1f°".format(pitchDeg)
        render()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    private fun render() {
        val known = knownInput.text.toString().toDoubleOrNull() ?: return
        val pitch = Math.toRadians(pitchDeg)
        resultView.text = if (modeHeight) {
            // Distance to object via eye height + downward pitch: d = h / tan(-pitch).
            if (pitchDeg >= -1) "Aim down at the base"
            else "Distance ≈ %.2f m".format(known / tan(-pitch))
        } else {
            // Height from distance + upward pitch: h = d * tan(pitch).
            if (pitchDeg <= 1) "Aim up at the top"
            else "Height ≈ %.2f m".format(known * tan(pitch))
        }
    }
}
