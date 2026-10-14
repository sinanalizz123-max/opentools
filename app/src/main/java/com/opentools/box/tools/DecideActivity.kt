package com.opentools.box.tools

import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.random.Random

/** Random decision picker (yes/no, coin, choice list, dice). Original implementation. */
class DecideActivity : AppCompatActivity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { textSize = 40f; gravity = Gravity.CENTER; minLines = 2 }
        val choices = EditText(this).apply { hint = "Choices, one per line (for Pick)" }
        val yesNo = MaterialButton(this).apply { text = "Yes / No" }
        val coin = MaterialButton(this).apply { text = "Coin flip" }
        val dice = MaterialButton(this).apply { text = "Dice (1-6)" }
        val pick = MaterialButton(this).apply { text = "Pick one" }
        yesNo.setOnClickListener { output.text = if (Random.nextBoolean()) "YES" else "NO" }
        coin.setOnClickListener { output.text = if (Random.nextBoolean()) "Heads" else "Tails" }
        dice.setOnClickListener { output.text = Random.nextInt(1, 7).toString() }
        pick.setOnClickListener {
            val list = choices.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
            output.text = if (list.isEmpty()) "Enter choices first" else list.random()
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Decide",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(output); addView(choices)
            addView(yesNo); addView(coin); addView(dice); addView(pick)
        }))
    }
}
