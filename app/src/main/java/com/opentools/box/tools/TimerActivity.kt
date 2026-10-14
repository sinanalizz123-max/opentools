package com.opentools.box.tools

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Countdown timer with h/m/s pickers. Original implementation. */
class TimerActivity : AppCompatActivity() {

    private var countdown: CountDownTimer? = null
    private var totalMs = 0L
    private var running = false
    private lateinit var displayView: TextView
    private lateinit var bar: LinearProgressIndicator
    private lateinit var toggle: MaterialButton
    private lateinit var hours: NumberPicker
    private lateinit var minutes: NumberPicker
    private lateinit var seconds: NumberPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayView = TextView(this).apply { textSize = 52f; text = "00:00:00" }
        bar = LinearProgressIndicator(this).apply { max = 1000 }
        hours = NumberPicker(this).apply { minValue = 0; maxValue = 23 }
        minutes = NumberPicker(this).apply { minValue = 0; maxValue = 59 }
        seconds = NumberPicker(this).apply { minValue = 0; maxValue = 59 }
        toggle = MaterialButton(this).apply { text = "Start" }
        toggle.setOnClickListener { if (running) cancel() else start() }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Timer",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(displayView)
            addView(bar)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(hours); addView(minutes); addView(seconds)
            })
            addView(toggle)
        }))
    }

    private fun start() {
        totalMs = ((hours.value * 3600L + minutes.value * 60L + seconds.value) * 1000)
        if (totalMs <= 0) return
        running = true
        toggle.text = "Cancel"
        countdown = object : CountDownTimer(totalMs, 50) {
            override fun onTick(left: Long) {
                val s = left / 1000
                displayView.text = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
                bar.setProgressCompat((1000 * (totalMs - left) / totalMs).toInt(), false)
            }
            override fun onFinish() {
                displayView.text = "Done!"
                bar.setProgressCompat(1000, true)
                running = false
                toggle.text = "Start"
            }
        }.start()
    }

    private fun cancel() {
        countdown?.cancel()
        running = false
        toggle.text = "Start"
        displayView.text = "00:00:00"
        bar.setProgressCompat(0, false)
    }

    override fun onPause() {
        super.onPause()
        // Keep counting in background; cancel only on explicit cancel.
    }
}
