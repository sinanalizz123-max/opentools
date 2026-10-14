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

/** Calorie + step counter (step-detector sensor with manual fallback). */
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
            weight.text.toString().toDoubleOrNull()?.takeIf { it in 1.0..500.0 }?.let { weightKg = it }
            render()
        }
        val reset = MaterialButton(this).apply { text = "Reset steps" }
        reset.setOnClickListener { steps = 0; render() }
        render()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Calorie Counter", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(stepsView); addView(calorieView); addView(weight); addView(set); addView(reset)
        }))
    }

    override fun onResume() {
        super.onResume()
        requestOrRegisterSensor()
    }

    private fun requestOrRegisterSensor() {
        // ACTIVITY_RECOGNITION is an API-29 runtime permission; older releases
        // grant it at install time so no request is needed there.
        if (android.os.Build.VERSION.SDK_INT >= 29 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
            return
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run { calorieView.text = "Step sensor unavailable" }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        steps += event.values[0].toInt()
        render()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun render() {
        stepsView.text = steps.toString() + " steps"
        calorieView.text = "≈ %.1f kcal".format(steps * 0.04 * weightKg / 70.0)
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 26")
    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestOrRegisterSensor()
        else calorieView.text = "Activity permission denied"
    }
}
