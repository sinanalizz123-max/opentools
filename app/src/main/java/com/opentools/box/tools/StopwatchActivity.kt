package com.opentools.box.tools

import android.os.Bundle
import android.os.SystemClock
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Stopwatch with laps. Original implementation. */
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
        displayView = TextView(this).apply { textSize = 48f; text = format(0L) }
        startPause = Button(this).apply { text = "Start" }
        lapReset = Button(this).apply { text = "Reset" }
        val lapList = ListView(this)
        lapAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, laps)
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
                laps.add(0, "Lap ${laps.size + 1}: ${format(currentElapsed())}")
                lapAdapter.notifyDataSetChanged()
            } else {
                baseElapsed = 0L
                laps.clear()
                lapAdapter.notifyDataSetChanged()
                displayView.text = format(0L)
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Stopwatch",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(displayView)
            addView(startPause)
            addView(lapReset)
            addView(lapList)
        }))
    }

    override fun onPause() {
        super.onPause()
        if (running) {
            baseElapsed = currentElapsed()
            startStamp = SystemClock.elapsedRealtime()
        }
    }

    private fun currentElapsed(): Long =
        baseElapsed + if (running) SystemClock.elapsedRealtime() - startStamp else 0L

    private fun format(ms: Long): String {
        val m = ms / 60000
        val s = (ms % 60000) / 1000
        val cs = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(m, s, cs)
    }
}
