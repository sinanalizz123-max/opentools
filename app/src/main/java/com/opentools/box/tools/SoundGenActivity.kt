package com.opentools.box.tools

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.sin

/** Tone generator with native frequency control and sine/square wave selection. */
class SoundGenActivity : AppCompatActivity() {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var playing = false
    @Volatile private var freq = 440.0
    @Volatile private var square = false
    private lateinit var freqLabel: TextView
    private lateinit var status: TextView
    private lateinit var toggle: NativeButton
    private lateinit var typeButton: NativeButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        freqLabel = TextView(this).apply {
            text = "440 Hz"
            UiStyle.styleText(this, 42f, true)
            setPadding(0, UiStyle.dp(context, 18f), 0, UiStyle.dp(context, 10f))
        }
        val slider = SeekBar(this).apply {
            max = 7960
            progress = 400
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            contentDescription = "Tone frequency in hertz"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    freq = (progress + 40).toDouble()
                    freqLabel.text = "%.0f Hz".format(freq)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        typeButton = NativeButton(this, false).apply { text = "Waveform: Sine" }
        typeButton.setOnClickListener {
            square = !square
            typeButton.text = if (square) "Waveform: Square" else "Waveform: Sine"
        }
        status = TextView(this).apply {
            text = "Choose a frequency, then play the tone."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 12f), 0, UiStyle.dp(context, 12f))
        }
        toggle = NativeButton(this).apply { text = "Play tone" }
        toggle.setOnClickListener {
            if (playing) stopTone() else startTone()
            toggle.text = if (playing) "Stop tone" else "Play tone"
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Generate a tone"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Adjust the pitch from 40 Hz to 8,000 Hz."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 12f))
            })
            addView(freqLabel)
            addView(slider, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(typeButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 6f)
            })
            addView(status)
        }
        setContentView(ToolScaffold.wrap(this, "Tone Generator", content))
    }

    private fun startTone() {
        val sampleRate = 44100
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            status.text = "Audio output is not available on this device."
            return
        }
        try {
            val audio = AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, 4096), AudioTrack.MODE_STREAM
            )
            if (audio.state != AudioTrack.STATE_INITIALIZED) {
                audio.release()
                status.text = "Couldn't initialize audio output."
                return
            }
            track = audio
            audio.play()
            playing = true
            status.text = "Playing at %.0f Hz".format(freq)
            Thread {
                val buffer = ShortArray(2048)
                var phase = 0.0
                while (playing) {
                    val step = 2 * Math.PI * freq / sampleRate
                    for (index in buffer.indices) {
                        val sine = sin(phase)
                        buffer[index] = ((if (square) Math.signum(sine) else sine) * 20000).toInt().toShort()
                        phase += step
                        if (phase >= 2 * Math.PI) phase -= 2 * Math.PI
                    }
                    try {
                        val current = track ?: break
                        current.write(buffer, 0, buffer.size)
                    } catch (_: Exception) {
                        break
                    }
                }
            }.start()
        } catch (error: Exception) {
            stopTone()
            status.text = "Couldn't start audio: " + (error.message ?: "audio unavailable")
        }
    }

    private fun stopTone() {
        playing = false
        try { track?.pause(); track?.flush(); track?.stop(); track?.release() } catch (_: Exception) { }
        track = null
        if (::status.isInitialized) status.text = "Tone stopped"
    }

    override fun onPause() {
        if (playing) {
            stopTone()
            if (::toggle.isInitialized) toggle.text = "Play tone"
        }
        super.onPause()
    }
}
