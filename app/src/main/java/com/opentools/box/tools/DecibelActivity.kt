package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Sound-level meter (dB) with peak hold. Original implementation. */
class DecibelActivity : AppCompatActivity() {

    private lateinit var levelView: TextView
    private lateinit var peakView: TextView
    private lateinit var bar: LinearProgressIndicator
    private var recorder: AudioRecord? = null
    private var measuring = false
    private var worker: Thread? = null
    private var peak = 0.0
    private var peakSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        levelView = TextView(this).apply { textSize = 56f; text = "-- dB" }
        peakView = TextView(this).apply { textSize = 20f; text = "Peak: --" }
        bar = LinearProgressIndicator(this).apply { max = 120 }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Decibel Meter",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(levelView)
            addView(bar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(peakView)
        }))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        startMeasuring()
    }

    override fun onPause() {
        stopMeasuring()
        super.onPause()
    }

    private fun startMeasuring() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (measuring) return
        val size = maxOf(
            AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT),
            4096)
        recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, 44100,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        } catch (_: Exception) { null }
        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            recorder = null
            levelView.text = "Microphone unavailable"
            return
        }
        try {
            recorder?.startRecording()
        } catch (_: SecurityException) {
            recorder?.release()
            recorder = null
            levelView.text = "Microphone permission required"
            return
        }
        measuring = true
        peak = 0.0
        peakSince = System.currentTimeMillis()
        worker = Thread {
            val buf = ShortArray(size)
            var smooth = 0.0
            while (measuring) {
                val read = recorder?.read(buf, 0, buf.size) ?: 0
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) sum += buf[i] * buf[i].toDouble()
                    val rms = sqrt(sum / read)
                    // dBFS mapped to an approximate SPL scale for display.
                    val db = if (rms < 1) 0.0 else (20 * log10(rms / 32768.0) + 100)
                    smooth = smooth * 0.7 + db * 0.3
                    if (smooth > peak || System.currentTimeMillis() - peakSince > 5000) {
                        peak = smooth
                        peakSince = System.currentTimeMillis()
                    }
                    val s = smooth
                    val p = peak
                    runOnUiThread {
                        levelView.text = "%d dB".format(s.roundToInt())
                        peakView.text = "Peak: %d dB".format(p.roundToInt())
                        bar.setProgressCompat(s.roundToInt().coerceIn(0, 120), true)
                    }
                }
            }
        }.also { it.start() }
    }

    private fun stopMeasuring() {
        measuring = false
        try { recorder?.stop() } catch (_: Exception) { }
        try { worker?.join(500) } catch (_: Exception) { }
        worker = null
        recorder?.release()
        recorder = null
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startMeasuring()
        else finish()
    }
}
