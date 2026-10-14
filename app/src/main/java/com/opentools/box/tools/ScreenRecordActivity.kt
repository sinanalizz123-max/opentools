package com.opentools.box.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.content.SharedPreferences
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
    private val recorderPrefs by lazy { getSharedPreferences("screen_recorder", MODE_PRIVATE) }
    private val stateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "state" || key == "error") runOnUiThread { syncRecordingState() }
    }

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
            status.text = "Starting recording…"
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
        syncRecordingState()
    }

    override fun onResume() {
        super.onResume()
        recorderPrefs.registerOnSharedPreferenceChangeListener(stateListener)
        syncRecordingState()
    }

    override fun onPause() {
        recorderPrefs.unregisterOnSharedPreferenceChangeListener(stateListener)
        super.onPause()
    }

    private fun syncRecordingState() {
        val state = recorderPrefs.getString("state", "stopped")
        recording = state == "recording" || state == "starting"
        when (state) {
            "starting" -> status.text = "Starting recording…"
            "recording" -> status.text = "Recording in progress. Use Stop or the notification."
            "failed" -> status.text = recorderPrefs.getString("error", "Screen recording failed.")
            else -> status.text = "Ready"
        }
        toggle.text = if (recording) "Stop recording" else "Start recording"
    }

    private fun stopRecording() {
        startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
        recording = false
        status.text = "Stopping recording…"
        toggle.text = "Start recording"
    }
}
