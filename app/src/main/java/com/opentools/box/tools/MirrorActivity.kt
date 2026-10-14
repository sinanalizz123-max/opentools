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
import android.widget.TextView
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.UiStyle

/** Front-camera mirror. Original implementation (Camera2 preview). */
class MirrorActivity : AppCompatActivity() {

    private lateinit var preview: TextureView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var cameraId: String? = null
    private var previewSurface: Surface? = null
    private var openingCamera = false
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Prefer a front-facing camera; fall back to the first available.
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
        } ?: cameraManager.cameraIdList.firstOrNull()

        preview = TextureView(this).apply { contentDescription = "Front camera mirror preview" }
        status = TextView(this).apply {
            text = "Use the front camera as a mirror."
            UiStyle.styleText(this, 13f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 10f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val frame = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                val margin = UiStyle.dp(context, 12f)
                setMargins(margin, margin, margin, 0)
            })
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Mirror", frame))

        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                previewSurface?.release()
                previewSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) = Unit
        }
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("mirror").also { it.start() }
        handler = Handler(thread!!.looper)
        if (preview.isAvailable) openCamera()
    }

    override fun onPause() {
        session?.close(); session = null
        camera?.close(); camera = null
        previewSurface?.release(); previewSurface = null
        openingCamera = false
        thread?.quitSafely(); thread = null
        super.onPause()
    }

    private fun openCamera() {
        val id = cameraId ?: run { status.text = "No camera is available."; return }
        if (camera != null || openingCamera) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            status.text = "Camera permission is needed for the mirror."
            return
        }
        openingCamera = true
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) {
                    openingCamera = false
                    camera = c
                    status.text = "Mirror ready"
                    startPreview(c)
                }
                override fun onDisconnected(c: CameraDevice) {
                    openingCamera = false
                    c.close()
                    if (camera === c) camera = null
                    runOnUiThread { status.text = "Camera disconnected." }
                }
                override fun onError(c: CameraDevice, error: Int) {
                    openingCamera = false
                    c.close()
                    if (camera === c) camera = null
                    runOnUiThread { status.text = "Camera unavailable." }
                }
            }, handler)
        } catch (_: Exception) {
            openingCamera = false
            status.text = "Camera unavailable."
        }
    }

    private fun startPreview(device: CameraDevice) {
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(preview.width.coerceAtLeast(1), preview.height.coerceAtLeast(1))
        previewSurface?.release()
        val surface = Surface(texture)
        previewSurface = surface
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                if (camera !== device) { s.close(); return }
                session = s
                try { s.setRepeatingRequest(request.build(), null, handler) }
                catch (_: Exception) { runOnUiThread { status.text = "Preview failed to start." } }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) {
                s.close()
                runOnUiThread { status.text = "Preview setup failed." }
            }
        }, handler)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else status.text = "Camera permission denied."
    }
}
