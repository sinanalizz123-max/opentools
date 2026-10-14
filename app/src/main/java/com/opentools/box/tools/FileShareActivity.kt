package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

/**
 * LAN file share: serve files on port 9999, or fetch from a peer IP.
 * Original implementation (plain TCP, no cloud).
 */
class FileShareActivity : AppCompatActivity() {

    private var server: ServerSocket? = null
    private var serving = false
    private val shared = mutableListOf<Uri>()
    private lateinit var status: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val log = mutableListOf<String>()

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            shared.add(it)
            log.add(0, "Shared: ${it.lastPathSegment}")
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 16f; text = "Server off" }
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, log)
        list.adapter = adapter
        val share = MaterialButton(this).apply { text = "Add file to share" }
        val toggle = MaterialButton(this).apply { text = "Start server :9999" }
        val peer = EditText(this).apply { hint = "Peer IP, e.g. 192.168.1.5" }
        val fetch = MaterialButton(this).apply { text = "Fetch list from peer" }
        share.setOnClickListener { picker.launch(arrayOf("*/*")) }
        toggle.setOnClickListener {
            if (serving) stopServer() else startServer()
            toggle.text = if (serving) "Stop server" else "Start server :9999"
        }
        fetch.setOnClickListener { fetchFrom(peer.text.toString().trim()) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(status); addView(share); addView(toggle); addView(peer); addView(fetch)
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    private fun startServer() {
        serving = true
        status.text = "Serving ${shared.size} file(s) on :9999"
        Thread {
            try {
                server = ServerSocket(9999)
                while (serving) {
                    val socket = server?.accept() ?: break
                    Thread { serve(socket) }.start()
                }
            } catch (_: Exception) { }
        }.start()
    }

    private fun serve(socket: Socket) {
        try {
            socket.use { s ->
                val input = DataInputStream(s.getInputStream())
                val output = DataOutputStream(s.getOutputStream())
                when (input.readUTF()) {
                    "LIST" -> {
                        output.writeInt(shared.size)
                        shared.forEachIndexed { i, uri ->
                            output.writeUTF("$i:${uri.lastPathSegment}")
                        }
                    }
                    else -> {
                        val index = input.readUTF().removePrefix("GET ").toIntOrNull() ?: -1
                        val uri = shared.getOrNull(index)
                        if (uri == null) {
                            output.writeLong(-1)
                        } else {
                            contentResolver.openInputStream(uri)?.use { ins ->
                                val bytes = ins.readBytes()
                                output.writeLong(bytes.size.toLong())
                                output.write(bytes)
                                output.writeUTF(uri.lastPathSegment ?: "file")
                            } ?: output.writeLong(-1)
                        }
                    }
                }
                output.flush()
            }
        } catch (_: Exception) { }
    }

    private fun stopServer() {
        serving = false
        try { server?.close() } catch (_: Exception) { }
        server = null
        status.text = "Server off"
    }

    private fun fetchFrom(ip: String) {
        if (ip.isEmpty()) return
        Thread {
            try {
                Socket(ip, 9999).use { s ->
                    s.soTimeout = 10000
                    val output = DataOutputStream(s.getOutputStream())
                    val input = DataInputStream(s.getInputStream())
                    output.writeUTF("LIST")
                    output.flush()
                    val n = input.readInt()
                    val names = (0 until n).map { input.readUTF() }
                    // Fetch each file.
                    for (entry in names) {
                        val index = entry.substringBefore(":")
                        Socket(ip, 9999).use { fs ->
                            fs.soTimeout = 60000
                            val fo = DataOutputStream(fs.getOutputStream())
                            val fi = DataInputStream(fs.getInputStream())
                            fo.writeUTF("GET $index")
                            fo.flush()
                            val size = fi.readLong()
                            if (size > 0) {
                                val bytes = ByteArray(size.toInt())
                                fi.readFully(bytes)
                                val name = fi.readUTF().takeLast(60)
                                val dest = File(getExternalFilesDir(null), "recv-$name")
                                dest.writeBytes(bytes)
                                runOnUiThread {
                                    log.add(0, "Received: ${dest.name}")
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                    runOnUiThread { log.add(0, "Fetched $n file(s)"); adapter.notifyDataSetChanged() }
                }
            } catch (e: Exception) {
                runOnUiThread { log.add(0, "Fetch failed: ${e.message}"); adapter.notifyDataSetChanged() }
            }
        }.start()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
