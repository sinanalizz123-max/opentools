package com.opentools.box.tools

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.UiStyle
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * LAN music share: host decodes an audio file and streams PCM over UDP;
 * joiners play it live. Original implementation.
 */
class MusicShareActivity : AppCompatActivity() {

    private lateinit var status: TextView
    @Volatile private var hosting = false
    @Volatile private var joining = false
    private var recvSocket: DatagramSocket? = null
    private var track: AudioTrack? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { host(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Idle · host a file or join another device."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val peer = EditText(this).apply {
            hint = "Peer IP address"
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val hostBtn = NativeButton(this).apply { text = "Host audio file" }
        val stopHost = NativeButton(this, false).apply { text = "Stop hosting" }
        val joinBtn = NativeButton(this, false).apply { text = "Join audio stream" }
        val stopJoin = NativeButton(this, false).apply { text = "Stop listening" }
        hostBtn.setOnClickListener { picker.launch(arrayOf("audio/*")) }
        stopHost.setOnClickListener { hosting = false; status.text = "Idle" }
        joinBtn.setOnClickListener { if (!joining) join() }
        stopJoin.setOnClickListener {
            joining = false
            try { recvSocket?.close() } catch (_: Exception) { }
            try { track?.pause(); track?.flush(); track?.stop(); track?.release() } catch (_: Exception) { }
            track = null
            status.text = "Listening stopped."
        }
        pendingPeer = peer
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
            addView(peer, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            listOf(hostBtn, stopHost, joinBtn, stopJoin).forEach { button ->
                addView(button, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(context, 8f)
                })
            }
        }))
    }

    private var pendingPeer: EditText? = null

    private fun host(uri: Uri) {
        val ip = pendingPeer?.text?.toString()?.trim().orEmpty()
        if (ip.isEmpty()) { status.text = "Enter peer IP first"; return }
        hosting = true
        status.text = "Streaming…"
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
                // Send header: sampleRate, channels.
                val header = ByteBuffer.allocate(8).putInt(sampleRate).putInt(channels).array()
                send.send(DatagramPacket(header, header.size, addr, 9994))
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
                        // Chunk into UDP packets.
                        var off = 0
                        while (off < pcm.size) {
                            val len = minOf(1400, pcm.size - off)
                            send.send(DatagramPacket(pcm, off, len, addr, 9994))
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

    private fun join() {
        joining = true
        status.text = "Listening on :9994"
        Thread {
            try {
                recvSocket = DatagramSocket(9994)
                // Header first.
                val hbuf = ByteArray(8)
                recvSocket!!.receive(DatagramPacket(hbuf, hbuf.size))
                val header = ByteBuffer.wrap(hbuf)
                val sampleRate = header.int
                val channels = header.int
                val cfg = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO
                    else AudioFormat.CHANNEL_OUT_MONO
                val minBuf = AudioTrack.getMinBufferSize(sampleRate, cfg,
                    AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, cfg,
                    AudioFormat.ENCODING_PCM_16BIT, minBuf, AudioTrack.MODE_STREAM)
                    .also { it.play() }
                val buf = ByteArray(4096)
                while (joining) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        recvSocket?.receive(pkt)
                        track?.write(pkt.data, 0, pkt.length)
                    } catch (_: Exception) { break }
                }
            } catch (_: Exception) { }
            try { track?.stop(); track?.release() } catch (_: Exception) { }
            track = null
            joining = false
            runOnUiThread { status.text = "Idle" }
        }.start()
    }

    override fun onPause() {
        hosting = false
        joining = false
        try { recvSocket?.close() } catch (_: Exception) { }
        super.onPause()
    }
}
