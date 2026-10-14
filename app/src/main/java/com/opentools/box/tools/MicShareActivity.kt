package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.net.nsd.NsdServiceInfo
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.UiStyle
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * LAN microphone share: stream mic to peer, or listen for a stream.
 * Original implementation.
 */
class MicShareActivity : AppCompatActivity() {

    @Volatile private var streaming = false
    @Volatile private var listening = false
    @Volatile private var streamSocket: DatagramSocket? = null
    @Volatile private var listenSocket: DatagramSocket? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null
    private lateinit var status: TextView
    private lateinit var streamButton: NativeButton
    private lateinit var listenButton: NativeButton
    private var hostHandle: AutoCloseable? = null
    private var browseHandle: AutoCloseable? = null
    private var hostingNsd = false
    private var hostName = ""
    private var selectedIp = ""
    @Volatile private var sessionToken = ""
    private val peers = mutableListOf<NsdServiceInfo>()
    private lateinit var peerAdapter: ArrayAdapter<String>
    private val svcType = "_opentools-mic._udp."
    private fun peerLabel(s: NsdServiceInfo): String {
        val ip = try { s.host?.hostAddress ?: "?" } catch (_: Exception) { "?" }
        return (s.serviceName ?: "?") + " · " + ip + ":" + s.port
    }
    private fun ensureLocBrowse() {
        try { browseHandle?.close() } catch (_: Exception) { }
        browseHandle = null
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 9001)
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
                    if (peers.isEmpty()) peerAdapter.add("(searching for hosts…)")
                    peerAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    private fun stopDiscovery() {
        try { hostHandle?.close() } catch (_: Exception) { }
        try { browseHandle?.close() } catch (_: Exception) { }
        hostHandle = null; browseHandle = null; hostingNsd = false
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionToken = newToken()
        peerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf("(searching for hosts…)"))
        val peerList = ListView(this).apply { adapter = peerAdapter }
        val token = android.widget.EditText(this).apply {
            hint = "Peer access token"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val hostBtn = NativeButton(this, false).apply { text = "Start hosting" }
        hostBtn.setOnClickListener {
            if (hostingNsd) { try { hostHandle?.close() } catch (_: Exception) { }; hostHandle = null; hostingNsd = false; hostBtn.text = "Start hosting"; status.text = "Idle"; ensureLocBrowse() }
            else { startListen(); hostHandle = LanDiscovery.host(this, svcType, 9997, (android.os.Build.MODEL ?: "mic").replace(" ","-") + "-" + (0..999).random(), onRegistered = { n -> runOnUiThread { hostName = n; status.text = "Hosting as " + n + " · listening on :9997\nShare token: " + sessionToken } }, onError = { m -> runOnUiThread { status.text = "Host error: " + m } }); hostingNsd = true; hostBtn.text = "Stop hosting" }
        }
        peerList.setOnItemClickListener { _, _, pos, _ ->
            peers.getOrNull(pos)?.let { s -> try { selectedIp = s.host?.hostAddress ?: "" } catch (_: Exception) { }; status.text = "Peer: " + peerLabel(s); if (!streaming) startStream(selectedIp, token.text.toString().trim()) }
        }
        status = TextView(this).apply {
            text = "Idle · choose whether to stream your microphone or listen for a peer."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        streamButton = NativeButton(this).apply { text = "Start microphone stream" }
        listenButton = NativeButton(this, false).apply { text = "Listen on port 9997" }
        streamButton.setOnClickListener {
            if (streaming) stopStream() else startStream(selectedIp, token.text.toString().trim())
        }
        listenButton.setOnClickListener {
            if (listening) stopListen() else startListen()
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Mic Share", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Share audio on your LAN"
                UiStyle.styleText(this, 24f, true)
            })
            addView(TextView(context).apply {
                text = "Use the same Wi-Fi network and enter the receiving device's IP address."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(hostBtn, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(peerList, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 140f)).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(token, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(streamButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(listenButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(status)
        }))
    }

    override fun onResume() {
        super.onResume()
        ensureLocBrowse()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun startStream(ip: String, token: String) {
        if (ip.isBlank()) { status.text = "Tap a peer in the list first"; return }
        if (token.isBlank()) { status.text = "Enter the access token shown on the listening device"; return }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (streaming) return
        streaming = true
        streamButton.text = "Stop microphone stream"
        status.text = "Connecting to $ip…"
        Thread {
            var localRecorder: AudioRecord? = null
            var sender: DatagramSocket? = null
            try {
                val minIn = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minIn <= 0) throw IllegalStateException("Microphone audio format is unsupported")
                localRecorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 4096))
                if (localRecorder.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone unavailable")
                recorder = localRecorder
                localRecorder.startRecording()
                sender = DatagramSocket()
                streamSocket = sender
                val addr = InetAddress.getByName(ip)
                val prefix = (token + "\n").toByteArray()
                val buf = ByteArray(prefix.size + 2048)
                runOnUiThread { status.text = "Streaming to $ip" }
                while (streaming) {
                    val n = localRecorder.read(buf, prefix.size, buf.size - prefix.size)
                    if (n < 0) throw IllegalStateException("Microphone read failed")
                    if (n > 0) sender.send(DatagramPacket(buf, prefix.size + n, addr, 9997))
                }
            } catch (e: Exception) {
                if (streaming) runOnUiThread { status.text = "Stream failed: " + (e.message ?: "audio unavailable") }
            } finally {
                streaming = false
                try { sender?.close() } catch (_: Exception) { }
                try { localRecorder?.stop() } catch (_: Exception) { }
                try { localRecorder?.release() } catch (_: Exception) { }
                if (streamSocket === sender) streamSocket = null
                if (recorder === localRecorder) recorder = null
                runOnUiThread { streamButton.text = "Start microphone stream" }
            }
        }.start()
    }

    private fun stopStream() {
        streaming = false
        try { streamSocket?.close() } catch (_: Exception) { }
        try { recorder?.stop() } catch (_: Exception) { }
        streamButton.text = "Start streaming mic"
        status.text = "Idle"
    }

    private fun startListen() {
        if (listening) return
        listening = true
        listenButton.text = "Stop listening"
        status.text = "Listening on :9997"
        Thread {
            var receiver: DatagramSocket? = null
            var localPlayer: AudioTrack? = null
            try {
                receiver = DatagramSocket(9997)
                listenSocket = receiver
                val minOut = AudioTrack.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minOut <= 0) throw IllegalStateException("Speaker audio format is unsupported")
                localPlayer = AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minOut, 4096), AudioTrack.MODE_STREAM)
                if (localPlayer.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("Speaker unavailable")
                player = localPlayer
                localPlayer.play()
                val expected = (sessionToken + "\n").toByteArray()
                val buf = ByteArray(expected.size + 2048)
                while (listening) {
                    val pkt = DatagramPacket(buf, buf.size)
                    receiver.receive(pkt)
                    if (pkt.length >= expected.size &&
                        MessageDigest.isEqual(expected, pkt.data.copyOfRange(0, expected.size))) {
                        localPlayer.write(pkt.data, expected.size, pkt.length - expected.size)
                    }
                }
            } catch (e: Exception) {
                if (listening) runOnUiThread { status.text = "Listener failed: " + (e.message ?: "audio unavailable") }
            } finally {
                listening = false
                try { receiver?.close() } catch (_: Exception) { }
                try { localPlayer?.stop() } catch (_: Exception) { }
                try { localPlayer?.release() } catch (_: Exception) { }
                if (listenSocket === receiver) listenSocket = null
                if (player === localPlayer) player = null
                runOnUiThread { listenButton.text = "Listen on port 9997" }
            }
        }.start()
    }

    private fun stopListen() {
        listening = false
        try { listenSocket?.close() } catch (_: Exception) { }
        listenButton.text = "Listen on :9997"
        status.text = "Idle"
    }

    override fun onPause() {
        stopDiscovery()
        stopStream()
        stopListen()
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 9001) { if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startBrowse(); return }
        if (code != 1) return
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
    }
}
