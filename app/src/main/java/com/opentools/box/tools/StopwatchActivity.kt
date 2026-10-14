package com.opentools.box.tools

import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Stopwatch with laps, a native interface, and lifecycle-safe elapsed-time tracking. */
class StopwatchActivity : AppCompatActivity() {
    private lateinit var displayView: TextView
    private lateinit var startPause: Button
    private lateinit var lapReset: Button
    private val laps = mutableListOf<String>()
    private lateinit var lapAdapter: ArrayAdapter<String>
    private var running = false
    private var baseElapsed = 0L
    private var startStamp = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            displayView.text = format(currentElapsed())
            displayView.postDelayed(this, 31)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseElapsed = savedInstanceState?.getLong("elapsed") ?: 0L
        running = savedInstanceState?.getBoolean("running") ?: false
        displayView = TextView(this).apply {
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 44f, true)
            text = format(baseElapsed)
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 26f), UiStyle.dp(context, 12f), UiStyle.dp(context, 26f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
        }
        startPause = Button(this).apply { text = if (running) "Pause" else "Start"; UiStyle.styleButton(this) }
        lapReset = Button(this).apply { text = if (running) "Lap" else "Reset"; UiStyle.styleButton(this, false) }
        val lapList = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        lapAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, laps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(this@StopwatchActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 15f, false)
                row.setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 14f), UiStyle.dp(context, 12f), UiStyle.dp(context, 14f))
                return row
            }
        }
        lapList.adapter = lapAdapter

        startPause.setOnClickListener {
            if (running) {
                baseElapsed = currentElapsed()
                running = false
                displayView.removeCallbacks(ticker)
                startPause.text = "Start"
                lapReset.text = "Reset"
            } else {
                startStamp = SystemClock.elapsedRealtime()
                running = true
                displayView.post(ticker)
                startPause.text = "Pause"
                lapReset.text = "Lap"
            }
        }
        lapReset.setOnClickListener {
            if (running) {
                laps.add(0, "Lap ${laps.size + 1}  ·  ${format(currentElapsed())}")
                lapAdapter.notifyDataSetChanged()
            } else {
                baseElapsed = 0L
                laps.clear()
                lapAdapter.notifyDataSetChanged()
                displayView.text = format(0L)
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(displayView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 16f) })
            addView(startPause, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(lapReset, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 20f) })
            addView(TextView(context).apply {
                text = "Laps"
                UiStyle.styleText(this, 18f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 8f))
            })
            addView(lapList, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Stopwatch", content))
        if (running) {
            startStamp = SystemClock.elapsedRealtime()
            displayView.post(ticker)
        }
    }

    override fun onPause() {
        if (running) baseElapsed = currentElapsed()
        displayView.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (running) {
            startStamp = SystemClock.elapsedRealtime()
            displayView.post(ticker)
        }
    }

    private fun currentElapsed(): Long =
        baseElapsed + if (running) SystemClock.elapsedRealtime() - startStamp else 0L

    private fun format(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val centiseconds = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putLong("elapsed", currentElapsed())
        out.putBoolean("running", running)
    }
}
