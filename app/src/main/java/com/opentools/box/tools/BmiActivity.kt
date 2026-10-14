package com.opentools.box.tools

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** BMI calculator. Original implementation. */
class BmiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weight = EditText(this).apply { hint = "Weight (kg)"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val height = EditText(this).apply { hint = "Height (cm)"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val result = TextView(this).apply { textSize = 24f }
        val calc = MaterialButton(this).apply { text = "Calculate" }
        calc.setOnClickListener {
            val w = weight.text.toString().toDoubleOrNull()
            val h = height.text.toString().toDoubleOrNull()?.div(100)
            if (w == null || h == null || h <= 0) { result.text = "Enter valid numbers"; return@setOnClickListener }
            val bmi = w / (h * h)
            val cat = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25 -> "Normal"
                bmi < 30 -> "Overweight"
                else -> "Obese"
            }
            result.text = "BMI: %.1f (%s)".format(bmi, cat)
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "BMI",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(weight); addView(height); addView(calc); addView(result)
        }))
    }
}
