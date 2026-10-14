package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.abs

/** Motion detector: alerts when preview frames change. Original implementation. */
class MotionDetectActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var status: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var armed = false
    private var threshold = 25.0
    private var previous: ByteArray? = null
    private lateinit var armButton: NativeButton
    private lateinit var sensitivityLabel: TextView
    private val alertClip = mutableListOf<ByteArray>()
    private fun saveAlertClip() { try { if (alertClip.isEmpty()) { status.text = sensitivityLabel.text.toString() + " · no clip yet"; return }; val f = java.io.File(getExternalFilesDir(null), "motion_" + System.currentTimeMillis() + ".bin"); f.outputStream().use { o -> alertClip.forEach { o.write(it) } }; status.text = "Alert clip saved: " + f.absolutePath; alertClip.clear() } catch (e: Exception) { status.text = "Clip save failed" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        status = TextView(this).apply {
            text = "Disarmed · arm detection to watch for frame changes."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val sensitivity = SeekBar(this).apply {
            max = 75
            progress = 20
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            contentDescription = "Motion sensitivity threshold"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    threshold = (progress + 5).toDouble()
                    if (::sensitivityLabel.isInitialized) sensitivityLabel.text = "Sensitivity · threshold ${threshold.toInt()}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        armButton = NativeButton(this).apply { text = "Arm motion detection" }
        armButton.setOnClickListener {
            armed = !armed
            previous = null
            armButton.text = if (armed) "Disarm motion detection" else "Arm motion detection"
            status.text = if (armed) "Watching for motion…" else "Detection disarmed."
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Motion detector"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "The camera compares frames locally. Sensitivity controls how much a frame must change."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 14f))
            })
            addView(TextView(context).apply {
                text = "Sensitivity · threshold ${threshold.toInt()}"
                UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
                sensitivityLabel = this
            })
            addView(sensitivity, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(armButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Motion Detector", content))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("motion").also { it.start() }
        handler = Handler(thread!!.looper)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }
        openCamera()
    }

    override fun onPause() {
        armed = false
        previous = null
        if (::armButton.isInitialized) armButton.text = "Arm motion detection"
        if (::status.isInitialized) status.text = "Detection paused."
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        thread?.quitSafely(); thread = null
        super.onPause()
    }

    private fun openCamera() {
        val id = cameraManager.cameraIdList.firstOrNull() ?: return
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera = c; startSession(c) }
                override fun onDisconnected(c: CameraDevice) { c.close() }
                override fun onError(c: CameraDevice, e: Int) { c.close() }
            }, handler)
        } catch (_: SecurityException) { }
    }

    private fun startSession(device: CameraDevice) {
        reader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (armed) compare(img)
                } finally { img.close() }
            }, handler)
        }
        val activeReader = reader ?: return
        val surface = activeReader.surface
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
        }
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                s.setRepeatingRequest(req.build(), null, handler)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    private fun compare(img: android.media.Image) {
        val w = img.width
        val h = img.height
        val stride = img.planes[0].rowStride
        val buf = img.planes[0].buffer
        // Downsampled grayscale signature.
        val sig = ByteArray(32 * 24)
        val row = ByteArray(stride)
        for (y in 0 until 24) {
            buf.position((y * h / 24) * stride)
            buf.get(row)
            for (x in 0 until 32) sig[y * 32 + x] = row[x * w / 32]
        }
        val prev = previous
        previous = sig
        if (prev == null) return
        var diff = 0.0
        for (i in sig.indices) diff += abs((sig[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
        diff /= sig.size
        if (diff > threshold) {
            runOnUiThread { status.text = "MOTION! (%.0f)".format(diff) }
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else {
            status.text = "Camera permission denied."
            finish()
        }
    }
}
