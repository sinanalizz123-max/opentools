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
    private var strobeHz = 5
    private var strobeOn = false
    private var sosOn = false
    private val strobeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var strobeLit = false
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
        val strobeLabel = TextView(this).apply { gravity = Gravity.CENTER; UiStyle.styleText(this, 14f, false, UiStyle.muted(context)); text = "Strobe: 5 Hz" }
        val strobeBar = SeekBar(this).apply { max = 9; progress = 4 }
        strobeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, u: Boolean) { strobeHz = p + 1; strobeLabel.text = "Strobe: $strobeHz Hz" }
            override fun onStartTrackingTouch(b: SeekBar?) = Unit
            override fun onStopTrackingTouch(b: SeekBar?) = Unit
        })
        val strobeToggle = NativeButton(this, false).apply { text = "Start strobe"; isEnabled = cameraId != null }
        strobeToggle.setOnClickListener { if (strobeOn) stopEffects() else { sosOn = false; strobeOn = true; strobeToggle.text = "Stop strobe"; toggle.text = "Turn off flashlight"; tickStrobe() } }
        val sos = NativeButton(this, false).apply { text = "SOS signal"; isEnabled = cameraId != null }
        sos.setOnClickListener { if (sosOn) stopEffects() else { strobeOn = false; sosOn = true; sos.text = "Stop SOS"; toggle.text = "Turn off flashlight"; tickSos(0) } }
        toggle.setOnClickListener { stopEffects(); setTorch(!enabled) }

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
            addView(strobeLabel, LinearLayout.LayoutParams(-1, -2))
            addView(strobeBar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiStyle.dp(context, 4f); bottomMargin = UiStyle.dp(context, 8f) })
            addView(strobeToggle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(sos, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Torch", android.widget.ScrollView(this).apply { isFillViewport = true; addView(content) }))
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
            toggle.setTextColor(if (enabled) android.graphics.Color.WHITE else UiStyle.muted(this))
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

    private val sosPattern = listOf(1,1,1,3,3,3,1,1,1)
    private fun setRaw(on: Boolean) { try { cameraManager.setTorchMode(cameraId!!, on) } catch (_: Exception) {} }
    private fun tickStrobe() {
        if (!strobeOn) return
        strobeLit = !strobeLit; setRaw(strobeLit); enabled = strobeLit
        strobeHandler.postDelayed({ tickStrobe() }, (1000L / (strobeHz * 2)).coerceAtLeast(50L))
    }
    private fun tickSos(i: Int) {
        if (!sosOn) return
        val idx = i % (sosPattern.size * 2)
        val on = idx % 2 == 0
        setRaw(on)
        val units = if (on) sosPattern[idx / 2] else 1
        strobeHandler.postDelayed({ tickSos(i + 1) }, units * 200L)
    }
    private fun stopEffects() {
        strobeOn = false; sosOn = false; strobeHandler.removeCallbacksAndMessages(null)
        try { cameraId?.let { cameraManager.setTorchMode(it, false) } } catch (_: Exception) {}
        enabled = false
        if (::toggle.isInitialized) toggle.text = "Turn on flashlight"
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
        strobeOn = false; sosOn = false
        try { strobeHandler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
        cameraId?.let {
            try { cameraManager.setTorchMode(it, false) } catch (_: Exception) { }
        }
        enabled = false
        if (::toggle.isInitialized) toggle.text = "Turn on flashlight"
        if (::status.isInitialized && cameraId != null) status.text = "Flashlight is off"
    }
}
