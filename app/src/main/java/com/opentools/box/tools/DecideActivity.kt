package com.opentools.box.tools

import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.random.Random

/** Random decision picker for yes/no, coin flips, dice, and custom choices. */
class DecideActivity : AppCompatActivity() {
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            text = "Let chance decide"
            textSize = 34f
            gravity = Gravity.CENTER
            minLines = 2
            UiStyle.styleText(this, 34f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 26f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 26f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
        }
        val choices = EditText(this).apply {
            hint = "Enter choices, one per line"
            minLines = 3
            UiStyle.styleInput(this)
        }
        val yesNo = NativeButton(this).apply { text = "Yes or No" }
        val coin = NativeButton(this, false).apply { text = "Flip a coin" }
        val dice = NativeButton(this, false).apply { text = "Roll a die (1–6)" }
        val pick = NativeButton(this).apply { text = "Pick one choice" }
        yesNo.setOnClickListener { output.text = if (Random.nextBoolean()) "YES" else "NO" }
        coin.setOnClickListener { output.text = if (Random.nextBoolean()) "Heads" else "Tails" }
        dice.setOnClickListener { output.text = Random.nextInt(1, 7).toString() }
        pick.setOnClickListener {
            val options = choices.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
            output.text = if (options.isEmpty()) {
                choices.error = "Add at least one choice"
                "Enter choices first"
            } else options.random()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Make a decision"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Choose a random answer or let the app pick from your list."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(output, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(yesNo, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(coin, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(dice, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 18f) })
            addView(choices, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 10f) })
            addView(pick, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Decision Picker", content))
    }
}
