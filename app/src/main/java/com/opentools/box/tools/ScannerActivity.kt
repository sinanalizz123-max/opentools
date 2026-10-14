package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** QR and barcode scanner with a live camera preview and on-device ZXing decoding. */
class ScannerActivity : AppCompatActivity() {
    private lateinit var preview: TextureView
    private lateinit var result: TextView
    private lateinit var status: TextView
    private lateinit var rescan: NativeButton
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var activeCameraId: String? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var found = false
    private fun onDecoded(v: String) { pushHistory(v); if (!batchMode) found = true }
    @Volatile private var lastAttempt = 0L
    private var openingCamera = false
    private var previewRequest: CaptureRequest.Builder? = null
    private var zoomHelper: CameraZoom? = null
    private val decoder = MultiFormatReader()
    private val scanHistory = mutableListOf<String>()
    private var batchMode = false
    private var torchOn = false
    private fun histPrefs() = getSharedPreferences("scan_history", MODE_PRIVATE)
    private fun pushHistory(v: String) { scanHistory.add(0, v); while (scanHistory.size > 30) scanHistory.removeAt(scanHistory.size - 1); try { histPrefs().edit().putString("h", scanHistory.joinToString("\n")).apply() } catch (_: Exception) {} }
    private fun toggleTorch() { torchOn = !torchOn; try { previewRequest?.set(android.hardware.camera2.CaptureRequest.FLASH_MODE, if (torchOn) android.hardware.camera2.CaptureRequest.FLASH_MODE_TORCH else android.hardware.camera2.CaptureRequest.FLASH_MODE_OFF); val req = previewRequest?.build() ?: return; val h = handler ?: return; session?.setRepeatingRequest(req, null, h) } catch (_: Exception) {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this).apply {
            contentDescription = "Barcode scanner preview. Center a code in the frame."
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    previewSurface?.release()
                    previewSurface = null
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
        result = TextView(this).apply {
            text = "Point the camera at a QR code or barcode."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 15f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            setTextIsSelectable(true)
        }
        status = TextView(this).apply {
            text = "Ready to scan"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        rescan = NativeButton(this).apply { text = "Scan another code"; isEnabled = false }
        rescan.setOnClickListener {
            found = false
            decoder.reset()
            result.text = "Point the camera at a QR code or barcode."
            status.text = "Scanning…"
            rescan.isEnabled = false
        }
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            background = UiStyle.rounded(context, UiStyle.background(context), 16f, UiStyle.border(context))
            addView(result, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 6f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(rescan, LinearLayout.LayoutParams(-1, -2))
        }
        val frame = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                val margin = UiStyle.dp(context, 10f)
                setMargins(margin, 0, margin, margin)
            })
        }
        setContentView(ToolScaffold.wrap(this, "QR Scanner", frame))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("barcode-scan").also { it.start() }
        handler = Handler(thread!!.looper)
        zoomHelper = CameraZoom(preview) { applyPinchZoom(it) }.also {
            try { it.maxZoom = maxZoomFor(activeCameraId); it.attach() } catch (_: Exception) { }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        } else if (preview.isAvailable) openCamera()
    }

    override fun onPause() {
        zoomHelper?.detach(); zoomHelper = null
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        previewSurface?.release(); previewSurface = null
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
            status.text = "No camera is available."
            return
        }
        openingCamera = true
        try {
            activeCameraId = id
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    openingCamera = false
                    camera = device
                    startSession(device)
                }
                override fun onDisconnected(device: CameraDevice) {
                    openingCamera = false
                    device.close()
                    if (camera === device) camera = null
                    runOnUiThread { status.text = "Camera disconnected." }
                }
                override fun onError(device: CameraDevice, error: Int) {
                    openingCamera = false
                    device.close()
                    if (camera === device) camera = null
                    runOnUiThread { status.text = "Camera error ($error)." }
                }
            }, handler)
        } catch (_: Exception) {
            openingCamera = false
            status.text = "Could not open camera."
        }
    }

    private fun startSession(device: CameraDevice) {
        val texture = preview.surfaceTexture ?: return
        val optimal = activeCameraId?.let { CameraPreview.chooseSize(cameraManager, it, preview.width, preview.height) }
        val bufferW = optimal?.width ?: 1280
        val bufferH = optimal?.height ?: 720
        texture.setDefaultBufferSize(bufferW, bufferH)
        CameraPreview.fit(preview, bufferW, bufferH)
        previewSurface?.release()
        val surface = Surface(texture)
        previewSurface = surface
        reader?.close()
        reader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (!found && System.currentTimeMillis() - lastAttempt > 400) {
                        lastAttempt = System.currentTimeMillis()
                        decode(image)
                    }
                } finally { image.close() }
            }, handler)
        }
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            addTarget((reader ?: return).surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        previewRequest = request
        zoomHelper?.let { try { it.maxZoom = maxZoomFor(activeCameraId) } catch (_: Exception) { } }
        try {
            device.createCaptureSession(listOf(surface, (reader ?: return).surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (camera !== device) { configured.close(); return }
                    session = configured
                    try { configured.setRepeatingRequest(request.build(), null, handler) }
                    catch (_: Exception) { runOnUiThread { status.text = "Could not start camera preview." } }
                }
                override fun onConfigureFailed(failed: CameraCaptureSession) {
                    failed.close()
                    runOnUiThread { status.text = "Camera setup failed." }
                }
            }, handler)
        } catch (_: Exception) {
            status.text = "Could not start camera preview."
        }
    }

    private fun maxZoomFor(id: String?): Float {
        if (id == null) return 4f
        return try {
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?: 4f
        } catch (_: Exception) { 4f }.coerceAtLeast(1f)
    }

    private fun applyPinchZoom(z: Float) {
        val req = previewRequest ?: return
        val id = activeCameraId ?: return
        val active = try {
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        } catch (_: Exception) { null } ?: return
        val actual = z.coerceIn(1f, maxZoomFor(id))
        val w = (active.width() / actual).toInt().coerceAtLeast(1)
        val h = (active.height() / actual).toInt().coerceAtLeast(1)
        val cx = active.centerX(); val cy = active.centerY()
        req.set(CaptureRequest.SCALER_CROP_REGION,
            android.graphics.Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
        try { session?.setRepeatingRequest(req.build(), null, handler) } catch (_: Exception) { }
    }

    private fun decode(image: android.media.Image) {
        val y = image.planes[0].buffer
        val rowStride = image.planes[0].rowStride
        val width = image.width
        val height = image.height
        val data = ByteArray(width * height)
        val row = ByteArray(rowStride)
        for (r in 0 until height) {
            y.position(r * rowStride)
            y.get(row, 0, rowStride.coerceAtMost(y.remaining()))
            System.arraycopy(row, 0, data, r * width, width)
        }
        try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val text = decoder.decode(BinaryBitmap(HybridBinarizer(source))).text
            found = true
            runOnUiThread {
                result.text = text
                status.text = "Code found"
                rescan.isEnabled = true
            }
        } catch (_: Exception) {
            decoder.reset()
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_CAMERA) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else status.text = "Camera permission denied."
    }

    companion object { private const val REQUEST_CAMERA = 1 }
}
