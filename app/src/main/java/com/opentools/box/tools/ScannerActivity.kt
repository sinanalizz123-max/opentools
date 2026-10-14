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
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** QR/barcode scanner (Camera2 + ZXing). Original implementation. */
class ScannerActivity : AppCompatActivity() {

    private lateinit var preview: TextureView
    private lateinit var result: TextView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var found = false
    private var lastAttempt = 0L
    private val decoder = MultiFormatReader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this)
        result = TextView(this).apply { textSize = 18f; text = "Point at a code" }
        val frame = FrameLayout(this)
        frame.addView(preview)
        frame.addView(result)
        setContentView(frame)
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("scan").also { it.start() }
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
        reader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (!found && System.currentTimeMillis() - lastAttempt > 400) {
                        lastAttempt = System.currentTimeMillis()
                        decode(img)
                    }
                } finally { img.close() }
            }, handler)
        }
        val surface = reader!!.surface
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                s.setRepeatingRequest(req.build(), null, handler)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    private fun decode(img: android.media.Image) {
        val y = img.planes[0].buffer
        val rowStride = img.planes[0].rowStride
        val w = img.width
        val h = img.height
        val data: ByteArray = if (rowStride == w) {
            ByteArray(w * h).also { y.get(it) }
        } else {
            val out = ByteArray(w * h)
            val row = ByteArray(rowStride)
            for (r in 0 until h) {
                y.get(row)
                System.arraycopy(row, 0, out, r * w, w)
            }
            out
        }
        try {
            val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
            val text = decoder.decode(BinaryBitmap(HybridBinarizer(source))).text
            found = true
            runOnUiThread {
                result.text = text
                Toast.makeText(this, "Found", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else finish()
    }
}
