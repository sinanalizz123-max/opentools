package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton

/** Calorie + step counter (step-detector sensor with manual fallback). Original implementation. */
class CalorieActivity : AppCompatActivity(), SensorEventListener {

    private var steps = 0
    private var weightKg = 70.0
    private lateinit var sensorManager: SensorManager
    private lateinit var stepsView: TextView
    private lateinit var calorieView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepsView = TextView(this).apply { textSize = 48f }
        calorieView = TextView(this).apply { textSize = 24f }
        val weight = EditText(this).apply {
            hint = "Weight (kg)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val set = MaterialButton(this).apply { text = "Set weight" }
        set.setOnClickListener {
            weight.text.toString().toDoubleOrNull()?.let { weightKg = it }
            render()
        }
        val reset = MaterialButton(this).apply { text = "Reset steps" }
        reset.setOnClickListener { steps = 0; render() }
        render()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Calorie Counter",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(stepsView); addView(calorieView); addView(weight); addView(set); addView(reset)
        }))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            == PackageManager.PERMISSION_GRANTED) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        steps += e.values[0].toInt()
        render()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    private fun render() {
        stepsView.text = "$steps steps"
        // ~0.04 kcal per step for a 70kg adult, scaled by weight.
        calorieView.text = "≈ %.1f kcal".format(steps * 0.04 * weightKg / 70.0)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        onResume()
    }
}
