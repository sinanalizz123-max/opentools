package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.io.ByteArrayOutputStream

/** Live camera color sampler. Tap the preview to sample the selected point. */
class ColorDetectActivity : AppCompatActivity() {
    private lateinit var preview: TextureView
    private lateinit var swatch: TextView
    private lateinit var instruction: TextView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var tapX = 0.5f
    @Volatile private var tapY = 0.5f
    @Volatile private var lastSample = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        preview = TextureView(this).apply {
            contentDescription = "Camera preview. Tap to sample a color."
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    camera?.let { startSession(it) }
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    previewSurface?.release()
                    previewSurface = null
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (width > 0 && height > 0) {
                            tapX = (event.x / width).coerceIn(0f, 1f)
                            tapY = (event.y / height).coerceIn(0f, 1f)
                            instruction.text = "Sampling selected point…"
                        }
                    }
                    MotionEvent.ACTION_UP -> view.performClick()
                }
                true
            }
        }
        instruction = TextView(this).apply {
            text = "Point the camera at a color and tap the preview."
            UiStyle.styleText(this, 13f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 10f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        swatch = TextView(this).apply {
            text = "Tap to sample"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 20f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 18f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        val frame = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(instruction, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                val margin = UiStyle.dp(context, 12f)
                setMargins(margin, margin, margin, 0)
            })
            addView(swatch, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                val margin = UiStyle.dp(context, 12f)
                setMargins(margin, 0, margin, margin)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Color Detector", frame))
    }

    override fun onResume() {
        super.onResume()
        thread = HandlerThread("color-sampler").also { it.start() }
        handler = Handler(thread!!.looper)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }
        openCamera()
    }

    override fun onPause() {
        session?.close()
        session = null
        camera?.close()
        camera = null
        reader?.close()
        reader = null
        previewSurface?.release()
        previewSurface = null
        thread?.quitSafely()
        thread = null
        handler = null
        super.onPause()
    }

    private fun openCamera() {
        val id = try { cameraManager.cameraIdList.firstOrNull() } catch (_: Exception) { null }
        if (id == null) {
            instruction.text = "No camera is available on this device."
            return
        }
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    if (preview.isAvailable) startSession(device)
                    else instruction.text = "Preparing camera preview…"
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (camera === device) camera = null
                    runOnUiThread { instruction.text = "Camera disconnected." }
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (camera === device) camera = null
                    runOnUiThread { instruction.text = "Camera error ($error)." }
                }
            }, handler)
        } catch (_: SecurityException) {
            instruction.text = "Camera permission is required."
        } catch (_: Exception) {
            instruction.text = "Could not open the camera."
        }
    }

    private fun startSession(device: CameraDevice) {
        val texture = preview.surfaceTexture ?: return
        session?.close()
        session = null
        previewSurface?.release()
        texture.setDefaultBufferSize(640, 480)
        val surface = Surface(texture)
        previewSurface = surface
        reader?.close()
        reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try { sample(image) } catch (_: Exception) { }
                finally { image.close() }
            }, handler)
        }
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                addTarget((reader ?: return).surface)
            }
            device.createCaptureSession(listOf(surface, (reader ?: return).surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        if (camera !== device) {
                            configured.close()
                            return
                        }
                        session = configured
                        try {
                            configured.setRepeatingRequest(request.build(), null, handler)
                            runOnUiThread { instruction.text = "Tap the preview to sample a color." }
                        } catch (_: Exception) {
                            runOnUiThread { instruction.text = "Could not start the camera preview." }
                        }
                    }
                    override fun onConfigureFailed(failed: CameraCaptureSession) {
                        failed.close()
                        runOnUiThread { instruction.text = "Camera preview setup failed." }
                    }
                }, handler)
        } catch (_: Exception) {
            instruction.text = "Could not start the camera preview."
        }
    }

    private fun sample(image: android.media.Image) {
        val now = System.currentTimeMillis()
        if (now - lastSample < 300) return
        lastSample = now
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val nv21 = ByteArray(width * height * 3 / 2)
        for (row in 0 until height) {
            val buffer = yPlane.buffer
            buffer.position(row * yPlane.rowStride)
            buffer.get(nv21, row * width, width)
        }
        var position = width * height
        for (row in 0 until height / 2) {
            for (column in 0 until width / 2) {
                vPlane.buffer.position(row * vPlane.rowStride + column * vPlane.pixelStride)
                nv21[position++] = vPlane.buffer.get()
                uPlane.buffer.position(row * uPlane.rowStride + column * uPlane.pixelStride)
                nv21[position++] = uPlane.buffer.get()
            }
        }
        val output = ByteArrayOutputStream()
        if (!YuvImage(nv21, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), 75, output)) return
        val bitmap = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size()) ?: return
        val pixel = bitmap.getPixel(
            (tapX * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
            (tapY * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        )
        bitmap.recycle()
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        runOnUiThread {
            swatch.text = "#%02X%02X%02X  ·  RGB %d, %d, %d".format(0xFFFFFF and pixel, red, green, blue)
            swatch.setBackgroundColor(pixel or 0xFF000000.toInt())
            val luminance = (0.299 * red + 0.587 * green + 0.114 * blue)
            swatch.setTextColor(if (luminance < 145) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            instruction.text = "Sampled the selected point. Tap elsewhere to sample again."
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_CAMERA) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else instruction.text = "Camera permission denied."
    }

    companion object { private const val REQUEST_CAMERA = 1 }
}
