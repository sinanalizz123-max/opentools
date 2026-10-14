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

    private var streaming = false
    private var listening = false
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val peer = EditText(this).apply { hint = "Peer IP to stream to" }
        status = TextView(this).apply { textSize = 16f; text = "Idle" }
        val stream = MaterialButton(this).apply { text = "Start streaming mic" }
        val listen = MaterialButton(this).apply { text = "Listen on :9997" }
        stream.setOnClickListener {
            if (streaming) stopStream()
            else startStream(peer.text.toString().trim())
            stream.text = if (streaming) "Stop streaming" else "Start streaming mic"
        }
        listen.setOnClickListener {
            if (!listening) startListen()
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(peer); addView(stream); addView(listen); addView(status)
        })
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun startStream(ip: String) {
        if (ip.isEmpty()) { status.text = "Enter peer IP"; return }
        streaming = true
        status.text = "Streaming to $ip"
        Thread {
            try {
                val minIn = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val rec = AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minIn)
                rec.startRecording()
                val send = DatagramSocket()
                val addr = InetAddress.getByName(ip)
                val buf = ByteArray(2048)
                while (streaming) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) send.send(DatagramPacket(buf, n, addr, 9997))
                }
                send.close()
                rec.stop(); rec.release()
            } catch (e: Exception) {
                runOnUiThread { status.text = "Stream failed: ${e.message}" }
                streaming = false
            }
        }.start()
    }

    private fun stopStream() {
        streaming = false
        status.text = "Idle"
    }

    private fun startListen() {
        listening = true
        status.text = "Listening on :9997"
        Thread {
            try {
                val sock = DatagramSocket(9997)
                val minOut = AudioTrack.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    minOut, AudioTrack.MODE_STREAM).also { it.play() }
                val buf = ByteArray(2048)
                while (listening) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        sock.receive(pkt)
                        track.write(pkt.data, 0, pkt.length)
                    } catch (_: Exception) { break }
                }
                track.stop(); track.release()
                sock.close()
            } catch (_: Exception) { }
        }.start()
    }

    override fun onPause() {
        streaming = false
        listening = false
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
    }
}
