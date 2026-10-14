package com.opentools.box.tools

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.pow

/** EMI calculator with validated values and a clear repayment summary. */
class EmiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun amountField(hintText: String, decimals: Boolean = true) = EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (decimals) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
            UiStyle.styleInput(this)
        }
        val principal = amountField("Loan amount (₹)")
        val rate = amountField("Annual interest rate (%)")
        val years = amountField("Loan tenure (years)", false)
        val result = TextView(this).apply {
            text = "Your monthly payment and total cost will appear here."
            UiStyle.styleText(this, 16f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 18f), UiStyle.dp(context, 18f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        val calc = NativeButton(this).apply { text = "Calculate EMI" }
        calc.setOnClickListener {
            val pRaw = principal.text.toString().trim().toDoubleOrNull()
            val rRaw = rate.text.toString().trim().toDoubleOrNull()
            val yRaw = years.text.toString().trim().toIntOrNull()
            if (pRaw == null || rRaw == null || yRaw == null) {
                result.text = "Enter a positive loan amount, non-negative interest rate, and tenure from 1 to 100 years."
                return@setOnClickListener
            }
            val p: Double = pRaw
            val r: Double = rRaw
            val y: Int = yRaw
            if (!p.isFinite() || !r.isFinite() ||
                p <= 0 || r < 0 || y <= 0 || y > 100) {
                result.text = "Enter a positive loan amount, non-negative interest rate, and tenure from 1 to 100 years."
                return@setOnClickListener
            }
            val monthlyRate = r / 1200.0
            val months = y * 12
            val emi = if (monthlyRate == 0.0) p / months else {
                val factor = (1 + monthlyRate).pow(months)
                p * monthlyRate * factor / (factor - 1)
            }
            if (!emi.isFinite()) {
                result.text = "Unable to calculate this loan. Check the values."
                return@setOnClickListener
            }
            var bal = p; val sched = StringBuilder("Monthly EMI   ₹%.2f\nTotal payable   ₹%.2f\nTotal interest   ₹%.2f\n\nAmortization (per year):\n".format(emi, emi * months, emi * months - p))
            for (yr in 1..y) { var yp = 0.0; var yi = 0.0; for (m in 1..12) { val intr = bal * monthlyRate; var prin = emi - intr; if (bal <= 0) { prin = 0.0 }; bal = (bal - prin).coerceAtLeast(0.0); yp += prin; yi += intr; if (bal <= 0) break }; sched.append("Year %d: principal ₹%.2f interest ₹%.2f balance ₹%.2f\n".format(yr, yp, yi, bal)); if (bal <= 0) break }
            result.text = sched.toString()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Loan calculator"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Estimate your monthly repayment."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 20f))
            })
            listOf(principal, rate, years).forEach { field ->
                addView(field, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 12f) })
            }
            addView(calc, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 4f)
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(result, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "EMI Calculator", content))
    }
}
