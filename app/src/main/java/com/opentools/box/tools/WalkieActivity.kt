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
 * LAN walkie-talkie: hold to talk, UDP audio to peer IP:9998.
 * Original implementation.
 */
class WalkieActivity : AppCompatActivity() {

    private lateinit var peerInput: EditText
    private lateinit var status: TextView
    private var talking = false
    private var listening = true
    private var socket: DatagramSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        peerInput = EditText(this).apply { hint = "Peer IP, e.g. 192.168.1.5" }
        status = TextView(this).apply { textSize = 16f; text = "Listening on :9998" }
        val talk = MaterialButton(this).apply { text = "HOLD TO TALK" }
        talk.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> startTalking()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopTalking()
            }
            true
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Walkie-Talkie",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(peerInput); addView(status); addView(talk)
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
    }

    override fun onPause() {
        stopTalking()
        listening = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        super.onPause()
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
                val buf = ByteArray(2048)
                while (listening) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try {
                        socket?.receive(pkt)
                        if (!talking) player?.write(pkt.data, 0, pkt.length)
                    } catch (_: Exception) { break }
                }
            } catch (_: Exception) { }
            try { player?.stop(); player?.release() } catch (_: Exception) { }
            player = null
        }.start()
    }

    private fun startTalking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (talking) return
        val ip = peerInput.text.toString().trim()
        if (ip.isEmpty()) { status.text = "Enter peer IP first"; return }
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
                val buf = ByteArray(2048)
                while (talking) {
                    val n = recorder?.read(buf, 0, buf.size) ?: 0
                    if (n > 0) send.send(DatagramPacket(buf, n, addr, 9998))
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
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else finish()
    }
}
