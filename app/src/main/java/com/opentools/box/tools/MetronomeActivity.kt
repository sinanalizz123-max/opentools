package com.opentools.box.tools

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

/** Metronome with BPM slider, beats-per-bar, accent + flash. Original implementation. */
class MetronomeActivity : AppCompatActivity() {

    private var bpm = 120
    private var beatsPerBar = 4
    private var running = false
    private var beatIndex = 0

    private lateinit var bpmLabel: TextView
    private lateinit var flash: View
    private lateinit var toggle: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val accent = beatIndex % beatsPerBar == 0
            tone.startTone(if (accent) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP, 80)
            flash.setBackgroundColor(if (accent) 0xFF1B6EF3.toInt() else 0xFF9EC1FF.toInt())
            flash.postDelayed({ if (running) flash.setBackgroundColor(0xFFE3E3E3.toInt()) }, 90)
            beatIndex++
            handler.postDelayed(this, (60000L / bpm).coerceAtLeast(30L))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bpmLabel = TextView(this).apply { textSize = 44f; text = "$bpm BPM" }
        val slider = Slider(this).apply {
            valueFrom = 30f; valueTo = 240f; value = bpm.toFloat(); stepSize = 1f
            addOnChangeListener { _, v, _ -> bpm = v.toInt(); bpmLabel.text = "$bpm BPM" }
        }
        val beats = Spinner(this).apply {
            adapter = ArrayAdapter(this@MetronomeActivity,
                android.R.layout.simple_spinner_item, listOf("2", "3", "4", "6"))
            setSelection(2)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    beatsPerBar = listOf(2, 3, 4, 6)[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) = Unit
            }
        }
        flash = View(this).apply { setBackgroundColor(0xFFE3E3E3.toInt()) }
        toggle = MaterialButton(this).apply { text = "Start" }
        toggle.setOnClickListener {
            running = !running
            if (running) {
                beatIndex = 0
                handler.post(tick)
                toggle.text = "Stop"
            } else {
                handler.removeCallbacks(tick)
                toggle.text = "Start"
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Metronome",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(bpmLabel)
            addView(slider)
            addView(beats)
            addView(toggle)
            addView(flash, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, pad * 4))
        }))
    }

    override fun onPause() {
        running = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        tone.release()
        super.onDestroy()
    }
}
