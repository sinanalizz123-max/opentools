package com.opentools.box.tools

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.random.Random

/** Random integer generator with bounded output and validated ranges. */
class RandomDigitsActivity : AppCompatActivity() {
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun numberField(label: String, signed: Boolean) = EditText(this).apply {
            hint = label
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
            UiStyle.styleInput(this)
        }
        val min = numberField("Minimum value", true)
        val max = numberField("Maximum value", true)
        val count = numberField("How many numbers? (1–100)", false)
        output = TextView(this).apply {
            text = "Generated numbers will appear here."
            UiStyle.styleText(this, 18f, true)
            gravity = Gravity.CENTER
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 18f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        val diceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL
            listOf(4, 6, 8, 10, 12, 20, 100).forEach { sides ->
                addView(NativeButton(context, false).apply { text = "d$sides"; setOnClickListener { output.text = "d$sides: " + kotlin.random.Random.nextInt(1, sides + 1).toString(); output.gravity = Gravity.CENTER } }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        val go = Button(this).apply { text = "Generate numbers"; UiStyle.styleButton(this) }
        go.setOnClickListener {
            val low = min.text.toString().trim().toIntOrNull() ?: 1
            val high = max.text.toString().trim().toIntOrNull() ?: 100
            val amount = count.text.toString().trim().toIntOrNull() ?: 1
            if (low > high) {
                output.text = "Minimum must be less than or equal to maximum."
                return@setOnClickListener
            }
            if (amount !in 1..100) {
                output.text = "Choose between 1 and 100 numbers."
                return@setOnClickListener
            }
            output.text = (1..amount).map {
                Random.nextLong(low.toLong(), high.toLong() + 1L).toString()
            }.joinToString("\n")
            output.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Random number generator"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Set a range and generate up to 100 random integers."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            listOf(min, max, count).forEach { field ->
                addView(field, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(context, 10f)
                })
            }
            addView(diceRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(go, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 4f)
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(ScrollView(context).apply { isFillViewport = true; addView(output) },
                LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Random Numbers", content))
    }
}
