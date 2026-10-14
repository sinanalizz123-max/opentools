package com.opentools.box.tools

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.util.Locale

/** Text-to-speech reader with pitch/speed. Original implementation. */
class TtsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var ttsReadyCallback: (() -> Unit)? = null
    private lateinit var input: EditText
    private var pitch = 1f
    private var speed = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = EditText(this).apply { hint = "Type text to speak"; minLines = 4 }
        val pitchSlider = Slider(this).apply {
            valueFrom = 0.5f; valueTo = 2f; value = 1f; stepSize = 0.1f
            addOnChangeListener { _, v, _ -> pitch = v }
        }
        val speedSlider = Slider(this).apply {
            valueFrom = 0.5f; valueTo = 2f; value = 1f; stepSize = 0.1f
            addOnChangeListener { _, v, _ -> speed = v }
        }
        val speak = MaterialButton(this).apply { text = "Speak"; isEnabled = false }
        ttsReadyCallback = { speak.isEnabled = true }
        speak.setOnClickListener {
            tts?.let {
                it.setPitch(pitch)
                it.setSpeechRate(speed)
                it.speak(input.text.toString(), TextToSpeech.QUEUE_FLUSH, null, "tts1")
            }
        }
        val stop = MaterialButton(this).apply { text = "Stop" }
        stop.setOnClickListener { tts?.stop() }
        tts = TextToSpeech(this, this)
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Text to Speech",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(input); addView(pitchSlider); addView(speedSlider); addView(speak); addView(stop)
        }))
        speak.tag = "speak"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            ready = true
            ttsReadyCallback?.invoke()
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
