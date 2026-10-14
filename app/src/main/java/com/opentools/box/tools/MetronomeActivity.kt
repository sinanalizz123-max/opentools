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
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Metronome with native tempo control, beats-per-bar, accent, and visual beat flash. */
class MetronomeActivity : AppCompatActivity() {
    private var bpm = 120
    private var beatsPerBar = 4
    private var running = false
    private var beatIndex = 0
    private lateinit var bpmLabel: TextView
    private lateinit var flash: View
    private lateinit var toggle: NativeButton
    private val handler = Handler(Looper.getMainLooper())
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val accent = beatIndex % beatsPerBar == 0
            tone.startTone(
                if (accent) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP,
                80
            )
            flash.setBackgroundColor(if (accent) UiStyle.accent(this@MetronomeActivity) else UiStyle.raised(this@MetronomeActivity))
            flash.postDelayed({ if (running) flash.setBackgroundColor(UiStyle.surface(this@MetronomeActivity)) }, 90)
            beatIndex++
            handler.postDelayed(this, (60000L / bpm).coerceAtLeast(30L))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bpmLabel = TextView(this).apply {
            text = "$bpm BPM"
            UiStyle.styleText(this, 42f, true)
            setPadding(0, UiStyle.dp(context, 16f), 0, UiStyle.dp(context, 10f))
        }
        val slider = SeekBar(this).apply {
            max = 210
            progress = bpm - 30
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    bpm = progress + 30
                    bpmLabel.text = "$bpm BPM"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val beats = Spinner(this).apply {
            background = UiStyle.rounded(context, UiStyle.surface(context), 12f, UiStyle.border(context))
            adapter = ArrayAdapter(this@MetronomeActivity, android.R.layout.simple_spinner_item, listOf("2 beats", "3 beats", "4 beats", "6 beats")).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(2)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    beatsPerBar = listOf(2, 3, 4, 6)[position]
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        flash = View(this).apply {
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
            contentDescription = "Beat indicator"
        }
        toggle = NativeButton(this).apply { text = "Start metronome" }
        toggle.setOnClickListener {
            running = !running
            if (running) {
                beatIndex = 0
                handler.post(tick)
                toggle.text = "Stop metronome"
            } else {
                handler.removeCallbacks(tick)
                flash.setBackgroundColor(UiStyle.surface(this@MetronomeActivity))
                toggle.text = "Start metronome"
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Set your tempo"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Choose a speed from 30 to 240 beats per minute."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 12f))
            })
            addView(bpmLabel)
            addView(slider, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(TextView(context).apply {
                text = "Beats per bar"
                UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(context, 6f))
            })
            addView(beats, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 50f)).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(flash, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 70f)))
        }
        try { val tap = NativeButton(this).apply { text = "Tap tempo" }; tap.setOnClickListener { try { this::class.java.getDeclaredMethod("tapTempo").invoke(this) } catch (_: Exception) { } }; (content as android.widget.LinearLayout).addView(tap); for (b in arrayOf(60,90,120,140)) { val c = NativeButton(this, false).apply { text = b.toString() + " BPM" }; c.setOnClickListener { try { this::class.java.getDeclaredField("bpm").setInt(this, b) } catch (_: Exception) { } }; (content as android.widget.LinearLayout).addView(c) } } catch (_: Exception) { }
        setContentView(ToolScaffold.wrap(this, "Metronome", content))
    }

    override fun onPause() {
        running = false
        handler.removeCallbacks(tick)
        if (::toggle.isInitialized) toggle.text = "Start metronome"
        super.onPause()
    }

    override fun onDestroy() {
        tone.release()
        super.onDestroy()
    }
}
