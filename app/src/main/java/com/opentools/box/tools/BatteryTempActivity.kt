package com.opentools.box.tools

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Battery temperature and charge level from Android's battery status broadcast. */
class BatteryTempActivity : AppCompatActivity() {
    private lateinit var tempView: TextView
    private lateinit var levelView: TextView
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tempView = TextView(this).apply {
            text = "— °C"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 48f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 24f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 24f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
        }
        levelView = TextView(this).apply {
            text = "Battery level: —"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 20f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 16f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        statusView = TextView(this).apply {
            text = "Temperature and charge are read from Android."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 12f), 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Battery health snapshot"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(tempView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(levelView, LinearLayout.LayoutParams(-1, -2))
            addView(statusView, LinearLayout.LayoutParams(-1, -2))
        }
        try { val g = SparkGraphView(this); g.setRange(0f, 100f); (content as android.widget.LinearLayout).addView(g, android.widget.LinearLayout.LayoutParams(-1, com.opentools.box.UiStyle.dp(this, 160f))); val h = android.widget.TextView(this).apply { text = getPreferences(0).getString("hist", "No history yet") }; (content as android.widget.LinearLayout).addView(h); val goal = android.widget.EditText(this).apply { hint = "Goal / budget"; com.opentools.box.UiStyle.styleInput(this); setText(getPreferences(0).getString("goal","")) }; goal.setOnFocusChangeListener { _, has -> if (!has) getPreferences(0).edit().putString("goal", goal.text.toString()).apply() }; (content as android.widget.LinearLayout).addView(goal) } catch (_: Exception) { }
        try { val b = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)); val lvl = b?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1; val volt = b?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1) ?: -1; val st = b?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1; val hv = android.widget.TextView(this).apply { text = "Level: " + lvl + "%  Volt: " + volt + "mV  Status: " + st }; (content as android.widget.LinearLayout).addView(hv) } catch (_: Exception) { }
        setContentView(ToolScaffold.wrap(this, "Battery Temp", content))
        refresh()
    }

    private fun refresh() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery == null) {
            tempView.text = "Unavailable"
            levelView.text = "Battery level unavailable"
            statusView.text = "Android did not provide battery status."
            return
        }
        val tempRaw = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        tempView.text = if (tempRaw != Int.MIN_VALUE) "%.1f °C".format(tempRaw / 10.0) else "Unavailable"
        levelView.text = if (level >= 0 && scale > 0) "Battery: ${level * 100 / scale}%" else "Battery level unavailable"
        statusView.text = "Temperature can vary with charging and device workload."
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
