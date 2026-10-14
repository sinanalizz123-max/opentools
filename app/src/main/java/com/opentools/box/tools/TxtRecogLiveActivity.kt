package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Live on-device OCR with an actual camera preview and safe frame ownership. */
class TxtRecogLiveActivity : AppCompatActivity() {
    private lateinit var preview: TextureView
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var activeCameraId: String? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var busy = false
    private var openingCamera = false
    private var previewRequest: CaptureRequest.Builder? = null
    private var zoomHelper: CameraZoom? = null
    private var rotationDegrees = 0
    private var ocrLang = "Latin"
    private inner class OcrOverlay(ctx: android.content.Context) : android.view.View(ctx) { var boxes: List<android.graphics.RectF> = emptyList(); override fun onDraw(c: android.graphics.Canvas) { super.onDraw(c); val p = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f; color = 0xFF00E676.toInt() }; boxes.forEach { c.drawRect(it, p) } } }
    private var overlay: OcrOverlay? = null
    private fun copyAllLive() { try { val cb = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("ocr", output.text.toString())) } catch (_: Exception) {} }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this).apply {
            contentDescription = "Live camera view for text recognition"
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
        output = TextView(this).apply {
            text = "Point the camera at printed text."
            UiStyle.styleText(this, 15f, false)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            setTextIsSelectable(true)
        }
        status = TextView(this).apply {
            text = "Live OCR is processed on-device."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val clear = NativeButton(this, false).apply { text = "Clear text" }
        clear.setOnClickListener {
            output.text = "Point the camera at printed text."
            status.text = "Text cleared. Keep the text steady in view."
        }
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            background = UiStyle.rounded(context, UiStyle.background(context), 16f, UiStyle.border(context))
            addView(output, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(clear, LinearLayout.LayoutParams(-1, -2))
        }
        val frame = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                val margin = UiStyle.dp(context, 10f)
                setMargins(margin, 0, margin, margin)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Live OCR", frame))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("live-ocr").also { it.start() }
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
                cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (_: Exception) { null }
        if (id == null) {
            status.text = "No camera is available."
            return
        }
        val sensorOrientation = try {
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        } catch (_: Exception) { 0 }
        val displayRotation = when (preview.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        rotationDegrees = (sensorOrientation - displayRotation + 360) % 360
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
                if (busy) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                busy = true
                try {
                    val input = InputImage.fromMediaImage(image, rotationDegrees)
                    recognizer.process(input)
                        .addOnSuccessListener { result ->
                            if (result.text.isNotBlank()) runOnUiThread {
                                output.text = result.text
                                status.text = "Text recognized · select and copy as needed"
                            }
                        }
                        .addOnFailureListener { error ->
                            runOnUiThread { status.text = "OCR failed: ${error.message ?: "unknown error"}" }
                        }
                        .addOnCompleteListener {
                            image.close()
                            busy = false
                        }
                } catch (_: Exception) {
                    image.close()
                    busy = false
                }
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
                    try {
                        configured.setRepeatingRequest(request.build(), null, handler)
                        runOnUiThread { status.text = "Point the camera at printed text." }
                    } catch (_: Exception) {
                        runOnUiThread { status.text = "Could not start camera preview." }
                    }
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
                .get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 4f
        } catch (_: Exception) { 4f }.coerceAtLeast(1f)
    }

    private fun applyPinchZoom(z: Float) {
        val req = previewRequest ?: return
        val id = activeCameraId ?: return
        val active = try {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        } catch (_: Exception) { null } ?: return
        val actual = z.coerceIn(1f, maxZoomFor(id))
        val w = (active.width() / actual).toInt().coerceAtLeast(1)
        val h = (active.height() / actual).toInt().coerceAtLeast(1)
        val cx = active.centerX(); val cy = active.centerY()
        req.set(CaptureRequest.SCALER_CROP_REGION,
            android.graphics.Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
        try { session?.setRepeatingRequest(req.build(), null, handler) } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_CAMERA) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else status.text = "Camera permission denied."
    }

    override fun onDestroy() {
        recognizer.close()
        super.onDestroy()
    }

    companion object { private const val REQUEST_CAMERA = 1 }
}
