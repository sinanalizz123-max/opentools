package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
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
    private lateinit var recordBtn: MaterialButton
    private lateinit var playBtn: MaterialButton
    private lateinit var level: ProgressBar

    private val levelTick = object : Runnable {
        override fun run() {
            recorder?.let {
                try {
                    level.progress = (it.maxAmplitude * 100 / 32767).coerceIn(0, 100)
                } catch (_: Exception) { }
                timer.postDelayed(this, 100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timer = Chronometer(this).apply { textSize = 40f }
        recordBtn = MaterialButton(this).apply { text = "Record" }
        playBtn = MaterialButton(this).apply { text = "Play"; isEnabled = false }
        level = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        recordBtn.setOnClickListener { if (recording) stopRecording() else startRecording() }
        playBtn.setOnClickListener { if (playing) stopPlaying() else startPlaying() }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(timer)
            addView(level)
            addView(recordBtn)
            addView(playBtn)
        })
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        lastFile = File(filesDir, "rec-$stamp.m4a")
        recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(lastFile!!.absolutePath)
            prepare()
            start()
        }
        recording = true
        timer.base = SystemClock.elapsedRealtime()
        timer.start()
        timer.post(levelTick)
        recordBtn.text = "Stop"
        playBtn.isEnabled = false
    }

    private fun stopRecording() {
        timer.removeCallbacks(levelTick)
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        recording = false
        timer.stop()
        recordBtn.text = "Record"
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
        playBtn.text = "Stop"
    }

    private fun stopPlaying() {
        player?.release()
        player = null
        playing = false
        playBtn.text = "Play"
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
