package com.opentools.box.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** UI for the foreground-service-backed screen recorder. */
class ScreenRecordActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var toggle: MaterialButton
    private lateinit var status: TextView
    private var recording = false

    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val start = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(start) else startService(start)
            recording = true
            status.text = "Recording started. Use Stop or the notification."
            toggle.text = "Stop recording"
        } else {
            status.text = "Screen capture permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        status = TextView(this).apply { textSize = 17f; text = "Ready" }
        toggle = MaterialButton(this).apply { text = "Start recording" }
        toggle.setOnClickListener {
            if (recording) stopRecording()
            else capture.launch(projectionManager.createScreenCaptureIntent())
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Screen Recorder", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(status); addView(toggle)
        }))
    }

    private fun stopRecording() {
        startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
        recording = false
        status.text = "Recording stopped. The file is in app Movies storage."
        toggle.text = "Start recording"
    }
}
