package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.Button
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Heart-rate via camera PPG (finger over lens + flash). Original implementation. */
class HeartRateActivity : AppCompatActivity() {

    private lateinit var bpmView: TextView
    private lateinit var hintView: TextView
    private lateinit var signalBar: android.widget.ProgressBar
    private lateinit var signalView: TextView
    private lateinit var flashToggle: Button
    private lateinit var cameraManager: CameraManager
    private var flashOn = true
    private var measuring = false
    private var pausedForCooldown = false
    private var sessionStartMs = 0L
    private val safetyHandler = Handler(android.os.Looper.getMainLooper())
    private val safetyTimeout = Runnable { pauseForCooldown() }
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val redSamples = ArrayDeque<Double>()
    private var lastBeat = 0L
    private val intervals = ArrayDeque<Long>()
    private val bpmHistory = ArrayDeque<Double>()
    private var smoothedBpm = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        bpmView = TextView(this).apply {
            text = "—"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 60f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 28f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 28f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
        }
        hintView = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "Cover the rear camera lens with a fingertip and hold still."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 14f))
        }
        signalBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        signalView = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "Signal: 0%"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        flashToggle = NativeButton(this).apply {
            text = "Flash: on"
            setOnClickListener {
                flashOn = !flashOn
                text = if (flashOn) "Flash: on" else "Flash: off"
                if (pausedForCooldown) return@setOnClickListener
                restartForFlashChange()
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 22f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Heart rate estimate"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(bpmView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(hintView, LinearLayout.LayoutParams(-1, -2))
            addView(signalBar, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 12f)
            })
            addView(signalView, LinearLayout.LayoutParams(-1, -2))
            addView(flashToggle, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 12f)
            })
            addView(TextView(context).apply {
                text = "Camera-based estimate only; not a medical device."
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 12f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 14f), 0, 0)
            })
        }
        try { val g = SparkGraphView(this); g.setRange(0f, 100f); (content as android.widget.LinearLayout).addView(g, android.widget.LinearLayout.LayoutParams(-1, com.opentools.box.UiStyle.dp(this, 160f))); val h = android.widget.TextView(this).apply { text = getPreferences(0).getString("hist", "No history yet") }; (content as android.widget.LinearLayout).addView(h); val goal = android.widget.EditText(this).apply { hint = "Goal / budget"; com.opentools.box.UiStyle.styleInput(this); setText(getPreferences(0).getString("goal","")) }; goal.setOnFocusChangeListener { _, has -> if (!has) getPreferences(0).edit().putString("goal", goal.text.toString()).apply() }; (content as android.widget.LinearLayout).addView(goal) } catch (_: Exception) { }
        setContentView(ToolScaffold.wrap(this, "Heart Rate", content))
    }

    override fun onResume() {
        super.onResume()
        pausedForCooldown = false
        thread = HandlerThread("ppg").also { it.start() }
        handler = Handler(thread!!.looper)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }
        openCamera()
    }

    override fun onPause() {
        safetyHandler.removeCallbacks(safetyTimeout)
        measuring = false
        try { camera?.let { flashOff(it) } } catch (_: Exception) { }
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        thread?.quitSafely(); thread = null
        super.onPause()
    }

    private fun backCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                && chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) return id
        }
        return null
    }

    private fun openCamera() {
        val id = backCameraId()
        if (id == null) {
            hintView.text = "A rear camera with a flash is required for this estimate."
            return
        }
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera = c; startSession(c, id) }
                override fun onDisconnected(c: CameraDevice) { c.close() }
                override fun onError(c: CameraDevice, e: Int) { c.close() }
            }, handler)
        } catch (_: SecurityException) { }
    }

    private fun startSession(device: CameraDevice, id: String) {
        reader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 3).apply {
            setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try { process(img) } finally { img.close() }
            }, handler)
        }
        val activeReader = reader ?: return
        val surface = activeReader.surface
        measuring = true
        sessionStartMs = System.currentTimeMillis()
        safetyHandler.removeCallbacks(safetyTimeout)
        safetyHandler.postDelayed(safetyTimeout, 60_000)
        val strengthSupported = supportsLowTorch(id)
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            try {
                if (flashOn && strengthSupported && Build.VERSION.SDK_INT >= 35) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    set(CaptureRequest.FLASH_STRENGTH_LEVEL, 1)
                } else if (flashOn) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                } else {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            } catch (_: Exception) { }
        }
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                s.setRepeatingRequest(req.build(), null, handler)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    /** Minimum-strength flash needs API 35 (FLASH_STRENGTH_LEVEL key). */
    private fun supportsLowTorch(id: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < 35) false
            else (cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1) > 1
        } catch (_: Exception) { false }
    }

    private fun restartForFlashChange() {
        try { session?.close() } catch (_: Exception) { }
        session = null
        try { camera?.close() } catch (_: Exception) { }
        camera = null
        measuring = false
        safetyHandler.removeCallbacks(safetyTimeout)
        if (::hintView.isInitialized && !pausedForCooldown) {
            hintView.text = "Cover the rear camera lens with a fingertip and hold still."
        }
        openCamera()
    }

    private fun pauseForCooldown() {
        if (!measuring) return
        measuring = false
        pausedForCooldown = true
        try { session?.close() } catch (_: Exception) { }
        session = null
        try { camera?.close() } catch (_: Exception) { }
        camera = null
        runOnUiThread {
            hintView.text = "Paused to let the flash cool down — tap to resume"
            flashToggle.text = "Resume measurement"
            flashToggle.setOnClickListener {
                pausedForCooldown = false
                flashToggle.text = if (flashOn) "Flash: on" else "Flash: off"
                flashToggle.setOnClickListener(null)
                flashToggle.setOnClickListener {
                    flashOn = !flashOn
                    flashToggle.text = if (flashOn) "Flash: on" else "Flash: off"
                    if (!pausedForCooldown) restartForFlashChange()
                }
                restartForFlashChange()
            }
        }
    }

    private fun flashOff(device: CameraDevice) { }

    private fun process(img: android.media.Image) {
        val buf = img.planes[0].buffer
        val w = img.width
        val h = img.height
        val stride = img.planes[0].rowStride
        var sum = 0L
        var sumSq = 0L
        var n = 0
        val row = ByteArray(stride)
        var r = h / 4
        while (r < h * 3 / 4) {
            buf.position(r * stride)
            buf.get(row)
            var c = w / 4
            while (c < w * 3 / 4) { val v = row[c].toInt() and 0xFF; sum += v; sumSq += v * v; n++; c += 4 }
            r += 4
        }
        val avg = if (n == 0) return else sum.toDouble() / n
        val variance = if (n == 0) 0.0 else sumSq.toDouble() / n - avg * avg
        // Signal strength: fingertip fully covering lens gives mid-bright, low-variance red field.
        // Too dark = not covered / no flash coupling; very high variance = moving / partial cover.
        val brightnessScore = (avg / 255.0 * 100.0).coerceIn(0.0, 100.0)
        val steadiness = (1.0 - (variance / 2500.0)).coerceIn(0.0, 1.0)
        val strength = (brightnessScore * 0.5 + steadiness * 50.0).toInt().coerceIn(0, 100)
        val covered = avg > 40 && variance < 2500
        val still = variance < 900
        runOnUiThread {
            signalBar.progress = strength
            signalView.text = "Signal: $strength%"
            hintView.text = when {
                !covered -> "Step 1: cover the lens — press your fingertip fully over the rear camera + flash."
                !still || redSamples.size < 60 -> "Step 2: hold still — keep the finger steady."
                intervals.size < 3 -> "Step 3: measuring… keep holding still."
                else -> "Measuring… keep holding still."
            }
        }
        redSamples.addLast(avg)
        while (redSamples.size > 90) redSamples.removeFirst()
        if (redSamples.size < 60) return
        if (!covered) return
        // Loosened peak detection on detrended signal.
        val mean = redSamples.average()
        val std = kotlin.math.sqrt(redSamples.map { (it - mean) * (it - mean) }.average()).coerceAtLeast(0.5)
        val margin = std * 0.25
        val prev = redSamples.dropLast(1).takeLast(1).firstOrNull() ?: return
        val prevPrev = redSamples.dropLast(2).takeLast(1).firstOrNull() ?: return
        val now = System.currentTimeMillis()
        if (prevPrev < mean + margin && prev >= mean - margin && avg < mean && now - lastBeat > 300) {
            if (lastBeat > 0) {
                val interval = now - lastBeat
                // Require consistent beats: reject intervals that jump too far from recent average.
                val ok = intervals.isEmpty() || kotlin.math.abs(interval - intervals.average()) < intervals.average() * 0.35
                if (ok) {
                    intervals.addLast(interval)
                    while (intervals.size > 8) intervals.removeFirst()
                    if (intervals.size >= 3) {
                        val bpm = 60000.0 / intervals.average()
                        if (bpm in 35.0..220.0) {
                            bpmHistory.addLast(bpm)
                            while (bpmHistory.size > 5) bpmHistory.removeFirst()
                            smoothedBpm = bpmHistory.average()
                            runOnUiThread { bpmView.text = "%.0f".format(smoothedBpm) + " bpm" }
                        }
                    } else {
                        runOnUiThread { bpmView.text = "…" }
                    }
                }
            }
            lastBeat = now
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else hintView.text = "Camera permission denied."
    }
}
