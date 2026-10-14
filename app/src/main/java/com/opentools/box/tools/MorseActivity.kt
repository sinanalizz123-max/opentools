package com.opentools.box.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Morse translator with flashlight and audible transmission using native controls. */
class MorseActivity : AppCompatActivity() {
    private val encode = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
        '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....",
        '7' to "--...", '8' to "---..", '9' to "----.", ' ' to "/"
    )
    private val decodeMap = encode.entries.associate { (character, morse) -> morse to character }
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var transmitting = false
    private var toneGen: android.media.ToneGenerator? = null
    private var cameraManager: CameraManager? = null
    private var flashId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        flashId = try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
        input = EditText(this).apply {
            hint = "Enter text or Morse (.-)"
            minLines = 2
            UiStyle.styleInput(this)
        }
        output = TextView(this).apply {
            text = "Encoded or decoded text will appear here."
            gravity = Gravity.CENTER_VERTICAL
            UiStyle.styleText(this, 17f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 16f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
            setTextIsSelectable(true)
        }
        status = TextView(this).apply {
            text = if (flashId == null) "Flashlight transmission is unavailable on this device."
                else "Ready to translate or transmit Morse code."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val toMorse = NativeButton(this).apply { text = "Encode to Morse" }
        val toText = NativeButton(this, false).apply { text = "Decode to text" }
        val flash = NativeButton(this, false).apply {
            text = "Transmit with flashlight"
            isEnabled = flashId != null
        }
        val sound = NativeButton(this, false).apply { text = "Transmit as sound" }
        toMorse.setOnClickListener {
            output.text = input.text.toString().uppercase()
                .map { character -> encode[character] ?: "?" }.joinToString(" ")
            status.text = "Text encoded. Use a transmission option to send it."
        }
        toText.setOnClickListener {
            output.text = input.text.toString().trim().split(Regex("\\s+"))
                .map { code -> if (code == "/") " " else (decodeMap[code] ?: '?') }.joinToString("")
            status.text = "Morse decoded."
        }
        val trainerQ = TextView(this).apply { text = "Trainer: guess the letter"; UiStyle.styleText(this, 16f, true) }
        val trainerBtn = NativeButton(this, false).apply { text = "New trainer letter" }
        var trainerAns = ""
        trainerBtn.setOnClickListener { trainerAns = ('A'..'Z').random().toString(); trainerQ.text = "Trainer Morse: " + (encode[trainerAns[0]] ?: ""); status.text = "Type the letter in the input and press Decode to check (answer hidden)." }
        flash.setOnClickListener { transmit(resolveMorse()) }
        sound.setOnClickListener { transmitSound(resolveMorse()) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Morse translator"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Encode text, decode dot-dash sequences, or transmit the result."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 16f))
            })
            addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(toMorse, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(toText, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(output, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(flash, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(trainerQ, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(trainerBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(sound, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(status)
        }
        setContentView(ToolScaffold.wrap(this, "Morse Code", content))
    }

    private fun resolveMorse(): String {
        val candidate = output.text.toString().takeIf {
            it.isNotBlank() && it != "Encoded or decoded text will appear here."
        } ?: input.text.toString()
        val normalized = candidate.trim()
        return if (normalized.any { it.isLetterOrDigit() } && normalized.any { it !in ".-/ " }) {
            normalized.uppercase().map { encode[it] ?: "?" }.joinToString(" ")
        } else normalized
    }

    private fun transmit(morse: String) {
        val manager = cameraManager
        val id = flashId
        if (manager == null || id == null) {
            status.text = "No flashlight emitter is available."
            return
        }
        if (transmitting) {
            status.text = "A transmission is already running."
            return
        }
        val signal = morse.filter { it == '.' || it == '-' || it == ' ' || it == '/' }
        if (signal.isBlank()) {
            status.text = "Enter text or encode it to Morse first."
            return
        }
        transmitting = true
        status.text = "Transmitting Morse with the flashlight…"
        val unit = 200L
        var delay = 0L
        fun schedule(at: Long, on: Boolean) {
            handler.postDelayed({
                try { manager.setTorchMode(id, on) }
                catch (_: Exception) { status.text = "Flashlight transmission failed." }
            }, at)
        }
        for (character in signal) {
            when (character) {
                '.' -> { schedule(delay, true); delay += unit; schedule(delay, false); delay += unit }
                '-' -> { schedule(delay, true); delay += unit * 3; schedule(delay, false); delay += unit }
                ' ' -> delay += unit * 2
                '/' -> delay += unit * 6
            }
        }
        handler.postDelayed({
            try { manager.setTorchMode(id, false) } catch (_: Exception) { }
            transmitting = false
            status.text = "Flashlight transmission complete."
        }, delay + unit)
    }

    private fun transmitSound(morse: String) {
        if (transmitting) {
            status.text = "A transmission is already running."
            return
        }
        val signal = morse.filter { it == '.' || it == '-' || it == ' ' || it == '/' }
        if (signal.isBlank()) {
            status.text = "Enter text or encode it to Morse first."
            return
        }
        transmitting = true
        status.text = "Transmitting Morse as sound…"
        toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
        val unit = 180L
        var delay = 0L
        fun beep(duration: Long) {
            val startAt = delay
            handler.postDelayed({
                try { toneGen?.startTone(android.media.ToneGenerator.TONE_CDMA_DIAL_TONE_LITE, duration.toInt()) }
                catch (_: Exception) { }
            }, startAt)
            delay += duration + unit
        }
        for (character in signal) {
            when (character) {
                '.' -> beep(unit)
                '-' -> beep(unit * 3)
                ' ' -> delay += unit * 2
                '/' -> delay += unit * 6
            }
        }
        handler.postDelayed({
            stopSound()
            transmitting = false
            status.text = "Sound transmission complete."
        }, delay + unit)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        transmitting = false
        flashId?.let { id ->
            try { cameraManager?.setTorchMode(id, false) } catch (_: Exception) { }
        }
        stopSound()
        super.onPause()
    }

    private fun stopSound() {
        try { toneGen?.release() } catch (_: Exception) { }
        toneGen = null
    }
}
