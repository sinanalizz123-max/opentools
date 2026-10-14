package com.opentools.box.tools

import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Countdown timer using native Android controls and a clear progress display. */
class TimerActivity : AppCompatActivity() {
    private var countdown: CountDownTimer? = null
    private var totalMs = 0L
    private var running = false
    private lateinit var displayView: TextView
    private lateinit var bar: ProgressBar
    private lateinit var toggle: Button
    private lateinit var hours: NumberPicker
    private lateinit var minutes: NumberPicker
    private lateinit var seconds: NumberPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayView = TextView(this).apply {
            text = "00:00:00"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 42f, true)
            setPadding(0, UiStyle.dp(context, 22f), 0, UiStyle.dp(context, 18f))
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(UiStyle.border(context))
        }
        hours = picker(23)
        minutes = picker(59)
        seconds = picker(59)
        toggle = NativeButton(this).apply {
            text = "Start timer"
        }
        toggle.setOnClickListener { if (running) cancel() else start() }

        val pickerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            listOf("Hours" to hours, "Minutes" to minutes, "Seconds" to seconds).forEach { (label, picker) ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(TextView(context).apply {
                        text = label
                        gravity = Gravity.CENTER
                        UiStyle.styleText(this, 12f, false, UiStyle.muted(context))
                    })
                    addView(picker, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 140f)))
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Set a duration"
                UiStyle.styleText(this, 24f, true)
            })
            addView(TextView(context).apply {
                text = "Choose hours, minutes, and seconds."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 12f))
            })
            addView(displayView, LinearLayout.LayoutParams(-1, -2))
            addView(bar, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 6f)).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(pickerRow, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Timer", content))
    }

    private fun picker(max: Int) = NumberPicker(this).apply {
        minValue = 0
        maxValue = max
        wrapSelectorWheel = true
        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
    }

    private fun start() {
        totalMs = (hours.value * 3600L + minutes.value * 60L + seconds.value) * 1000L
        if (totalMs <= 0L) {
            displayView.text = "Choose a duration"
            return
        }
        running = true
        toggle.text = "Cancel timer"
        hours.isEnabled = false
        minutes.isEnabled = false
        seconds.isEnabled = false
        countdown = object : CountDownTimer(totalMs, 100) {
            override fun onTick(left: Long) {
                val wholeSeconds = left / 1000
                displayView.text = "%02d:%02d:%02d".format(
                    wholeSeconds / 3600, (wholeSeconds % 3600) / 60, wholeSeconds % 60
                )
                bar.progress = (1000 * (totalMs - left) / totalMs).toInt()
            }

            override fun onFinish() {
                displayView.text = "Time's up!"
                bar.progress = 1000
                running = false
                toggle.text = "Start timer"
                setPickersEnabled(true)
            }
        }.start()
    }

    private fun cancel() {
        countdown?.cancel()
        countdown = null
        running = false
        totalMs = 0L
        toggle.text = "Start timer"
        displayView.text = "00:00:00"
        bar.progress = 0
        setPickersEnabled(true)
    }

    private fun setPickersEnabled(enabled: Boolean) {
        hours.isEnabled = enabled
        minutes.isEnabled = enabled
        seconds.isEnabled = enabled
    }

    override fun onDestroy() {
        countdown?.cancel()
        countdown = null
        super.onDestroy()
    }
}
