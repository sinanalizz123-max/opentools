package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
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
 * LAN walkie-talkie: hold to talk, UDP audio to peer IP:9998.
 * Original implementation.
 */
class WalkieActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var hostHandle: AutoCloseable? = null
    private var browseHandle: AutoCloseable? = null
    private var hosting = false
    private var hostName = ""
    private var selectedIp = ""
    @Volatile private var sessionToken = ""
    private val peers = mutableListOf<NsdServiceInfo>()
    private lateinit var peerAdapter: ArrayAdapter<String>
    private val svcType = "_opentools-ptt._udp."
    private fun peerLabel(s: NsdServiceInfo): String {
        val ip = try { s.host?.hostAddress ?: "?" } catch (_: Exception) { "?" }
        return (s.serviceName ?: "?") + " · " + ip + ":" + s.port
    }
    private fun ensureLocBrowse() {
        try {
            browseHandle?.close()
        } catch (_: Exception) { }
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
        hostHandle = null; browseHandle = null; hosting = false
    }

    private var talking = false
    private var listening = true
    private var socket: DatagramSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

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
            if (hosting) { stopDiscovery(); hostBtn.text = "Start hosting"; status.text = "Listening on port 9998."; ensureLocBrowse() }
            else { hostHandle = LanDiscovery.host(this, svcType, 9998, (android.os.Build.MODEL ?: "walkie").replace(" ","-") + "-" + (0..999).random(), onRegistered = { n -> runOnUiThread { hostName = n; status.text = "Hosting as " + n + "\nShare token: " + sessionToken } }, onError = { m -> runOnUiThread { status.text = "Host error: " + m } }); hosting = true; hostBtn.text = "Stop hosting" }
        }
        peerList.setOnItemClickListener { _, _, pos, _ ->
            peers.getOrNull(pos)?.let { s -> try { selectedIp = s.host?.hostAddress ?: "" } catch (_: Exception) { }; status.text = "Peer: " + peerLabel(s) }
        }
        status = TextView(this).apply {
            text = "Listening on port 9998. Enter your peer's IP address."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val talk = NativeButton(this).apply {
            text = "Hold to talk"
            contentDescription = "Press and hold to transmit voice to the peer"
        }
        talk.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.performClick()
                    startTalking(token.text.toString().trim())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopTalking()
                    view.performClick()
                }
            }
            true
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Walkie-Talkie", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Push-to-talk"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Both devices should be on the same reachable network."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 16f))
            })
            addView(hostBtn, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(peerList, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 140f)).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(token, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(talk, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 64f)))
        }))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        startListening()
        ensureLocBrowse()
    }

    override fun onPause() {
        stopTalking()
        stopDiscovery()
        listening = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        super.onPause()
    }

    private fun newToken(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun startListening() {
        listening = true
        Thread {
            try {
                socket = DatagramSocket(9998)
                val minOut = AudioTrack.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                player = AudioTrack(AudioManager.STREAM_VOICE_CALL, 16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    minOut, AudioTrack.MODE_STREAM).also { it.play() }
                val expected = (sessionToken + "\n").toByteArray()
                val buf = ByteArray(expected.size + 2048)
                while (listening) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try {
                        socket?.receive(pkt)
                        if (!talking && pkt.length >= expected.size &&
                            MessageDigest.isEqual(expected, pkt.data.copyOfRange(0, expected.size))) {
                            player?.write(pkt.data, expected.size, pkt.length - expected.size)
                        }
                    } catch (_: Exception) { break }
                }
            } catch (_: Exception) { }
            try { player?.stop(); player?.release() } catch (_: Exception) { }
            player = null
        }.start()
    }

    private fun startTalking(token: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (talking) return
        val ip = selectedIp
        if (ip.isEmpty()) { status.text = "Tap a peer in the list first"; return }
        if (token.isEmpty()) { status.text = "Enter the access token shown on the receiving device"; return }
        talking = true
        status.text = "Talking…"
        Thread {
            try {
                val minIn = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minIn <= 0) throw IllegalStateException("Microphone audio format is unsupported")
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 4096))
                if (recorder?.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone unavailable")
                recorder?.startRecording()
                val send = DatagramSocket()
                val addr = InetAddress.getByName(ip)
                val prefix = (token + "\n").toByteArray()
                val buf = ByteArray(prefix.size + 2048)
                while (talking) {
                    val n = recorder?.read(buf, prefix.size, buf.size - prefix.size) ?: 0
                    if (n > 0) send.send(DatagramPacket(buf, prefix.size + n, addr, 9998))
                }
                send.close()
            } catch (e: Exception) {
                runOnUiThread { status.text = "Talk failed: ${e.message}" }
            }
            try { recorder?.stop(); recorder?.release() } catch (_: Exception) { }
            recorder = null
        }.start()
    }

    private fun stopTalking() {
        talking = false
        runOnUiThread { status.text = "Listening on :9998" }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 9001) { if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startBrowse(); return }
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else finish()
    }
}
