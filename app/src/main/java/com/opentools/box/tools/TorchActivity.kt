package com.opentools.box.tools

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/** Flashlight toggle. Original clean-room implementation. */
class TorchActivity : AppCompatActivity() {

    private var cameraId: String? = null
    private var enabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull()

        val button = Button(this).apply { text = "Turn on" }
        button.setOnClickListener {
            val id = cameraId ?: return@setOnClickListener
            enabled = !enabled
            cameraManager.setTorchMode(id, enabled)
            button.text = if (enabled) "Turn off" else "Turn on"
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Torch",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button)
        }))
    }

    override fun onPause() {
        super.onPause()
        cameraId?.let {
            try {
                (getSystemService(Context.CAMERA_SERVICE) as CameraManager)
                    .setTorchMode(it, false)
            } catch (_: Exception) { }
        }
        enabled = false
    }
}
