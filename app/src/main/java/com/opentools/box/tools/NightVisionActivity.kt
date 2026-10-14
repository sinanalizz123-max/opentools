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
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/** Night vision: max-exposure camera preview + brightness boost. Original implementation. */
class NightVisionActivity : AppCompatActivity() {

    private lateinit var preview: TextureView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var exposure = 4L // x multiplier index
    private var request: CaptureRequest.Builder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this)
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) = Unit
        }
        val gain = SeekBar(this).apply {
            max = 8; progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    exposure = (p + 1).toLong()
                    applyExposure()
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Night Vision",FrameLayout(this).apply {
            addView(preview)
            addView(gain)
        }))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("night").also { it.start() }
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
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera = c; startPreview(c) }
                override fun onDisconnected(c: CameraDevice) { c.close() }
                override fun onError(c: CameraDevice, e: Int) { c.close() }
            }, handler)
        } catch (_: Exception) { }
    }

    private fun startPreview(device: CameraDevice) {
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(preview.width, preview.height)
        val surface = Surface(texture)
        request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        applyExposure()
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                s.setRepeatingRequest(request!!.build(), null, handler)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    private fun applyExposure() {
        val req = request ?: return
        try {
            req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            // Boost sensor exposure time + sensitivity within safe bounds.
            req.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 33_000_000L * exposure.coerceIn(1, 9))
            req.set(CaptureRequest.SENSOR_SENSITIVITY, (800 * exposure).toInt().coerceIn(100, 6400))
            session?.setRepeatingRequest(req.build(), null, handler)
        } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else finish()
    }
}
