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
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.net.nsd.NsdServiceInfo
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.UiStyle
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * LAN video call: both sides open this screen and enter each other's IP.
 * Camera publishes MJPEG on :9996, microphone streams UDP to peer :9995.
 * Original implementation.
 */
class VideoCallActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var remoteView: ImageView
    private lateinit var status: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private var mjpegServer: CctvLan.MjpegServer? = null
    private var mjpegClient: CctvLan.MjpegClient? = null

    @Volatile private var callActive = false
    private val accessToken = java.util.UUID.randomUUID().toString().replace("-", "")
    private var hostHandle: AutoCloseable? = null
    private var browseHandle: AutoCloseable? = null
    private var hostingNsd = false
    private var hostName = ""
    private val peers = mutableListOf<NsdServiceInfo>()
    private lateinit var peerAdapter: ArrayAdapter<String>
    private val svcType = "_opentools-call._tcp."
    private var pickedIp = ""
    private fun peerLabel(s: NsdServiceInfo): String {
        val ip = try { s.host?.hostAddress ?: "?" } catch (_: Exception) { "?" }
        return (s.serviceName ?: "?") + " · " + ip + ":" + s.port
    }
    private fun ensureLocBrowse() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 9001)
            return
        }
        startBrowse()
    }
    private fun startBrowse() {
        try { browseHandle?.close() } catch (_: Exception) { }
        browseHandle = LanDiscovery.browse(this, svcType) { list ->
            runOnUiThread {
                peers.clear(); peers.addAll(list.filter { it.serviceName != hostName })
                if (::peerAdapter.isInitialized) {
                    peerAdapter.clear(); peerAdapter.addAll(peers.map { peerLabel(it) })
                    if (peers.isEmpty()) peerAdapter.add("(searching for callers…)")
                    peerAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    private fun pendingPeer(): String? = if (pickedIp.isBlank()) null else pickedIp
    private var pendingPeerToken: String? = null
    private var audioSocket: DatagramSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        peerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf("(searching for callers…)"))
        val peerList = ListView(this).apply { adapter = peerAdapter }
        val hostBtn = NativeButton(this, false).apply { text = "Start hosting" }
        val peerToken = android.widget.EditText(this).apply {
            hint = "Peer camera/audio token"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        status = TextView(this).apply {
            text = "Enter a peer IP and token to join a call."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 10f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 12f, UiStyle.border(context))
        }
        val localToken = TextView(this).apply {
            text = "Your token: " + accessToken
            UiStyle.styleText(this, 11f, false, UiStyle.muted(context))
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 12f, UiStyle.border(context))
            contentDescription = "Your video call access token"
        }
        val copyToken = NativeButton(this, false).apply {
            text = "Copy token"
            contentDescription = "Copy your access token"
        }
        copyToken.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Video call access token", accessToken))
            android.widget.Toast.makeText(this, "Access token copied", android.widget.Toast.LENGTH_SHORT).show()
        }
        remoteView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Remote video stream"
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val join = NativeButton(this).apply { text = "Join call" }
        val hangup = NativeButton(this, false).apply { text = "Hang up" }
        peerList.setOnItemClickListener { _, _, pos, _ ->
            peers.getOrNull(pos)?.let { s -> try { pickedIp = s.host?.hostAddress ?: "" } catch (_: Exception) { }; status.text = "Peer: " + peerLabel(s) }
        }
        hostBtn.setOnClickListener {
            if (hostingNsd) { try { hostHandle?.close() } catch (_: Exception) { }; hostHandle = null; hostingNsd = false; hostBtn.text = "Start hosting"; ensureLocBrowse() }
            else { hostHandle = LanDiscovery.host(this, svcType, CctvLan.PORT, (android.os.Build.MODEL ?: "call").replace(" ","-") + "-" + (0..999).random(), onRegistered = { n -> runOnUiThread { hostName = n; status.text = "Hosting as " + n } }, onError = { }); hostingNsd = true; hostBtn.text = "Stop hosting" }
        }
        join.setOnClickListener { joinCall(pickedIp, peerToken.text.toString().trim()) }
        hangup.setOnClickListener { hangUp() }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "LAN Video Call", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Call another device on your local network"
                UiStyle.styleText(this, 22f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 4f))
            })
            addView(TextView(context).apply {
                text = "Both devices must be reachable. Exchange IP addresses and access tokens only with someone you trust."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(context, 14f))
            })
            addView(hostBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(peerList, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 130f)).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(peerToken, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(localToken, LinearLayout.LayoutParams(0, -2, 2.4f).apply {
                    marginEnd = UiStyle.dp(context, 6f)
                })
                addView(copyToken, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(join, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = UiStyle.dp(context, 6f) })
                addView(hangup, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(remoteView, LinearLayout.LayoutParams(-1, 0, 1f))
        }))
    }

    override fun onResume() {
        super.onResume()
        ensureLocBrowse()
    }

    private fun startCaptureIfAllowed() {
        val peer = pendingPeer() ?: return
        val need = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
            return
        }
        if (thread == null) {
            thread = HandlerThread("vcall").also { it.start() }
            handler = Handler(thread!!.looper)
        }
        if (mjpegServer == null) mjpegServer = CctvLan.MjpegServer(
            { latestJpeg.get() },
            accessToken,
            { message -> if (callActive) runOnUiThread { status.text = "Video stream failed: " + message } }
        ).also { it.start() }
        if (camera == null) openCamera()
        val peerSecret = pendingPeerToken ?: return
        pendingPeerToken = null
        beginCall(peer, peerSecret)
    }

    override fun onPause() {
        if (callActive || mjpegServer != null || camera != null) hangUp()
        try { hostHandle?.close() } catch (_: Exception) { }
        try { browseHandle?.close() } catch (_: Exception) { }
        hostHandle = null; browseHandle = null; hostingNsd = false
        super.onPause()
    }

    private fun joinCall(ip: String, peerToken: String) {
        if (ip.isBlank() || peerToken.isBlank()) {
            status.text = "Tap a peer and enter their token."
            return
        }
        if (callActive) return
        pickedIp = ip
        pendingPeerToken = peerToken
        status.text = "Checking permissions…"
        startCaptureIfAllowed()
    }

    private fun beginCall(ip: String, peerToken: String) {
        callActive = true
        status.text = "In call with " + ip
        mjpegClient = CctvLan.MjpegClient(
            remoteView,
            { message -> status.text = message },
            { status.text = "In call with " + ip }
        ).also {
            it.start("http://" + ip + ":" + CctvLan.PORT + "/", peerToken)
        }
        startAudio(ip, peerToken)
    }

    private fun hangUp(message: String = "Call ended") {
        pickedIp = ""
        pendingPeerToken = null
        callActive = false
        mjpegClient?.stop(); mjpegClient = null
        try { audioSocket?.close() } catch (_: Exception) { }
        audioSocket = null
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) { }
        recorder = null
        try { player?.stop(); player?.release() } catch (_: Exception) { }
        player = null
        mjpegServer?.stop(); mjpegServer = null
        try { session?.close() } catch (_: Exception) { }
        session = null
        try { camera?.close() } catch (_: Exception) { }
        camera = null
        try { reader?.close() } catch (_: Exception) { }
        reader = null
        thread?.quitSafely(); thread = null; handler = null
        latestJpeg.set(null)
        runOnUiThread { status.text = message }
    }

    private fun startAudio(ip: String, peerToken: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Microphone permission required"
            return
        }
        Thread {
            try {
                val addr = InetAddress.getByName(ip)
                audioSocket = DatagramSocket(9995)
                val minIn = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minIn <= 0) throw IllegalStateException("Microphone audio format is unsupported")
                recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minIn)
                if (recorder?.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone could not start")
                recorder?.startRecording()
                val minOut = AudioTrack.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minOut <= 0) throw IllegalStateException("Speaker audio format is unsupported")
                player = AudioTrack(AudioManager.STREAM_VOICE_CALL, 16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    minOut, AudioTrack.MODE_STREAM)
                if (player?.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("Speaker could not start")
                player?.play()
                // Receiver.
                Thread {
                    val buf = ByteArray(4096)
                    while (callActive) {
                        try {
                            val pkt = DatagramPacket(buf, buf.size)
                            audioSocket?.receive(pkt)
                            val localSecret = accessToken.toByteArray(Charsets.UTF_8)
                            if (pkt.length <= localSecret.size) continue
                            if (!java.security.MessageDigest.isEqual(
                                    pkt.data.copyOfRange(0, localSecret.size), localSecret)) continue
                            player?.write(pkt.data, localSecret.size, pkt.length - localSecret.size)
                        } catch (_: Exception) { break }
                    }
                }.start()
                // Sender.
                val send = DatagramSocket()
                val secret = peerToken.toByteArray(Charsets.UTF_8)
                val buf = ByteArray(2048)
                val packetData = ByteArray(secret.size + buf.size)
                while (callActive) {
                    val n = recorder?.read(buf, 0, buf.size) ?: 0
                    if (n > 0) {
                        System.arraycopy(secret, 0, packetData, 0, secret.size)
                        System.arraycopy(buf, 0, packetData, secret.size, n)
                        try { send.send(DatagramPacket(packetData, secret.size + n, addr, 9995)) }
                        catch (_: Exception) { break }
                    }
                }
                send.close()
            } catch (error: Exception) {
                if (callActive) hangUp("Audio connection failed: " + (error.message ?: "audio unavailable"))
            }
        }.start()
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Camera permission required."
            return
        }
        // Front camera preferred for calls.
        val ids = cameraManager.cameraIdList
        val front = ids.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(
                android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
        } ?: ids.firstOrNull() ?: return
        try {
            cameraManager.openCamera(front, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) {
                    if (!callActive) {
                        c.close()
                        return
                    }
                    camera = c
                    try {
                        startSession(c)
                    } catch (error: Exception) {
                        hangUp("Camera setup failed: " + (error.message ?: "camera unavailable"))
                    }
                }
                override fun onDisconnected(c: CameraDevice) {
                    if (camera === c) camera = null
                    c.close()
                    if (callActive) runOnUiThread { status.text = "Camera disconnected" }
                }
                override fun onError(c: CameraDevice, e: Int) {
                    if (camera === c) camera = null
                    c.close()
                    if (callActive) runOnUiThread { status.text = "Camera error (" + e + ")" }
                }
            }, handler)
        } catch (error: Exception) {
            hangUp("Could not open camera: " + (error.message ?: "camera unavailable"))
        }
    }

    private fun startSession(device: CameraDevice) {
        reader = ImageReader.newInstance(480, 360, ImageFormat.YUV_420_888, 2).apply {
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
        val activeReader = reader ?: return
        val surface = activeReader.surface
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
        }
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                s.setRepeatingRequest(req.build(), null, handler)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = Unit
        }, handler)
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 26")
    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 9001) { if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startBrowse(); return }
        if (code != 1) return
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) startCaptureIfAllowed()
        else {
            pickedIp = ""
            pendingPeerToken = null
            finish()
        }
    }
}
