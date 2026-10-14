package com.opentools.box.tools

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlin.math.sin

/** Tone / sound generator (sine, square, sweep). Original implementation. */
class SoundGenActivity : AppCompatActivity() {

    private var track: AudioTrack? = null
    private var playing = false
    private var freq = 440.0
    private var square = false

    private lateinit var freqLabel: TextView
    private lateinit var toggle: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        freqLabel = TextView(this).apply { textSize = 40f; text = "440 Hz" }
        val slider = Slider(this).apply {
            valueFrom = 40f; valueTo = 8000f; value = 440f
            addOnChangeListener { _, v, _ -> freq = v.toDouble(); freqLabel.text = "%.0f Hz".format(freq) }
        }
        val type = MaterialButton(this).apply { text = "Wave: sine" }
        type.setOnClickListener {
            square = !square
            type.text = if (square) "Wave: square" else "Wave: sine"
        }
        toggle = MaterialButton(this).apply { text = "Play" }
        toggle.setOnClickListener {
            if (playing) stopTone() else startTone()
            toggle.text = if (playing) "Stop" else "Play"
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(freqLabel); addView(slider); addView(type); addView(toggle)
        })
    }

    private fun startTone() {
        val rate = 44100
        val minBuf = AudioTrack.getMinBufferSize(rate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack(AudioManager.STREAM_MUSIC, rate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            minBuf, AudioTrack.MODE_STREAM).also { it.play() }
        playing = true
        Thread {
            val buf = ShortArray(2048)
            var phase = 0.0
            while (playing) {
                val step = 2 * Math.PI * freq / rate
                for (i in buf.indices) {
                    val s = sin(phase)
                    buf[i] = ((if (square) Math.signum(s) else s) * 20000).toInt().toShort()
                    phase += step
                    if (phase > 2 * Math.PI) phase -= 2 * Math.PI
                }
                try { track?.write(buf, 0, buf.size) } catch (_: Exception) { break }
            }
        }.start()
    }

    private fun stopTone() {
        playing = false
        try { track?.stop(); track?.release() } catch (_: Exception) { }
        track = null
    }

    override fun onPause() {
        if (playing) { stopTone(); toggle.text = "Play" }
        super.onPause()
    }
}
