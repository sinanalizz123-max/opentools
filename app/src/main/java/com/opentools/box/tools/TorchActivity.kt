package com.opentools.box.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import android.widget.Button

/** Flashlight toggle with hardware detection and visible failure feedback. */
class TorchActivity : AppCompatActivity() {
    private var cameraId: String? = null
    private var enabled = false
    private var maxStrength = 1
    private var strengthLevel = 1
    private lateinit var toggle: Button
    private lateinit var status: TextView
    private lateinit var intensityLabel: TextView
    private lateinit var intensityBar: SeekBar
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
        toggle = NativeButton(this).apply {
            text = "Turn on flashlight"
        }
        maxStrength = try {
            if (cameraId != null && Build.VERSION.SDK_INT >= 33) {
                cameraManager.getCameraCharacteristics(cameraId!!)
                    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            } else 1
        } catch (_: Exception) { 1 }
        if (maxStrength < 1) maxStrength = 1
        strengthLevel = maxStrength

        intensityLabel = TextView(this).apply {
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
        }
        intensityBar = SeekBar(this).apply {
            max = maxStrength - 1
            progress = maxStrength - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) {
                    strengthLevel = p + 1
                    updateIntensityLabel()
                    if (enabled && fromUser) applyStrength()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        updateIntensityLabel()
        val strengthSupported = maxStrength > 1
        intensityLabel.visibility = if (strengthSupported) View.VISIBLE else View.GONE
        intensityBar.visibility = if (strengthSupported) View.VISIBLE else View.GONE

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
            addView(intensityLabel, LinearLayout.LayoutParams(-1, -2))
            addView(intensityBar, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 8f)
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
            if (on && maxStrength > 1 && Build.VERSION.SDK_INT >= 33) {
                try {
                    turnTorchWithStrength(id, strengthLevel)
                } catch (_: IllegalArgumentException) {
                    cameraManager.setTorchMode(id, true)
                }
            } else {
                cameraManager.setTorchMode(id, on)
            }
            enabled = on
            toggle.text = if (enabled) "Turn off flashlight" else "Turn on flashlight"
            toggle.setTextColor(if (enabled) android.graphics.Color.WHITE else android.graphics.Color.WHITE)
            status.text = if (enabled) "Flashlight is on" else "Flashlight is off"
        } catch (_: SecurityException) {
            Toast.makeText(this, "Camera permission required for flashlight.", Toast.LENGTH_SHORT).show()
            enabled = false
            toggle.text = "Turn on flashlight"
            status.text = "Camera permission required for flashlight."
        } catch (_: Exception) {
            enabled = false
            toggle.text = "Turn on flashlight"
            status.text = "Couldn't change the flashlight state. Another app may be using the camera."
        }
    }

    private fun updateIntensityLabel() {
        if (::intensityLabel.isInitialized) {
            intensityLabel.text = "Intensity: $strengthLevel / $maxStrength"
        }
    }

    @androidx.annotation.RequiresApi(33)
    private fun turnTorchWithStrength(id: String, level: Int) {
        // Direct API-33 call; callers guard with SDK_INT >= 33.
        cameraManager.turnOnTorchWithStrengthLevel(id, level)
    }

    private fun applyStrength() {
        val id = cameraId ?: return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                turnTorchWithStrength(id, strengthLevel)
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, "Camera permission required for flashlight.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
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
