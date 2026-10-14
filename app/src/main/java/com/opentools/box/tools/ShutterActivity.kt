package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Quiet still-photo capture (minimal UI). Original implementation. */
class ShutterActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var status: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        status = TextView(this).apply { textSize = 18f; text = "Ready" }
        val snap = MaterialButton(this).apply { text = "Capture photo" }
        snap.setOnClickListener { capture() }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Quick Camera",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(status); addView(snap)
        }))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("shutter").also { it.start() }
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
        reader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buf = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val dest = File(getExternalFilesDir(null), "photo-$stamp.jpg")
                    dest.writeBytes(bytes)
                    runOnUiThread {
                        status.text = "Saved ${dest.name}"
                        Toast.makeText(this@ShutterActivity, "Saved", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) { }
                finally { img.close() }
            }, handler)
        }
        val surface = reader!!.surface
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) { session = s }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    private fun capture() {
        val device = camera ?: return
        val surface = reader?.surface ?: return
        try {
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            session?.capture(req.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    runOnUiThread { status.text = "Captured…" }
                }
            }, handler)
        } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else finish()
    }
}
