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
import com.opentools.box.UiStyle

/** UI for the foreground-service-backed screen recorder. */
class ScreenRecordActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var toggle: NativeButton
    private lateinit var status: TextView
    private var recording = false
    private lateinit var pauseBtn: NativeButton
    private lateinit var micToggle: NativeButton
    private var paused = false
    private var micOn = true
    private fun sendSvc(action: String) { try { val i = android.content.Intent(this, ScreenRecordService::class.java).apply { this.action = action; putExtra(ScreenRecordService.EXTRA_MIC_ON, micOn) }; startService(i) } catch (_: Exception) {} }
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
            startForegroundService(start)
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
        status = TextView(this).apply {
            text = "Ready to record your screen."
            UiStyle.styleText(this, 15f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        toggle = NativeButton(this).apply { text = "Start recording" }
        toggle.setOnClickListener {
            if (recording) stopRecording()
            else ensureNotificationPermissionThen {
                capture.launch(projectionManager.createScreenCaptureIntent())
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Screen Recorder", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Capture your screen"
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 12f))
            })
            addView(TextView(context).apply {
                text = "Android will ask you to confirm screen capture before recording begins."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2))
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

    /** POST_NOTIFICATIONS (API 33+) lets the service refresh its status. */
    private fun ensureNotificationPermissionThen(block: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            block()
        } else {
            pendingCapture = block
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    private var pendingCapture: (() -> Unit)? = null

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {
            val granted = Build.VERSION.SDK_INT < 33 ||
                grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) pendingCapture?.invoke()
            else status.text = "Notification permission denied. Recording was not started."
            pendingCapture = null
        }
    }

    private fun stopRecording() {
        startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
        recording = false
        status.text = "Stopping recording…"
        toggle.text = "Start recording"
    }
}
