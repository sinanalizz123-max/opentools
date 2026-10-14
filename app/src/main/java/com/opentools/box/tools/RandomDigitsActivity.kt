package com.opentools.box.tools

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.random.Random

/** Random number / digit generator. Original implementation. */
class RandomDigitsActivity : AppCompatActivity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val min = EditText(this).apply { hint = "Min"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val max = EditText(this).apply { hint = "Max"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val count = EditText(this).apply { hint = "Count"; inputType = InputType.TYPE_CLASS_NUMBER }
        output = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER }
        val go = MaterialButton(this).apply { text = "Generate" }
        go.setOnClickListener {
            val lo = min.text.toString().toIntOrNull() ?: 1
            val hi = max.text.toString().toIntOrNull() ?: 100
            val n = (count.text.toString().toIntOrNull() ?: 1).coerceIn(1, 100)
            if (lo > hi) { output.text = "Min must be ≤ max"; return@setOnClickListener }
            output.text = (1..n).map { Random.nextInt(lo, hi + 1).toString() }.joinToString("\n")
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Random Digits",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(min); addView(max); addView(count); addView(go); addView(output)
        }))
    }
}
