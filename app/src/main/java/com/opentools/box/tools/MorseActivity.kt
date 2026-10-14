package com.opentools.box.tools

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Morse code translator + flashlight transmitter. Original implementation. */
class MorseActivity : AppCompatActivity() {

    private val encode = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
        '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....",
        '7' to "--...", '8' to "---..", '9' to "----.", ' ' to "/")
    private val decodeMap = encode.entries.associate { (k, v) -> v to k }

    private lateinit var input: EditText
    private lateinit var output: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var transmitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = EditText(this).apply { hint = "Text or .- Morse" }
        output = TextView(this).apply { textSize = 22f }
        val toMorse = MaterialButton(this).apply { text = "To Morse" }
        val toText = MaterialButton(this).apply { text = "To Text" }
        val flash = MaterialButton(this).apply { text = "Flash it" }
        toMorse.setOnClickListener {
            output.text = input.text.toString().uppercase()
                .map { encode[it] ?: "?" }.joinToString(" ")
        }
        toText.setOnClickListener {
            output.text = input.text.toString().trim().split(Regex("\\s+"))
                .map { if (it == "/") " " else (decodeMap[it]?.toString() ?: "?") }.joinToString("")
        }
        flash.setOnClickListener { transmit(output.text.toString().ifEmpty { input.text.toString() }) }
        val sound = MaterialButton(this).apply { text = "Play as sound" }
        sound.setOnClickListener { transmitSound(output.text.toString().ifEmpty { input.text.toString() }) }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Morse Code",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(input); addView(toMorse); addView(toText); addView(flash); addView(sound); addView(output)
        }))
    }

    private fun transmit(morse: String) {
        val cm = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val id = try { cm.cameraIdList.firstOrNull() } catch (_: Exception) { null } ?: return
        if (transmitting) return
        transmitting = true
        val unit = 200L
        var delay = 0L
        fun at(t: Long, on: Boolean) {
            handler.postDelayed({
                try { cm.setTorchMode(id, on) } catch (_: Exception) { }
            }, t)
        }
        for (ch in morse) {
            when (ch) {
                '.' -> { at(delay, true); delay += unit; at(delay, false); delay += unit }
                '-' -> { at(delay, true); delay += unit * 3; at(delay, false); delay += unit }
                ' ' -> delay += unit * 2
                '/' -> delay += unit * 6
            }
        }
        handler.postDelayed({ transmitting = false }, delay + unit)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        transmitting = false
        stopSound()
        super.onPause()
    }

    private var toneGen: android.media.ToneGenerator? = null

    /** Transmit morse as audible beeps. */
    private fun transmitSound(morse: String) {
        if (transmitting) return
        transmitting = true
        toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
        val unit = 180L
        var delay = 0L
        fun beep(dur: Long) {
            val startAt = delay
            handler.postDelayed({
                try {
                    toneGen?.startTone(android.media.ToneGenerator.TONE_CDMA_DIAL_TONE_LITE,
                        dur.toInt())
                } catch (_: Exception) { }
            }, startAt)
            delay += dur + unit
        }
        for (ch in morse) {
            when (ch) {
                '.' -> beep(unit)
                '-' -> beep(unit * 3)
                ' ' -> delay += unit * 2
                '/' -> delay += unit * 6
            }
        }
        handler.postDelayed({ stopSound(); transmitting = false }, delay + unit)
    }

    private fun stopSound() {
        try { toneGen?.release() } catch (_: Exception) { }
        toneGen = null
    }
}
