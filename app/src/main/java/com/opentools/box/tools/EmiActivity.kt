package com.opentools.box.tools

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.math.pow

/** EMI loan calculator. Original implementation. */
class EmiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val principal = EditText(this).apply { hint = "Loan amount"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val rate = EditText(this).apply { hint = "Annual interest %"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val years = EditText(this).apply { hint = "Years"; inputType = InputType.TYPE_CLASS_NUMBER }
        val result = TextView(this).apply { textSize = 20f }
        val calc = MaterialButton(this).apply { text = "Calculate" }
        calc.setOnClickListener {
            val p = principal.text.toString().toDoubleOrNull()
            val r = rate.text.toString().toDoubleOrNull()
            val y = years.text.toString().toIntOrNull()
            if (p == null || r == null || y == null || y <= 0) { result.text = "Enter valid numbers"; return@setOnClickListener }
            val monthly = r / 1200
            val n = y * 12
            val emi = if (monthly == 0.0) p / n else p * monthly * (1 + monthly).pow(n) / ((1 + monthly).pow(n) - 1)
            result.text = "Monthly EMI: %.2f\nTotal payable: %.2f\nTotal interest: %.2f".format(emi, emi * n, emi * n - p)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(principal); addView(rate); addView(years); addView(calc); addView(result)
        })
    }
}
