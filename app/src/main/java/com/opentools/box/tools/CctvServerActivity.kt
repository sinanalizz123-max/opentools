package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.net.nsd.NsdServiceInfo
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.app.ActivityCompat
import com.opentools.box.UiStyle
import java.util.concurrent.atomic.AtomicReference

/** CCTV camera server: streams MJPEG on :9996. Original implementation. */
class CctvServerActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var status: TextView
    private lateinit var tokenView: TextView
    private lateinit var startButton: NativeButton
    private lateinit var stopButton: NativeButton
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private val accessToken = java.util.UUID.randomUUID().toString().replace("-", "")
    private var server: CctvLan.MjpegServer? = null
    private var recordingCctv = false
    private val recordFrames = mutableListOf<ByteArray>()
    private fun toggleRecord() { recordingCctv = !recordingCctv; if (!recordingCctv && recordFrames.isNotEmpty()) { try { val f = java.io.File(getExternalFilesDir(null), "cctv_" + System.currentTimeMillis() + ".mjpeg"); f.outputStream().use { it.write(recordFrames.reduce { a, b -> a + b }) }; status.text = "Saved " + f.absolutePath } catch (e: Exception) { status.text = "Record failed: " + e.message } ; recordFrames.clear() } }
    private fun snapshot() { try { val jpeg = latestJpeg.get() ?: return; val f = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "snap_" + System.currentTimeMillis() + ".jpg"); f.parentFile?.mkdirs(); f.writeBytes(jpeg); status.text = "Snapshot: " + f.absolutePath } catch (e: Exception) { status.text = "Snapshot failed" } }
    private var hostHandle: AutoCloseable? = null
    @Volatile private var active = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        status = TextView(this).apply {
            text = "Starting camera stream…"
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            setTextIsSelectable(true)
        }
        startButton = NativeButton(this).apply { text = "Start stream"; isEnabled = false }
        stopButton = NativeButton(this, false).apply { text = "Stop stream"; isEnabled = false }
        tokenView = TextView(this).apply {
            text = accessToken
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            contentDescription = "Viewer access token"
            UiStyle.styleText(this, 14f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            setOnClickListener { copyViewerToken() }
            setOnLongClickListener { copyViewerToken(); true }
        }
        val copyToken = NativeButton(this, false).apply { text = "Copy viewer token" }
        copyToken.setOnClickListener { copyViewerToken() }
        startButton.setOnClickListener { startCaptureIfAllowed() }
        stopButton.setOnClickListener { stopCapture() }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 18f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Share your camera on the local network"; UiStyle.styleText(this, 23f, true) })
            addView(TextView(context).apply {
                text = "Keep both devices on a trusted network. Only share the viewer token with the intended peer."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 16f))
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(tokenView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(copyToken, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(startButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(stopButton, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "CCTV Camera", content))
    }

    private fun copyViewerToken() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CCTV viewer token", accessToken))
        android.widget.Toast.makeText(this, "Viewer token copied", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        active = true
        startCaptureIfAllowed()
    }

    private fun startCaptureIfAllowed() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            status.text = "Camera permission is required to stream."
            return
        }
        active = true
        if (thread == null) {
            thread = HandlerThread("cctv").also { it.start() }
            handler = Handler(thread!!.looper)
        }
        if (hostHandle == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 9001)
            } else {
                hostHandle = LanDiscovery.host(this, "_opentools-cam._tcp.", CctvLan.PORT, (android.os.Build.MODEL ?: "cam").replace(" ","-") + "-" + (0..999).random(), onRegistered = { n -> runOnUiThread { status.text = "Hosting as " + n + " on port " + CctvLan.PORT } }, onError = { })
            }
        }
        if (server == null) server = CctvLan.MjpegServer(
            { latestJpeg.get() },
            accessToken,
            { message -> runOnUiThread { status.text = "Stream failed: " + message } }
        ).also { it.start() }
        status.text = "Streaming on port " + CctvLan.PORT
        startButton.isEnabled = false
        stopButton.isEnabled = true
        if (camera == null) openCamera()
    }

    private fun stopCapture() {
        active = false
        try { hostHandle?.close() } catch (_: Exception) { }
        hostHandle = null
        server?.stop(); server = null
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        thread?.quitSafely(); thread = null
        handler = null
        latestJpeg.set(null)
        if (::startButton.isInitialized) startButton.isEnabled = true
        if (::stopButton.isInitialized) stopButton.isEnabled = false
        if (::status.isInitialized) status.text = "Stream stopped. Tap Start stream to begin again."
    }

    override fun onPause() {
        stopCapture()
        super.onPause()
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Camera permission required."
            return
        }
        try {
            val id = cameraManager.cameraIdList.firstOrNull()
            if (id == null) {
                status.text = "No camera is available on this device."
                return
            }
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) {
                    if (!active) {
                        c.close()
                        return
                    }
                    camera = c
                    try { startSession(c) }
                    catch (error: Exception) {
                        runOnUiThread { status.text = "Camera setup failed: " + (error.message ?: "camera unavailable") }
                        c.close()
                        if (camera === c) camera = null
                    }
                }
                override fun onDisconnected(c: CameraDevice) {
                    if (camera === c) camera = null
                    c.close()
                    if (active) runOnUiThread { status.text = "Camera disconnected" }
                }
                override fun onError(c: CameraDevice, e: Int) {
                    if (camera === c) camera = null
                    c.close()
                    if (active) runOnUiThread { status.text = "Camera error (" + e + ")" }
                }
            }, handler)
        } catch (error: Exception) {
            status.text = "Could not open camera: " + (error.message ?: "camera unavailable")
        }
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
        try {
            val activeReader = reader ?: return
        val surface = activeReader.surface
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (!active) {
                        s.close()
                        return
                    }
                    session = s
                    try { s.setRepeatingRequest(req.build(), null, handler) }
                    catch (error: Exception) {
                        runOnUiThread { status.text = "Camera preview failed: " + (error.message ?: "camera unavailable") }
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    s.close()
                    runOnUiThread { status.text = "Camera preview configuration failed" }
                }
            }, handler)
        } catch (error: Exception) {
            status.text = "Could not start camera preview: " + (error.message ?: "camera unavailable")
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 26")
    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 9001) return
        if (code != REQUEST_CAMERA) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCaptureIfAllowed()
        } else {
            status.text = "Camera permission denied."
            startButton.isEnabled = true
        }
    }

    companion object { private const val REQUEST_CAMERA = 1 }
}
