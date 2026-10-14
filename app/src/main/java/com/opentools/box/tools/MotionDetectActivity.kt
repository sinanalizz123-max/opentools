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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        status = TextView(this).apply { textSize = 22f; text = "Disarmed" }
        val sensitivity = Slider(this).apply {
            valueFrom = 5f; valueTo = 80f; value = 25f
            addOnChangeListener { _, v, _ -> threshold = v.toDouble() }
        }
        val arm = MaterialButton(this).apply { text = "Arm" }
        arm.setOnClickListener {
            armed = !armed
            previous = null
            arm.text = if (armed) "Disarm" else "Arm"
            status.text = if (armed) "Watching…" else "Disarmed"
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Motion Detector",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(status); addView(sensitivity); addView(arm)
        }))
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
        val surface = reader!!.surface
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
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else finish()
    }
}
