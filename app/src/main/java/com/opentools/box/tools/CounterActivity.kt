package com.opentools.box.tools

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Native tally counter with increment, decrement, reset, and rotation-safe state. */
class CounterActivity : AppCompatActivity() {
    private var count = 0
    private lateinit var displayView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        count = savedInstanceState?.getInt("count") ?: 0
        displayView = TextView(this).apply {
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 64f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 28f), UiStyle.dp(context, 16f), UiStyle.dp(context, 28f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 22f, UiStyle.border(context))
        }
        val plus = NativeButton(this).apply { text = "+1" }
        val minus = NativeButton(this, false).apply { text = "−1" }
        val reset = NativeButton(this, false).apply { text = "Reset" }
        plus.setOnClickListener { count++; render() }
        minus.setOnClickListener { count--; render() }
        reset.setOnClickListener { count = 0; render() }
        render()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Tap to keep count"
                UiStyle.styleText(this, 15f, false, UiStyle.muted(context))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(displayView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 24f) })
            addView(plus, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 10f) })
            addView(minus, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 10f) })
            addView(reset, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Counter", content))
    }

    private fun render() { displayView.text = count.toString() }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("count", count)
    }
}
