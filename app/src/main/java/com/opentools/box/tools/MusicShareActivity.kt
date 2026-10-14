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
import com.google.android.material.button.MaterialButton
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
        status = TextView(this).apply { textSize = 16f; text = "Idle" }
        val peer = EditText(this).apply { hint = "Peer IP to stream to" }
        val hostBtn = MaterialButton(this).apply { text = "Host: pick audio" }
        val stopHost = MaterialButton(this).apply { text = "Stop hosting" }
        val joinBtn = MaterialButton(this).apply { text = "Join: listen :9994" }
        hostBtn.setOnClickListener { picker.launch(arrayOf("audio/*")) }
        stopHost.setOnClickListener { hosting = false; status.text = "Idle" }
        joinBtn.setOnClickListener { if (!joining) join() }
        pendingPeer = peer
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(status); addView(peer); addView(hostBtn); addView(stopHost); addView(joinBtn)
        })
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
