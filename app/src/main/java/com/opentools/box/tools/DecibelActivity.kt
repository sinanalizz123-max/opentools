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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Sound-level meter (dB) with peak hold. Original implementation. */
class DecibelActivity : AppCompatActivity() {
    private lateinit var graph: SparkGraphView
    private lateinit var minMax: TextView
    private var gMin: Float = Float.MAX_VALUE
    private var gMax: Float = Float.MIN_VALUE

    private lateinit var levelView: TextView
    private lateinit var peakView: TextView
    private lateinit var bar: ProgressBar
    private var recorder: AudioRecord? = null
    private var measuring = false
    private var worker: Thread? = null
    private var peak = 0.0
    private var peakSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        levelView = TextView(this).apply {
            text = "-- dB"
            gravity = android.view.Gravity.CENTER
            UiStyle.styleText(this, 52f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 24f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 24f))
        }
        peakView = TextView(this).apply {
            text = "Peak: --"
            gravity = android.view.Gravity.CENTER
            UiStyle.styleText(this, 18f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 120
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(UiStyle.border(context))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Sound level"
                gravity = android.view.Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(levelView, LinearLayout.LayoutParams(-1, -2))
            addView(bar, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 8f)).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(peakView, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(context).apply {
                text = "Approximate meter reading; not a calibrated sound pressure meter."
                gravity = android.view.Gravity.CENTER
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 12f), 0, 0)
            })
        }
        
        graph = SparkGraphView(this).apply { graph.setRange(0f, 120f) }
        minMax = TextView(this).apply { text = "Min: --  Max: --"; gravity = android.view.Gravity.CENTER; com.opentools.box.UiStyle.styleText(this, 13f, false, com.opentools.box.UiStyle.muted(context)) }
        try { (content as android.widget.LinearLayout).addView(graph, android.widget.LinearLayout.LayoutParams(-1, com.opentools.box.UiStyle.dp(this, 160f))); (content as android.widget.LinearLayout).addView(minMax) } catch (_: Exception) { }
                setContentView(ToolScaffold.wrap(this, "Decibel Meter", content))
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
                        try { val _v: Float = (s).toFloat(); graph.push(_v); if (_v < gMin) gMin = _v; if (_v > gMax) gMax = _v; minMax.text = "Min: %s  Max: %s".format(gMin, gMax) } catch (_: Exception) { }
                        bar.progress = s.roundToInt().coerceIn(0, 120)
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
        else levelView.text = "Microphone permission denied"
    }
}
