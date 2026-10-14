package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

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
    private lateinit var streamButton: MaterialButton
    private lateinit var listenButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val peer = EditText(this).apply { hint = "Peer IP to stream to" }
        status = TextView(this).apply { textSize = 16f; text = "Idle" }
        streamButton = MaterialButton(this).apply { text = "Start streaming mic" }
        listenButton = MaterialButton(this).apply { text = "Listen on :9997" }
        streamButton.setOnClickListener {
            if (streaming) stopStream() else startStream(peer.text.toString().trim())
        }
        listenButton.setOnClickListener {
            if (listening) stopListen() else startListen()
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Mic Share",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(peer); addView(streamButton); addView(listenButton); addView(status)
        }))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun startStream(ip: String) {
        if (ip.isBlank()) { status.text = "Enter peer IP"; return }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (streaming) return
        streaming = true
        streamButton.text = "Stop streaming"
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
                val buf = ByteArray(2048)
                runOnUiThread { status.text = "Streaming to $ip" }
                while (streaming) {
                    val n = localRecorder.read(buf, 0, buf.size)
                    if (n < 0) throw IllegalStateException("Microphone read failed")
                    if (n > 0) sender.send(DatagramPacket(buf, n, addr, 9997))
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
                runOnUiThread { streamButton.text = "Start streaming mic" }
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
                val buf = ByteArray(2048)
                while (listening) {
                    val pkt = DatagramPacket(buf, buf.size)
                    receiver.receive(pkt)
                    localPlayer.write(pkt.data, 0, pkt.length)
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
                runOnUiThread { listenButton.text = "Listen on :9997" }
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
        stopStream()
        stopListen()
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
    }
}
