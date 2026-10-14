package com.opentools.box.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Screen recorder (MediaProjection + MediaRecorder). Original implementation. */
class ScreenRecordActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var recording = false
    private lateinit var toggle: MaterialButton
    private lateinit var status: TextView

    private val capture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            projection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
            begin()
        } else {
            status.text = "Permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        status = TextView(this).apply { textSize = 18f; text = "Ready" }
        toggle = MaterialButton(this).apply { text = "Start recording" }
        toggle.setOnClickListener {
            if (recording) stop()
            else capture.launch(projectionManager.createScreenCaptureIntent())
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Screen Recorder",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(status); addView(toggle)
        }))
    }

    private fun begin() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(getExternalFilesDir(null), "screen-$stamp.mp4")
        val metrics = resources.displayMetrics
        recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(metrics.widthPixels, metrics.heightPixels)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(8 * 1024 * 1024)
            setOutputFile(file.absolutePath)
            prepare()
        }
        val surface = recorder!!.surface
        projection?.createVirtualDisplay("screen", metrics.widthPixels, metrics.heightPixels,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null)
        recorder!!.start()
        recording = true
        status.text = "Recording… ${file.name}"
        toggle.text = "Stop"
    }

    private fun stop() {
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release(); recorder = null
        projection?.stop(); projection = null
        recording = false
        status.text = "Saved"
        toggle.text = "Start recording"
    }

    override fun onDestroy() {
        if (recording) stop()
        super.onDestroy()
    }
}
