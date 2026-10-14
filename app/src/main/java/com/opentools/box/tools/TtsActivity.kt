package com.opentools.box.tools

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.util.Locale

/** Text-to-speech reader using native sliders for pitch and speed. */
class TtsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var ttsReadyCallback: (() -> Unit)? = null
    private lateinit var input: EditText
    private lateinit var statusView: TextView
    private var pitch = 1f
    private var speed = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = EditText(this).apply {
            hint = "Type or paste text to speak"
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            UiStyle.styleInput(this)
        }
        val pitchValue = TextView(this).apply { UiStyle.styleText(this, 14f, true) }
        val speedValue = TextView(this).apply { UiStyle.styleText(this, 14f, true) }
        val pitchSlider = SeekBar(this).apply {
            max = 15
            progress = 5
            contentDescription = "Pitch"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pitch = 0.5f + progress * 0.1f
                    pitchValue.text = "Pitch · %.1fx".format(pitch)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val speedSlider = SeekBar(this).apply {
            max = 15
            progress = 5
            contentDescription = "Speech speed"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    speed = 0.5f + progress * 0.1f
                    speedValue.text = "Speed · %.1fx".format(speed)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        pitchValue.text = "Pitch · 1.0x"
        speedValue.text = "Speed · 1.0x"
        statusView = TextView(this).apply {
            text = "Preparing speech engine…"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val speak = Button(this).apply {
            text = "Speak"
            isEnabled = false
            UiStyle.styleButton(this)
        }
        ttsReadyCallback = {
            speak.isEnabled = true
            statusView.text = "Speech engine ready"
        }
        speak.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                input.error = "Enter some text first"
                return@setOnClickListener
            }
            val engine = tts
            if (engine == null || !ready) {
                statusView.text = "Speech engine is not ready yet."
                return@setOnClickListener
            }
            engine.setPitch(pitch)
            engine.setSpeechRate(speed)
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "opentools-tts")
            statusView.text = if (result == TextToSpeech.SUCCESS) "Speaking…" else "Couldn't start speech."
        }
        val stop = Button(this).apply { text = "Stop speaking"; UiStyle.styleButton(this, false) }
        stop.setOnClickListener {
            tts?.stop()
            statusView.text = "Speech stopped"
        }
        tts = TextToSpeech(this, this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Read text aloud"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Adjust the voice, then tap Speak."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(pitchValue)
            addView(pitchSlider, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(speedValue)
            addView(speedSlider, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(speak, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(stop, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(statusView)
        }
        setContentView(ToolScaffold.wrap(this, "Text to Speech", content))
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale.getDefault())
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                statusView.text = "The device does not support the selected language."
                return
            }
            ready = true
            ttsReadyCallback?.invoke()
        } else {
            statusView.text = "Speech engine failed to initialize."
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
