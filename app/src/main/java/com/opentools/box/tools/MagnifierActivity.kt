package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.slider.Slider

/** Camera magnifier with digital zoom slider. Original implementation. */
class MagnifierActivity : AppCompatActivity() {

    private lateinit var preview: TextureView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var maxZoom = 4f
    private var zoom = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this)
        val slider = Slider(this).apply {
            valueFrom = 1f; valueTo = 8f; value = 1f; stepSize = 0.1f
            addOnChangeListener { _, v, _ -> zoom = v; applyZoom() }
        }
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) = Unit
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(preview, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(slider)
        })
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("mag").also { it.start() }
        handler = Handler(thread!!.looper)
        if (preview.isAvailable) openCamera()
        else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onPause() {
        session?.close(); session = null
        camera?.close(); camera = null
        thread?.quitSafely(); thread = null
        super.onPause()
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return
        val id = cameraManager.cameraIdList.firstOrNull() ?: return
        maxZoom = cameraManager.getCameraCharacteristics(id)
            .get(android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: 4f
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera = c; startPreview(c, id) }
                override fun onDisconnected(c: CameraDevice) { c.close() }
                override fun onError(c: CameraDevice, e: Int) { c.close() }
            }, handler)
        } catch (_: Exception) { }
    }

    private fun startPreview(device: CameraDevice, id: String) {
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(preview.width, preview.height)
        val surface = Surface(texture)
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        currentRequest = req
        currentSurface = surface
        currentId = id
        applyZoom()
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                s.setRepeatingRequest(req.build(), null, handler)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    private var currentRequest: CaptureRequest.Builder? = null
    private var currentSurface: Surface? = null
    private var currentId: String? = null

    private fun applyZoom() {
        val req = currentRequest ?: return
        val id = currentId ?: return
        val active = cameraManager.getCameraCharacteristics(id)
            .get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: return
        val z = zoom.coerceIn(1f, maxZoom)
        val dw = (active.width() / z).toInt()
        val dh = (active.height() / z).toInt()
        val cx = active.centerX()
        val cy = active.centerY()
        req.set(CaptureRequest.SCALER_CROP_REGION, android.graphics.Rect(
            cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2))
        try { session?.setRepeatingRequest(req.build(), null, handler) } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else finish()
    }
}
