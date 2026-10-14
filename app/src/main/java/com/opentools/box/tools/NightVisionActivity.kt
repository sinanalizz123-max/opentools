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
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Low-light camera preview with manual exposure gain controls where hardware supports it. */
class NightVisionActivity : AppCompatActivity() {
    private lateinit var preview: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var gainLabel: TextView
    private var camera: CameraDevice? = null
    private var activeCameraId: String? = null
    private var session: CameraCaptureSession? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var exposure = 4L
    private var request: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null
    private var openingCamera = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this).apply {
            contentDescription = "Night vision camera preview"
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
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
        gainLabel = TextView(this).apply {
            text = "Exposure boost · 4×"
            UiStyle.styleText(this, 13f, true)
            setPadding(0, 0, 0, UiStyle.dp(context, 4f))
        }
        val gain = SeekBar(this).apply {
            max = 8
            progress = 3
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            contentDescription = "Low-light exposure boost"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    exposure = (progress + 1).toLong()
                    gainLabel.text = "Exposure boost · ${exposure}×"
                    applyExposure()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 14f)
            setPadding(pad, pad, pad, pad)
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
            addView(gainLabel)
            addView(gain)
        }
        val frame = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                val margin = UiStyle.dp(context, 10f)
                setMargins(margin, 0, margin, margin)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Night Vision", frame))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("night-vision").also { it.start() }
        handler = Handler(thread!!.looper)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        } else if (preview.isAvailable) openCamera()
    }

    override fun onPause() {
        session?.close(); session = null
        camera?.close(); camera = null
        previewSurface?.release(); previewSurface = null
        request = null
        openingCamera = false
        thread?.quitSafely(); thread = null
        handler = null
        super.onPause()
    }

    private fun openCamera() {
        if (camera != null || openingCamera || !preview.isAvailable) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val id = try {
            cameraManager.cameraIdList.firstOrNull { cameraId ->
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (_: Exception) { null }
        if (id == null) {
            gainLabel.text = "No camera available"
            return
        }
        activeCameraId = id
        openingCamera = true
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) {
                    openingCamera = false
                    camera = c
                    startPreview(c)
                }
                override fun onDisconnected(c: CameraDevice) {
                    openingCamera = false
                    c.close()
                    if (camera === c) camera = null
                }
                override fun onError(c: CameraDevice, e: Int) {
                    openingCamera = false
                    c.close()
                    if (camera === c) camera = null
                    runOnUiThread { gainLabel.text = "Camera error ($e)" }
                }
            }, handler)
        } catch (_: Exception) {
            openingCamera = false
            gainLabel.text = "Could not open camera"
        }
    }

    private fun startPreview(device: CameraDevice) {
        val texture = preview.surfaceTexture ?: return
        val optimal = activeCameraId?.let { CameraPreview.chooseSize(cameraManager, it, preview.width, preview.height) }
        val bufferW = optimal?.width ?: preview.width.coerceAtLeast(1)
        val bufferH = optimal?.height ?: preview.height.coerceAtLeast(1)
        texture.setDefaultBufferSize(bufferW, bufferH)
        CameraPreview.fit(preview, bufferW, bufferH)
        previewSurface?.release()
        val surface = Surface(texture)
        previewSurface = surface
        request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        try {
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (camera !== device) { s.close(); return }
                    session = s
                    applyExposure()
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    s.close()
                    runOnUiThread { gainLabel.text = "Preview configuration failed" }
                }
            }, handler)
        } catch (_: Exception) {
            gainLabel.text = "Could not start preview"
        }
    }

    private fun applyExposure() {
        val req = request ?: return
        try {
            req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            req.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 33_000_000L * exposure.coerceIn(1, 9))
            req.set(CaptureRequest.SENSOR_SENSITIVITY, (800 * exposure).toInt().coerceIn(100, 6400))
            session?.setRepeatingRequest(req.build(), null, handler)
        } catch (_: Exception) {
            gainLabel.text = "Manual exposure is not supported by this camera."
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != REQUEST_CAMERA) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else gainLabel.text = "Camera permission denied."
    }

    companion object { private const val REQUEST_CAMERA = 1 }
}
