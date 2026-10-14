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
    private var stepSize = 1
    private var current = "default"
    private val counters = mutableMapOf<String, Int>()
    private lateinit var displayView: TextView
    private lateinit var stepInput: android.widget.EditText
    private lateinit var nameInput: android.widget.EditText
    private fun prefs() = getSharedPreferences("counters", android.content.Context.MODE_PRIVATE)
    private fun persist() { prefs().edit().putString("all", org.json.JSONObject(counters as Map<*, *>).toString()).putString("cur", current).putInt("step", stepSize).apply() }
    private fun restore() { try { val o = org.json.JSONObject(prefs().getString("all", "{}")); o.keys().forEach { counters[it] = o.getInt(it) } } catch (_: Exception) {}; current = prefs().getString("cur", "default") ?: "default"; stepSize = prefs().getInt("step", 1).coerceAtLeast(1); count = counters[current] ?: 0 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restore()
        count = savedInstanceState?.getInt("count") ?: count
        displayView = TextView(this).apply {
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 64f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 28f), UiStyle.dp(context, 16f), UiStyle.dp(context, 28f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 22f, UiStyle.border(context))
        }
        stepInput = android.widget.EditText(this).apply { hint = "Step size"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; UiStyle.styleInput(this); setText(stepSize.toString()) }
        nameInput = android.widget.EditText(this).apply { hint = "Counter name"; UiStyle.styleInput(this); setText(current) }
        val plus = NativeButton(this).apply { text = "+ step" }
        val minus = NativeButton(this, false).apply { text = "−1" }
        val reset = NativeButton(this, false).apply { text = "Reset" }
        fun sync() { counters[current] = count; persist() }
        plus.setOnClickListener { val s = stepInput.text.toString().toIntOrNull()?.coerceIn(1, 1000000) ?: stepSize; stepSize = s; count += stepSize; render(); sync() }
        minus.setOnClickListener { val s = stepInput.text.toString().toIntOrNull()?.coerceIn(1, 1000000) ?: stepSize; stepSize = s; count -= stepSize; render(); sync() }
        reset.setOnClickListener { count = 0; render(); sync() }
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
            addView(stepInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiStyle.dp(context, 10f); bottomMargin = UiStyle.dp(context, 8f) })
            addView(nameInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL
                addView(NativeButton(context, false).apply { text = "Add"; setOnClickListener { val n = nameInput.text.toString().trim().ifEmpty { return@setOnClickListener }; current = n; count = counters[n] ?: 0; render(); sync() } }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(NativeButton(context, false).apply { text = "Delete"; setOnClickListener { counters.remove(current); current = counters.keys.firstOrNull() ?: "default"; count = counters[current] ?: 0; nameInput.setText(current); render(); sync() } }, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
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
