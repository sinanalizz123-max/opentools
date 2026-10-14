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
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Camera magnifier with native digital zoom controls. */
class MagnifierActivity : AppCompatActivity() {
    private lateinit var preview: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var zoomSeek: SeekBar
    private lateinit var zoomLabel: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var maxZoom = 4f
    private var zoom = 1f
    private var openingCamera = false
    private var zoomHelper: CameraZoom? = null
    private fun capturePhoto(v: android.view.TextureView) { try { val bmp = v.bitmap ?: return; val f = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "frame_" + System.currentTimeMillis() + ".jpg"); f.parentFile?.mkdirs(); f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }; zoomLabel.text = "Saved " + f.absolutePath } catch (_: Exception) {} }
    private fun zoomChips(ctx: android.content.Context): android.widget.LinearLayout = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL; listOf(1f, 2f, 4f).forEach { z -> addView(NativeButton(ctx).apply { text = z.toString() + "x"; setOnClickListener { zoomHelper?.setZoom(z) } }) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this).apply {
            contentDescription = "Magnifier camera preview"
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    currentSurface?.release()
                    currentSurface = null
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
        zoomLabel = TextView(this).apply {
            text = "Zoom · 1.0×"
            UiStyle.styleText(this, 14f, true)
            setPadding(0, 0, 0, UiStyle.dp(context, 6f))
        }
        zoomSeek = SeekBar(this).apply {
            max = 70
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            contentDescription = "Digital zoom"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    zoom = 1f + progress / 10f
                    updateZoomLabel()
                    applyZoom()
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
            addView(zoomLabel)
            addView(zoomSeek)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(-1, -2).apply {
                val margin = UiStyle.dp(context, 10f)
                setMargins(margin, margin, margin, margin)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Magnifier", content))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("magnifier").also { it.start() }
        handler = Handler(thread!!.looper)
        zoomHelper = CameraZoom(preview) { z ->
            zoom = z.coerceIn(1f, maxZoom)
            zoomSeek.progress = ((zoom - 1f) * 10f).toInt().coerceIn(0, zoomSeek.max)
            updateZoomLabel()
            applyZoom()
        }.also { try { it.maxZoom = maxZoom; it.attach() } catch (_: Exception) { } }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        } else if (preview.isAvailable) openCamera()
    }

    override fun onPause() {
        zoomHelper?.detach(); zoomHelper = null
        session?.close()
        session = null
        camera?.close()
        camera = null
        openingCamera = false
        currentSurface?.release()
        currentSurface = null
        currentRequest = null
        currentId = null
        thread?.quitSafely()
        thread = null
        handler = null
        super.onPause()
    }

    private fun openCamera() {
        if (camera != null || openingCamera || !preview.isAvailable) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val id = try { cameraManager.cameraIdList.firstOrNull() } catch (_: Exception) { null }
        if (id == null) {
            zoomLabel.text = "No camera available"
            return
        }
        maxZoom = try {
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        } catch (_: Exception) { 1f }.coerceAtLeast(1f)
        zoomSeek.max = ((maxZoom - 1f) * 10f).toInt().coerceAtLeast(0)
        zoomSeek.progress = zoomSeek.progress.coerceIn(0, zoomSeek.max)
        updateZoomLabel()
        openingCamera = true
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    openingCamera = false
                    camera = device
                    startPreview(device, id)
                }
                override fun onDisconnected(device: CameraDevice) {
                    openingCamera = false
                    device.close()
                    if (camera === device) camera = null
                }
                override fun onError(device: CameraDevice, error: Int) {
                    openingCamera = false
                    device.close()
                    if (camera === device) camera = null
                    runOnUiThread { zoomLabel.text = "Camera error ($error)" }
                }
            }, handler)
        } catch (_: Exception) {
            openingCamera = false
            zoomLabel.text = "Could not open camera"
        }
    }

    private fun startPreview(device: CameraDevice, id: String) {
        val texture = preview.surfaceTexture ?: return
        val optimal = CameraPreview.chooseSize(cameraManager, id, preview.width, preview.height)
        val bufferW = optimal?.width ?: preview.width.coerceAtLeast(1)
        val bufferH = optimal?.height ?: preview.height.coerceAtLeast(1)
        texture.setDefaultBufferSize(bufferW, bufferH)
        CameraPreview.fit(preview, bufferW, bufferH)
        val surface = Surface(texture)
        currentSurface?.release()
        currentSurface = surface
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        currentRequest = request
        currentId = id
        zoomHelper?.let {
            try { it.maxZoom = maxZoom; it.setZoom(zoom) } catch (_: Exception) { }
        }
        applyZoom()
        try {
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (camera !== device) {
                        configured.close()
                        return
                    }
                    session = configured
                    try { configured.setRepeatingRequest(request.build(), null, handler) }
                    catch (_: Exception) { runOnUiThread { zoomLabel.text = "Preview failed to start" } }
                }
                override fun onConfigureFailed(failed: CameraCaptureSession) {
                    failed.close()
                    runOnUiThread { zoomLabel.text = "Preview configuration failed" }
                }
            }, handler)
        } catch (_: Exception) {
            zoomLabel.text = "Could not start preview"
        }
    }

    private var currentRequest: CaptureRequest.Builder? = null
    private var currentSurface: Surface? = null
    private var currentId: String? = null

    private fun updateZoomLabel() {
        zoomLabel.text = "Zoom · %.1f×".format(zoom.coerceIn(1f, maxZoom))
    }

    private fun applyZoom() {
        val request = currentRequest ?: return
        val id = currentId ?: return
        val active = try {
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        } catch (_: Exception) { null } ?: return
        val actualZoom = zoom.coerceIn(1f, maxZoom)
        val width = (active.width() / actualZoom).toInt()
        val height = (active.height() / actualZoom).toInt()
        val cx = active.centerX()
        val cy = active.centerY()
        request.set(CaptureRequest.SCALER_CROP_REGION,
            android.graphics.Rect(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2))
        try { session?.setRepeatingRequest(request.build(), null, handler) } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_CAMERA) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else zoomLabel.text = "Camera permission denied."
    }

    companion object { private const val REQUEST_CAMERA = 1 }
}
