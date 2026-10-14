package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.log2
import kotlin.math.roundToInt

private const val SAMPLE_RATE = 44100

/**
 * Shared mic pitch reader: AudioRecord + autocorrelation.
 * Returns detected frequency in Hz, or null when too quiet / unclear.
 */
object PitchReader {
    fun detect(buffer: ShortArray, read: Int): Double? {
        if (read < SAMPLE_RATE / 10) return null
        var energy = 0.0
        for (i in 0 until read) energy += buffer[i] * buffer[i].toDouble()
        energy /= read
        if (energy < 1e7) return null // too quiet
        // Normalize DC offset.
        var mean = 0.0
        for (i in 0 until read) mean += buffer[i]
        mean /= read
        val norm = DoubleArray(read) { buffer[it] - mean }
        val minPeriod = SAMPLE_RATE / 1000 // max 1000 Hz
        val maxPeriod = SAMPLE_RATE / 50 // min 50 Hz
        var bestPeriod = -1
        var bestCorr = 0.0
        for (period in minPeriod..minOf(maxPeriod, read / 2)) {
            var corr = 0.0
            var normSum = 0.0
            for (i in 0 until read - period) {
                corr += norm[i] * norm[i + period]
                normSum += norm[i] * norm[i] + norm[i + period] * norm[i + period]
            }
            if (normSum > 0) corr = 2 * corr / normSum
            if (corr > bestCorr) { bestCorr = corr; bestPeriod = period }
        }
        if (bestCorr < 0.6 || bestPeriod <= 0) return null
        return SAMPLE_RATE.toDouble() / bestPeriod
    }
}

/** Instrument tuner: nearest note + cents needle. Original implementation. */
class TunerActivity : AppCompatActivity() {

    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private lateinit var noteView: TextView
    private lateinit var freqView: TextView
    private lateinit var needle: ProgressBar
    private var recorder: AudioRecord? = null
    private var listening = false
    private var worker: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noteView = TextView(this).apply {
            text = "--"
            gravity = android.view.Gravity.CENTER
            UiStyle.styleText(this, 64f, true)
        }
        freqView = TextView(this).apply {
            text = "Play a note near your microphone."
            gravity = android.view.Gravity.CENTER
            UiStyle.styleText(this, 17f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 8f), 0, UiStyle.dp(context, 20f))
        }
        needle = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 50
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(UiStyle.border(context))
            contentDescription = "Pitch accuracy, centered means in tune"
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Instrument tuner"
                gravity = android.view.Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(noteView, LinearLayout.LayoutParams(-1, -2))
            addView(freqView, LinearLayout.LayoutParams(-1, -2))
            addView(needle, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 8f)))
            addView(TextView(context).apply {
                text = "The center of the bar indicates the nearest note is in tune."
                gravity = android.view.Gravity.CENTER
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 12f), 0, 0)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Tuner", content))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        startListening()
    }

    override fun onPause() {
        stopListening()
        super.onPause()
    }

    private fun startListening() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (listening) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val size = maxOf(minBuf, SAMPLE_RATE / 5)
        recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        } catch (_: Exception) { null }
        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            recorder = null
            Toast.makeText(this, "Microphone unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        try { recorder?.startRecording() } catch (_: SecurityException) {
            recorder?.release()
            recorder = null
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            return
        }
        listening = true
        worker = Thread {
            val buf = ShortArray(size)
            while (listening) {
                val read = recorder?.read(buf, 0, buf.size) ?: 0
                if (read > 0) {
                    val freq = PitchReader.detect(buf, read)
                    runOnUiThread { showPitch(freq) }
                }
            }
        }.also { it.start() }
    }

    private fun stopListening() {
        listening = false
        try { recorder?.stop() } catch (_: Exception) { }
        try { worker?.join(500) } catch (_: Exception) { }
        worker = null
        recorder?.release()
        recorder = null
    }

    private fun showPitch(freq: Double?) {
        if (freq == null) {
            noteView.text = "--"
            freqView.text = ""
            needle.progress = 50
            return
        }
        val midi = (12 * log2(freq / 440.0) + 69).roundToInt()
        val nearest = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        val cents = (1200 * log2(freq / nearest)).coerceIn(-50.0, 50.0)
        noteView.text = noteNames[(midi % 12 + 12) % 12]
        freqView.text = "%.1f Hz  %+.0f¢".format(freq, cents)
        needle.progress = (50 + cents).roundToInt().coerceIn(0, 100)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else finish()
    }
}
