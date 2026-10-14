package com.opentools.box.tools

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** BMI calculator with validated inputs and a readable result panel. */
class BmiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weight = EditText(this).apply {
            hint = "Weight in kg"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            UiStyle.styleInput(this)
        }
        val height = EditText(this).apply {
            hint = "Height in cm"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            UiStyle.styleInput(this)
        }
        val result = TextView(this).apply {
            text = "Enter your measurements to calculate BMI."
            UiStyle.styleText(this, 18f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 18f), UiStyle.dp(context, 18f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        val calc = NativeButton(this).apply { text = "Calculate BMI" }
        calc.setOnClickListener {
            val w = weight.text.toString().trim().toDoubleOrNull()
            val h = height.text.toString().trim().toDoubleOrNull()?.div(100.0)
            if (w == null || h == null || !w.isFinite() || !h.isFinite() || w <= 0 || h <= 0) {
                result.text = "Enter a valid positive weight and height."
                return@setOnClickListener
            }
            val bmi = w / (h * h)
            val category = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25 -> "Normal range"
                bmi < 30 -> "Overweight"
                else -> "Obesity range"
            }
            result.text = "Your BMI\n%.1f\n%s".format(bmi, category)
            result.gravity = Gravity.CENTER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Body mass index"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "A simple estimate based on your weight and height."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 20f))
            })
            addView(weight, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 12f) })
            addView(height, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 16f) })
            addView(calc, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 18f) })
            addView(result, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "BMI Calculator", content))
    }
}
