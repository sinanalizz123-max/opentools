package com.opentools.box.tools

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.nsd.NsdServiceInfo
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.app.ActivityCompat
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.UiStyle
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * LAN music share: host decodes an audio file and streams PCM over UDP;
 * joiners play it live. Original implementation.
 */
class MusicShareActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var connStatus: TextView
    private var volLevel = 0.8f
    private fun markConn(peer: String, connected: Boolean) { runOnUiThread { try { connStatus.text = peer + " · " + (if (connected) "connected" else "idle") } catch (_: Exception) {} } }
    private fun attachPtt(btn: NativeButton, start: () -> Unit, stop: () -> Unit) { btn.setOnTouchListener { v, e -> when (e.action) { android.view.MotionEvent.ACTION_DOWN -> { v.isPressed = true; start(); true } android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { v.isPressed = false; v.performClick(); stop(); true } else -> false } } }
    private fun volumeBar(ctx: android.content.Context, set: (Float) -> Unit): android.widget.SeekBar = android.widget.SeekBar(ctx).apply { max = 100; progress = 80; setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, u: Boolean) { volLevel = p / 100f; set(volLevel) } override fun onStartTrackingTouch(s: android.widget.SeekBar?) {} override fun onStopTrackingTouch(s: android.widget.SeekBar?) {} }) }
    @Volatile private var hosting = false
    @Volatile private var joining = false
    private var recvSocket: DatagramSocket? = null
    private var track: AudioTrack? = null
    private var hostHandle: AutoCloseable? = null
    private var browseHandle: AutoCloseable? = null
    private var hostingNsd = false
    private var hostName = ""
    private var selectedIp = ""
    @Volatile private var sessionToken = ""
    private val peers = mutableListOf<NsdServiceInfo>()
    private lateinit var peerAdapter: ArrayAdapter<String>
    private val svcType = "_opentools-music._udp."
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


    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { host(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionToken = newToken()
        status = TextView(this).apply {
            text = "Idle · host a file or join another device."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        peerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf("(searching for hosts…)"))
        val peerList = ListView(this).apply { adapter = peerAdapter }
        peerList.setOnItemClickListener { _, _, pos, _ ->
            peers.getOrNull(pos)?.let { s -> try { selectedIp = s.host?.hostAddress ?: "" } catch (_: Exception) { }; status.text = "Peer: " + peerLabel(s) }
        }
        val token = android.widget.EditText(this).apply {
            hint = "Peer access token"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val hostBtn = NativeButton(this).apply { text = "Start hosting" }
        val pickBtn = NativeButton(this).apply { text = "Host audio file" }
        val stopHost = NativeButton(this, false).apply { text = "Stop hosting" }
        val joinBtn = NativeButton(this, false).apply { text = "Join audio stream" }
        val stopJoin = NativeButton(this, false).apply { text = "Stop listening" }
        hostBtn.setOnClickListener {
            if (hostingNsd) { try { hostHandle?.close() } catch (_: Exception) { }; hostHandle = null; hostingNsd = false; hostBtn.text = "Start hosting"; status.text = "Idle"; ensureLocBrowse() }
            else { hostHandle = LanDiscovery.host(this, svcType, 9994, (android.os.Build.MODEL ?: "music").replace(" ","-") + "-" + (0..999).random(), onRegistered = { n -> runOnUiThread { hostName = n; status.text = "Hosting as " + n + " · pick a file to stream\nShare token: " + sessionToken } }, onError = { m -> runOnUiThread { status.text = "Host error: " + m } }); hostingNsd = true; hostBtn.text = "Stop hosting" }
        }
        pickBtn.setOnClickListener { picker.launch(arrayOf("audio/*")) }
        stopHost.setOnClickListener { hosting = false; status.text = "Idle" }
        joinBtn.setOnClickListener { if (!joining) join(token.text.toString().trim()) }
        stopJoin.setOnClickListener {
            joining = false
            try { recvSocket?.close() } catch (_: Exception) { }
            try { track?.pause(); track?.flush(); track?.stop(); track?.release() } catch (_: Exception) { }
            track = null
            status.text = "Listening stopped."
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Music Share", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Share music on your LAN"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Host an audio file for a peer or join a stream on port 9994."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 16f))
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(peerList, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 140f)).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(token, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            listOf(hostBtn, pickBtn, stopHost, joinBtn, stopJoin).forEach { button ->
                addView(button, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(context, 8f)
                })
            }
        }))
    }

    private fun newToken(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun host(uri: Uri) {
        val ip = selectedIp
        if (ip.isEmpty()) { status.text = "Tap a peer in the list first"; return }
        if (!hostingNsd) { hostHandle = LanDiscovery.host(this, svcType, 9994, (android.os.Build.MODEL ?: "music").replace(" ","-") + "-" + (0..999).random(), onRegistered = { n -> runOnUiThread { hostName = n } }, onError = { }); hostingNsd = true }
        hosting = true
        status.text = "Streaming…\nShare token: " + sessionToken
        Thread {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(this, uri, null)
                var audioTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        audioTrack = i
                        break
                    }
                }
                if (audioTrack < 0) throw IllegalArgumentException("no audio track")
                extractor.selectTrack(audioTrack)
                val fmt = extractor.getTrackFormat(audioTrack)
                val mime = fmt.getString(MediaFormat.KEY_MIME)!!
                val sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(fmt, null, null, 0)
                codec.start()
                val addr = InetAddress.getByName(ip)
                val send = DatagramSocket()
                val prefix = (sessionToken + "\n").toByteArray()
                // Send header: sampleRate, channels, preceded by the session token.
                val header = ByteBuffer.allocate(8).putInt(sampleRate).putInt(channels).array()
                val headerPacket = ByteArray(prefix.size + header.size)
                System.arraycopy(prefix, 0, headerPacket, 0, prefix.size)
                System.arraycopy(header, 0, headerPacket, prefix.size, header.size)
                send.send(DatagramPacket(headerPacket, headerPacket.size, addr, 9994))
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (hosting) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10000)
                    if (outIdx >= 0) {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        val pcm = ByteArray(info.size)
                        buf.get(pcm)
                        codec.releaseOutputBuffer(outIdx, false)
                        // Chunk into UDP packets, each prefixed with the session token.
                        var off = 0
                        while (off < pcm.size) {
                            val len = minOf(1400, pcm.size - off)
                            val pkt = ByteArray(prefix.size + len)
                            System.arraycopy(prefix, 0, pkt, 0, prefix.size)
                            System.arraycopy(pcm, off, pkt, prefix.size, len)
                            send.send(DatagramPacket(pkt, pkt.size, addr, 9994))
                            off += len
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                send.close()
                codec.stop(); codec.release()
                extractor.release()
            } catch (e: Exception) {
                runOnUiThread { status.text = "Stream failed: ${e.message}" }
            }
            hosting = false
            runOnUiThread { status.text = "Idle" }
        }.start()
    }

    private fun join(token: String) {
        if (token.isBlank()) { status.text = "Enter the access token shown on the host"; return }
        joining = true
        status.text = "Listening on :9994"
        Thread {
            try {
                val sock = try { DatagramSocket(9994) } catch (_: Exception) { null }
                if (sock == null) {
                    runOnUiThread { status.text = "Could not listen: port busy" }
                    joining = false
                    return@Thread
                }
                recvSocket = sock
                // Header arrives first, but only packets carrying the entered token are accepted.
                val expected = (token + "\n").toByteArray()
                val buf = ByteArray(4096)
                var configured = false
                while (joining) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    if (pkt.length < expected.size) continue
                    if (!MessageDigest.isEqual(expected, pkt.data.copyOfRange(0, expected.size))) continue
                    val payloadLen = pkt.length - expected.size
                    if (!configured) {
                        if (payloadLen < 8) continue
                        val header = ByteBuffer.wrap(pkt.data, expected.size, 8)
                        val sampleRate = header.int
                        val channels = header.int
                        val cfg = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO
                            else AudioFormat.CHANNEL_OUT_MONO
                        val minBuf = AudioTrack.getMinBufferSize(sampleRate, cfg,
                            AudioFormat.ENCODING_PCM_16BIT)
                        track = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, cfg,
                            AudioFormat.ENCODING_PCM_16BIT, minBuf, AudioTrack.MODE_STREAM)
                            .also { it.play() }
                        configured = true
                    } else {
                        track?.write(pkt.data, expected.size, payloadLen)
                    }
                }
            } catch (_: Exception) { }
            try { track?.stop(); track?.release() } catch (_: Exception) { }
            track = null
            joining = false
            runOnUiThread { status.text = "Idle" }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        ensureLocBrowse()
    }
    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 9001 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startBrowse()
    }
    override fun onPause() {
        stopDiscovery()
        hosting = false
        joining = false
        try { recvSocket?.close() } catch (_: Exception) { }
        super.onPause()
    }
}
