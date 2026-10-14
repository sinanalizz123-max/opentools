package com.opentools.box.tools

import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import android.widget.Button

/** Stopwatch with laps, a native interface, and lifecycle-safe elapsed-time tracking. */
class StopwatchActivity : AppCompatActivity() {
    private lateinit var displayView: TextView
    private lateinit var startPause: Button
    private lateinit var lapReset: Button
    private val laps = mutableListOf<String>()
    private val lapTimes = mutableListOf<Long>()
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
        savedInstanceState?.getLongArray("lapTimes")?.forEach { lapTimes.add(it) }
        lapTimes.forEachIndexed { i, total -> val prev = if (i == 0) 0L else lapTimes[i-1]; laps.add("Lap ${i+1}  ·  delta ${format(total - prev)}  ·  total ${format(total)}") }
        laps.reverse()
        displayView = TextView(this).apply {
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 44f, true)
            text = format(baseElapsed)
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 26f), UiStyle.dp(context, 12f), UiStyle.dp(context, 26f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
        }
        startPause = NativeButton(this).apply { text = if (running) "Pause" else "Start" }
        lapReset = NativeButton(this, false).apply { text = if (running) "Lap" else "Reset" }
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
                val now = currentElapsed()
                val prev = lapTimes.lastOrNull() ?: 0L
                lapTimes.add(now)
                laps.add(0, "Lap ${lapTimes.size}  ·  delta ${format(now - prev)}  ·  total ${format(now)}")
                lapAdapter.notifyDataSetChanged()
            } else {
                baseElapsed = 0L
                laps.clear()
                lapTimes.clear()
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
            addView(TextView(context).apply { text = "Lap list"; UiStyle.styleText(this, 13f, true, UiStyle.muted(context)) })
            addView(ScrollView(context).apply { isFillViewport = true; addView(lapList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Stopwatch", content))
        if (running) {
            startStamp = SystemClock.elapsedRealtime()
            displayView.post(ticker)
        }
    }

    override fun onPause() {
        if (running) {
            baseElapsed = currentElapsed()
            startStamp = SystemClock.elapsedRealtime()
        }
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
        out.putLong("elapsed", baseElapsed)
        out.putLongArray("lapTimes", lapTimes.toLongArray())
        out.putBoolean("running", running)
    }
}
