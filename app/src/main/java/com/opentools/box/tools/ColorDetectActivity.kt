package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.MotionEvent
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream
import android.graphics.Rect
import android.graphics.YuvImage

/** Point camera at a color, tap to sample it. Original implementation. */
class ColorDetectActivity : AppCompatActivity() {

    private lateinit var preview: TextureView
    private lateinit var swatch: TextView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var tapX = 0.5f
    private var tapY = 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this).apply {
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    tapX = e.x / width
                    tapY = e.y / height
                }
                true
            }
        }
        swatch = TextView(this).apply { textSize = 20f; text = "Tap to sample" }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Color Detector",FrameLayout(this).apply {
            addView(preview)
            addView(swatch)
        }))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("color").also { it.start() }
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
        reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try { sample(img) } finally { img.close() }
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

    private var lastSample = 0L

    private fun sample(img: android.media.Image) {
        if (System.currentTimeMillis() - lastSample < 300) return
        lastSample = System.currentTimeMillis()
        val w = img.width
        val h = img.height
        val yBuf = img.planes[0].buffer
        val uBuf = img.planes[1].buffer
        val vBuf = img.planes[2].buffer
        val yRow = img.planes[0].rowStride
        val uvRow = img.planes[1].rowStride
        val uvPix = img.planes[1].pixelStride
        val nv21 = ByteArray(w * h * 3 / 2)
        for (r in 0 until h) {
            yBuf.position(r * yRow)
            yBuf.get(nv21, r * w, w)
        }
        var pos = w * h
        for (r in 0 until h / 2) {
            for (c in 0 until w / 2) {
                vBuf.position(r * uvRow + c * uvPix)
                nv21[pos++] = vBuf.get()
                uBuf.position(r * uvRow + c * uvPix)
                nv21[pos++] = uBuf.get()
            }
        }
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, w, h, null)
            .compressToJpeg(Rect(0, 0, w, h), 80, out)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            ?: return
        val px = bmp.getPixel(
            (tapX * bmp.width).toInt().coerceIn(0, bmp.width - 1),
            (tapY * bmp.height).toInt().coerceIn(0, bmp.height - 1))
        bmp.recycle()
        val r = android.graphics.Color.red(px)
        val g = android.graphics.Color.green(px)
        val b = android.graphics.Color.blue(px)
        runOnUiThread {
            swatch.text = "#%02X%02X%02X  (%d, %d, %d)".format(0xFFFFFF and px, r, g, b)
            swatch.setBackgroundColor(px or 0xFF000000.toInt())
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else finish()
    }
}
