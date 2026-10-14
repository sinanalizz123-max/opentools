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
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Step counter and rough calorie estimate, with a manual fallback for missing sensors. */
class CalorieActivity : AppCompatActivity(), SensorEventListener {
    private var steps = 0
    private var weightKg = 70.0
    private lateinit var sensorManager: SensorManager
    private lateinit var stepsView: TextView
    private lateinit var calorieView: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        steps = savedInstanceState?.getInt("steps") ?: 0
        weightKg = savedInstanceState?.getDouble("weightKg") ?: 70.0
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepsView = TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            UiStyle.styleText(this, 36f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 22f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 22f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
        }
        calorieView = TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            UiStyle.styleText(this, 22f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 18f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
        }
        status = TextView(this).apply { UiStyle.styleText(this, 13f, false, UiStyle.muted(context)) }
        val weight = EditText(this).apply {
            hint = "Weight in kg (1–500)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(weightKg.toString())
            UiStyle.styleInput(this)
        }
        val set = NativeButton(this).apply { text = "Update weight" }
        set.setOnClickListener {
            val value = weight.text.toString().trim().toDoubleOrNull()
            if (value == null || !value.isFinite() || value !in 1.0..500.0) {
                weight.error = "Enter a weight from 1 to 500 kg"
                return@setOnClickListener
            }
            weightKg = value
            status.text = "Weight updated. Calorie value is an estimate."
            render()
        }
        val reset = NativeButton(this, false).apply { text = "Reset step count" }
        reset.setOnClickListener { steps = 0; render(); status.text = "Step count reset." }
        render()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Activity overview"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Steps are counted while the sensor is available and this screen is active."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(stepsView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(calorieView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(weight, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(set, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(reset, LinearLayout.LayoutParams(-1, -2))
        }
        try { val g = SparkGraphView(this); g.setRange(0f, 100f); (content as android.widget.LinearLayout).addView(g, android.widget.LinearLayout.LayoutParams(-1, com.opentools.box.UiStyle.dp(this, 160f))); val h = android.widget.TextView(this).apply { text = getPreferences(0).getString("hist", "No history yet") }; (content as android.widget.LinearLayout).addView(h); val goal = android.widget.EditText(this).apply { hint = "Goal / budget"; com.opentools.box.UiStyle.styleInput(this); setText(getPreferences(0).getString("goal","")) }; goal.setOnFocusChangeListener { _, has -> if (!has) getPreferences(0).edit().putString("goal", goal.text.toString()).apply() }; (content as android.widget.LinearLayout).addView(goal) } catch (_: Exception) { }
        setContentView(ToolScaffold.wrap(this, "Calorie Counter", content))
    }

    override fun onResume() {
        super.onResume()
        requestOrRegisterSensor()
    }

    private fun requestOrRegisterSensor() {
        if (android.os.Build.VERSION.SDK_INT >= 29 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_ACTIVITY)
            return
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            status.text = "Step sensor ready. Calorie estimate is approximate."
        } ?: run {
            status.text = "Step sensor unavailable on this device. Step count will not update automatically."
        }
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
        stepsView.text = "$steps\nsteps"
        calorieView.text = "≈ %.1f kcal".format(steps * 0.04 * weightKg / 70.0)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("steps", steps)
        out.putDouble("weightKg", weightKg)
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 26")
    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_ACTIVITY) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestOrRegisterSensor()
        else status.text = "Activity recognition permission denied."
    }

    companion object { private const val REQUEST_ACTIVITY = 1 }
}
