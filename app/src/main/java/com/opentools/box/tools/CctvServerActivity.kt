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
import java.util.concurrent.atomic.AtomicReference

/** CCTV camera server: streams MJPEG on :9996. Original implementation. */
class CctvServerActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var status: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private var server: CctvLan.MjpegServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        status = TextView(this).apply { textSize = 16f; text = "Starting…" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
        })
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("cctv").also { it.start() }
        handler = Handler(thread!!.looper)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }
        server = CctvLan.MjpegServer { latestJpeg.get() }.also { it.start() }
        status.text = "Streaming on :${CctvLan.PORT}"
        openCamera()
    }

    override fun onPause() {
        server?.stop(); server = null
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
                try {
                    val w = img.width
                    val h = img.height
                    val yBuf = img.planes[0].buffer
                    val nv21 = ByteArray(w * h * 3 / 2)
                    val yRow = img.planes[0].rowStride
                    for (row in 0 until h) {
                        yBuf.position(row * yRow)
                        yBuf.get(nv21, row * w, w)
                    }
                    var pos = w * h
                    val uBuf = img.planes[1].buffer
                    val vBuf = img.planes[2].buffer
                    val uvRow = img.planes[1].rowStride
                    val uvPix = img.planes[1].pixelStride
                    for (row in 0 until h / 2) {
                        for (c in 0 until w / 2) {
                            vBuf.position(row * uvRow + c * uvPix)
                            nv21[pos++] = vBuf.get()
                            uBuf.position(row * uvRow + c * uvPix)
                            nv21[pos++] = uBuf.get()
                        }
                    }
                    latestJpeg.set(CctvLan.nv21ToJpeg(nv21, w, h))
                } catch (_: Exception) { }
                finally { img.close() }
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

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) onResume()
        else finish()
    }
}
