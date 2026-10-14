package com.opentools.box.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Battery temperature + level. Original implementation. */
class BatteryTempActivity : AppCompatActivity() {

    private lateinit var tempView: TextView
    private lateinit var levelView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tempView = TextView(this).apply { textSize = 52f; gravity = Gravity.CENTER }
        levelView = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Battery Temp",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(tempView); addView(levelView)
        }))
        refresh()
    }

    private fun refresh() {
        val intent: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        tempView.text = "%.1f °C".format(temp)
        levelView.text = if (level >= 0) "Battery: ${level * 100 / scale}%" else ""
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
