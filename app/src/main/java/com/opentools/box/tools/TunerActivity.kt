package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.abs
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
    private lateinit var needle: LinearProgressIndicator
    private var recorder: AudioRecord? = null
    private var listening = false
    private var worker: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noteView = TextView(this).apply { textSize = 64f; text = "--" }
        freqView = TextView(this).apply { textSize = 24f; text = "" }
        needle = LinearProgressIndicator(this).apply {
            max = 100
            setProgressCompat(50, false)
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Tuner",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(noteView)
            addView(freqView)
            addView(needle, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }))
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
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val size = maxOf(minBuf, SAMPLE_RATE / 5)
        recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        } catch (_: Exception) { null }
        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "Microphone unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        recorder?.startRecording()
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
        try { worker?.join(300) } catch (_: Exception) { }
        worker = null
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
    }

    private fun showPitch(freq: Double?) {
        if (freq == null) {
            noteView.text = "--"
            freqView.text = ""
            needle.setProgressCompat(50, true)
            return
        }
        val midi = (12 * log2(freq / 440.0) + 69).roundToInt()
        val nearest = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        val cents = (1200 * log2(freq / nearest)).coerceIn(-50.0, 50.0)
        noteView.text = noteNames[(midi % 12 + 12) % 12]
        freqView.text = "%.1f Hz  %+.0f¢".format(freq, cents)
        needle.setProgressCompat((50 + cents).roundToInt().coerceIn(0, 100), true)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else finish()
    }
}
