package com.opentools.box.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Flashlight toggle with hardware detection and visible failure feedback. */
class TorchActivity : AppCompatActivity() {
    private var cameraId: String? = null
    private var enabled = false
    private lateinit var toggle: Button
    private lateinit var status: TextView
    private lateinit var cameraManager: CameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }

        status = TextView(this).apply {
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 15f, false, UiStyle.muted(context))
        }
        toggle = Button(this).apply {
            text = "Turn on flashlight"
            UiStyle.styleButton(this)
        }
        toggle.isEnabled = cameraId != null
        status.text = if (cameraId == null) "No flashlight is available on this device." else "Flashlight is off"
        toggle.setOnClickListener { setTorch(!enabled) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Flashlight"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 26f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 8f))
            })
            addView(TextView(context).apply {
                text = "Use your device's camera flash as a torch."
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(context, 24f))
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Torch", content))
    }

    private fun setTorch(on: Boolean) {
        val id = cameraId
        if (id == null) {
            status.text = "No flashlight is available on this device."
            return
        }
        try {
            cameraManager.setTorchMode(id, on)
            enabled = on
            toggle.text = if (enabled) "Turn off flashlight" else "Turn on flashlight"
            toggle.setTextColor(if (enabled) android.graphics.Color.WHITE else android.graphics.Color.WHITE)
            status.text = if (enabled) "Flashlight is on" else "Flashlight is off"
        } catch (_: Exception) {
            enabled = false
            toggle.text = "Turn on flashlight"
            status.text = "Couldn't change the flashlight state. Another app may be using the camera."
        }
    }

    override fun onPause() {
        super.onPause()
        cameraId?.let {
            try { cameraManager.setTorchMode(it, false) } catch (_: Exception) { }
        }
        enabled = false
        if (::toggle.isInitialized) toggle.text = "Turn on flashlight"
        if (::status.isInitialized && cameraId != null) status.text = "Flashlight is off"
    }
}
