package com.opentools.box.tools

import android.Manifest
import android.content.Context
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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
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
    private var pendingPeer: String? = null
    private var pendingPeerToken: String? = null
    private var audioSocket: DatagramSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ip = EditText(this).apply { hint = "Peer IP, e.g. 192.168.1.5" }
        val peerToken = EditText(this).apply { hint = "Peer camera/audio token" }
        status = TextView(this).apply { text = "Enter peer IP and token to join" }
        val localToken = TextView(this).apply {
            text = "Your access token: " + accessToken
            textSize = 12f
            setTextIsSelectable(true)
        }
        remoteView = ImageView(this).apply { adjustViewBounds = true }
        val join = MaterialButton(this).apply { text = "Join call" }
        val hangup = MaterialButton(this).apply { text = "Hang up" }
        join.setOnClickListener { joinCall(ip.text.toString().trim(), peerToken.text.toString().trim()) }
        hangup.setOnClickListener { hangUp() }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "LAN Video Call",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ip); addView(peerToken); addView(localToken); addView(status)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(join); addView(hangup)
            })
            addView(remoteView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }))
    }

    override fun onResume() {
        super.onResume()
        // Do not activate camera or microphone until the user explicitly joins a call.
    }

    private fun startCaptureIfAllowed() {
        val peer = pendingPeer ?: return
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
        if (mjpegServer == null) mjpegServer = CctvLan.MjpegServer({ latestJpeg.get() }, accessToken).also { it.start() }
        if (camera == null) openCamera()
        val peerSecret = pendingPeerToken ?: return
        pendingPeer = null
        pendingPeerToken = null
        beginCall(peer, peerSecret)
    }

    override fun onPause() {
        // A permission prompt can briefly pause the activity; don't discard a pending join
        // before its permission result arrives.
        if (callActive || mjpegServer != null || camera != null) hangUp()
        super.onPause()
    }

    private fun joinCall(ip: String, peerToken: String) {
        if (ip.isBlank() || peerToken.isBlank()) {
            status.text = "Enter both the peer IP and token."
            return
        }
        if (callActive) return
        pendingPeer = ip
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

    private fun hangUp() {
        pendingPeer = null
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
        runOnUiThread { status.text = "Call ended" }
    }

    private fun startAudio(ip: String, peerToken: String) {
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
                if (callActive) runOnUiThread {
                    status.text = "Audio connection failed: " + (error.message ?: "audio unavailable")
                }
            }
        }.start()
    }

    private fun openCamera() {
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
                        runOnUiThread { status.text = "Camera setup failed: " + (error.message ?: "camera unavailable") }
                        hangUp()
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
            runOnUiThread { status.text = "Could not open camera: " + (error.message ?: "camera unavailable") }
            hangUp()
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
        val surface = reader!!.surface
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
        if (code != 1) return
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) startCaptureIfAllowed()
        else {
            pendingPeer = null
            pendingPeerToken = null
            finish()
        }
    }
}
