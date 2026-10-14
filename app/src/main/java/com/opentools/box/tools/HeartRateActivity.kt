package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/** Heart-rate via camera PPG (finger over lens + flash). Original implementation. */
class HeartRateActivity : AppCompatActivity() {

    private lateinit var bpmView: TextView
    private lateinit var hintView: TextView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val redSamples = ArrayDeque<Double>()
    private var lastBeat = 0L
    private val intervals = ArrayDeque<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        bpmView = TextView(this).apply { textSize = 64f; gravity = Gravity.CENTER; text = "--" }
        hintView = TextView(this).apply {
            textSize = 16f; gravity = Gravity.CENTER
            text = "Cover the lens with a fingertip"
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(bpmView); addView(hintView)
        })
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("ppg").also { it.start() }
        handler = Handler(thread!!.looper)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }
        openCamera()
    }

    override fun onPause() {
        try { camera?.let { flashOff(it) } } catch (_: Exception) { }
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        thread?.quitSafely(); thread = null
        super.onPause()
    }

    private fun backCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                && chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) return id
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun openCamera() {
        val id = backCameraId() ?: return
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera = c; startSession(c, id) }
                override fun onDisconnected(c: CameraDevice) { c.close() }
                override fun onError(c: CameraDevice, e: Int) { c.close() }
            }, handler)
        } catch (_: SecurityException) { }
    }

    private fun startSession(device: CameraDevice, id: String) {
        reader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 3).apply {
            setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try { process(img) } finally { img.close() }
            }, handler)
        }
        val surface = reader!!.surface
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            try { set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH) } catch (_: Exception) { }
        }
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                s.setRepeatingRequest(req.build(), null, handler)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    private fun flashOff(device: CameraDevice) { }

    private fun process(img: android.media.Image) {
        val buf = img.planes[0].buffer
        val w = img.width
        val h = img.height
        val stride = img.planes[0].rowStride
        var sum = 0L
        var n = 0
        val row = ByteArray(stride)
        var r = h / 4
        while (r < h * 3 / 4) {
            buf.position(r * stride)
            buf.get(row)
            var c = w / 4
            while (c < w * 3 / 4) { sum += row[c].toInt() and 0xFF; n++; c += 4 }
            r += 4
        }
        val avg = sum.toDouble() / n
        redSamples.addLast(avg)
        while (redSamples.size > 90) redSamples.removeFirst()
        if (redSamples.size < 60) return
        // Simple peak detection on detrended signal.
        val mean = redSamples.average()
        val prev = redSamples.dropLast(1).takeLast(1).firstOrNull() ?: return
        val prevPrev = redSamples.dropLast(2).takeLast(1).firstOrNull() ?: return
        val now = System.currentTimeMillis()
        if (prevPrev < mean && prev >= mean && avg < mean && now - lastBeat > 350) {
            if (lastBeat > 0) {
                intervals.addLast(now - lastBeat)
                while (intervals.size > 6) intervals.removeFirst()
                val bpm = 60000.0 / intervals.average()
                if (bpm in 35.0..220.0) {
                    runOnUiThread { bpmView.text = "%.0f".format(bpm) }
                }
            }
            lastBeat = now
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else finish()
    }
}
