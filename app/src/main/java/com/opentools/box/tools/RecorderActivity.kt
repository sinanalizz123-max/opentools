package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.os.SystemClock
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.UiStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Audio recorder: record / stop / play back, with level meter. Original implementation. */
class RecorderActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recording = false
    private var playing = false
    private var lastFile: File? = null

    private lateinit var timer: Chronometer
    private lateinit var recordBtn: NativeButton
    private lateinit var playBtn: NativeButton
    private lateinit var level: ProgressBar
    private lateinit var wave: SparkGraphView
    private lateinit var statusView: android.widget.TextView

    private val levelTick = object : Runnable {
        override fun run() {
            recorder?.let {
                try {
                    try { wave.push((it.maxAmplitude * 100f / 32767f).coerceIn(0f, 100f)) } catch (_: Exception) { }
                    level.progress = (it.maxAmplitude * 100 / 32767).coerceIn(0, 100)
                } catch (_: Exception) { }
                timer.postDelayed(this, 100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timer = Chronometer(this).apply {
            textSize = 40f
            setTextColor(UiStyle.text(context))
            setPadding(0, UiStyle.dp(context, 22f), 0, UiStyle.dp(context, 22f))
        }
        recordBtn = NativeButton(this).apply { text = "Start recording" }
        playBtn = NativeButton(this, false).apply { text = "Play recording"; isEnabled = false }
        level = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(UiStyle.border(context))
        }
        statusView = android.widget.TextView(this).apply {
            text = "Audio is saved privately on this device."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 12f), 0, UiStyle.dp(context, 12f))
        }
        recordBtn.setOnClickListener { if (recording) stopRecording() else startRecording() }
        playBtn.setOnClickListener { if (playing) stopPlaying() else startPlaying() }
        wave = SparkGraphView(this).apply { setRange(0f, 100f) }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Recorder", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Voice recorder"
                UiStyle.styleText(this, 24f, true)
            })
            addView(TextView(context).apply {
                text = "Record audio and play back your latest take."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(timer, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(level, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 6f)).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(wave, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 160f)))
            addView(recordBtn, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(playBtn, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(statusView)
        }))
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dest = File(filesDir, "rec-$stamp.m4a")
        lastFile = dest
        recorder = try {
            (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(dest.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            statusView.text = "Microphone unavailable"
            lastFile = null
            null
        } ?: return
        recording = true
        timer.base = SystemClock.elapsedRealtime()
        timer.start()
        timer.post(levelTick)
        recordBtn.text = "Stop recording"
        statusView.text = "Recording…"
        playBtn.isEnabled = false
    }

    private fun stopRecording() {
        timer.removeCallbacks(levelTick)
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        recording = false
        timer.stop()
        recordBtn.text = "Start recording"
        statusView.text = "Recording saved privately on this device."
        playBtn.isEnabled = lastFile != null
        level.progress = 0
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    private fun startPlaying() {
        val f = lastFile ?: return
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            prepare()
            start()
            setOnCompletionListener { stopPlaying() }
        }
        playing = true
        playBtn.text = "Stop playback"
    }

    private fun stopPlaying() {
        player?.release()
        player = null
        playing = false
        playBtn.text = "Play recording"
    }

    override fun onPause() {
        if (recording) stopRecording()
        if (playing) stopPlaying()
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecording()
        else Toast.makeText(this, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }
}