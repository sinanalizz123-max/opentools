package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.UiStyle
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.Semaphore

/** Authenticated LAN file transfer with bounded memory use and private, local-only storage. */
class FileShareActivity : AppCompatActivity() {
    companion object { private const val MAX_FILE_BYTES = 512L * 1024L * 1024L }

    @Volatile private var server: ServerSocket? = null
    @Volatile private var serving = false
    @Volatile private var sessionToken = ""
    @Volatile private var serverGeneration = 0
    private val shared = Collections.synchronizedList(mutableListOf<Uri>())
    private val clientSlots = Semaphore(4)
    private lateinit var status: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val log = mutableListOf<String>()

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            shared.add(it)
            log.add(0, "Shared: " + (it.lastPathSegment ?: "file"))
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Server is off. Add files before starting a transfer."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, log) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(this@FileShareActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 14f, false)
                row.setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 12f),
                    UiStyle.dp(context, 12f), UiStyle.dp(context, 12f))
                return row
            }
        }
        list.adapter = adapter
        val share = NativeButton(this).apply { text = "Add file to share" }
        val toggle = NativeButton(this).apply { text = "Start server :9999" }
        val peer = EditText(this).apply {
            hint = "Peer IP address"
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val token = EditText(this).apply {
            hint = "Peer access token"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val fetch = NativeButton(this).apply { text = "Fetch files" }
        share.setOnClickListener { picker.launch(arrayOf("*/*")) }
        toggle.setOnClickListener {
            if (serving) stopServer() else startServer()
            toggle.text = if (serving) "Stop server" else "Start server :9999"
        }
        fetch.setOnClickListener { fetchFrom(peer.text.toString().trim(), token.text.toString().trim()) }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "LAN File Share", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Transfer files over Wi-Fi"
                UiStyle.styleText(this, 23f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 12f))
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(share, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(peer, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(token, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(fetch, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }))
    }

    private fun newToken(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun startServer() {
        if (serving) return
        serving = true
        val generation = ++serverGeneration
        sessionToken = newToken()
        status.text = "Starting server…"
        Thread {
            var localServer: ServerSocket? = null
            try {
                val activeServer = ServerSocket(9999)
                localServer = activeServer
                server = activeServer
                runOnUiThread {
                    if (serving && generation == serverGeneration) status.text = "Serving on :9999\nShare token: " + sessionToken
                }
                while (serving && generation == serverGeneration) {
                    val client = activeServer.accept()
                    if (!clientSlots.tryAcquire()) {
                        client.close()
                        continue
                    }
                    Thread {
                        try { serve(client) }
                        finally { clientSlots.release() }
                    }.start()
                }
            } catch (error: Exception) {
                if (serving && generation == serverGeneration) runOnUiThread {
                    serving = false
                    status.text = "Server failed: " + (error.message ?: "port unavailable")
                }
            } finally {
                try { localServer?.close() } catch (_: Exception) { }
                if (server === localServer) server = null
            }
        }.start()
    }

    private fun serve(socket: Socket) {
        try {
            socket.use { client ->
                client.soTimeout = 15000
                val input = DataInputStream(client.getInputStream())
                val output = DataOutputStream(client.getOutputStream())
                val suppliedToken = input.readUTF()
                val command = input.readUTF()
                if (!MessageDigest.isEqual(suppliedToken.toByteArray(), sessionToken.toByteArray()) || !serving) {
                    if (command == "LIST") output.writeInt(-1) else output.writeLong(-1)
                    output.flush()
                    return
                }
                if (command == "LIST") {
                    val snapshot = synchronized(shared) { shared.toList() }
                    output.writeInt(snapshot.size)
                    snapshot.forEachIndexed { index, uri ->
                        output.writeUTF(index.toString() + ":" + (uri.lastPathSegment ?: "file"))
                    }
                } else {
                    val index = command.removePrefix("GET ").toIntOrNull() ?: -1
                    val uri = synchronized(shared) { shared.getOrNull(index) }
                    if (uri == null) {
                        output.writeLong(-1)
                    } else {
                        sendFile(uri, output)
                    }
                }
                output.flush()
            }
        } catch (_: Exception) { }
    }

    private fun sendFile(uri: Uri, output: DataOutputStream) {
        val temp = File(cacheDir, "share-" + System.nanoTime() + ".tmp")
        try {
            val input = contentResolver.openInputStream(uri)
            if (input == null) {
                output.writeLong(-1)
                return
            }
            var total = 0L
            input.use { source ->
                temp.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_FILE_BYTES) {
                            output.writeLong(-1)
                            return
                        }
                        sink.write(buffer, 0, count)
                    }
                }
            }
            output.writeLong(total)
            temp.inputStream().buffered().use { it.copyTo(output) }
            output.writeUTF((uri.lastPathSegment ?: "file").substringAfterLast('/').takeLast(80))
        } catch (_: Exception) {
            try { output.writeLong(-1) } catch (_: Exception) { }
        } finally {
            temp.delete()
        }
    }

    private fun stopServer() {
        serving = false
        serverGeneration++
        sessionToken = ""
        try { server?.close() } catch (_: Exception) { }
        server = null
        status.text = "Server off"
    }

    private fun fetchFrom(ip: String, token: String) {
        if (ip.isBlank() || token.isBlank()) {
            status.text = "Enter the peer IP and the token shown on their server."
            return
        }
        Thread {
            try {
                val names: List<String> = Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, 9999), 8000)
                    socket.soTimeout = 10000
                    val output = DataOutputStream(socket.getOutputStream())
                    val input = DataInputStream(socket.getInputStream())
                    output.writeUTF(token)
                    output.writeUTF("LIST")
                    output.flush()
                    val count = input.readInt()
                    if (count < 0 || count > 1000) throw IOException("Authentication failed or invalid file list")
                    (0 until count).map { input.readUTF() }
                }
                var received = 0
                for (entry in names) {
                    val index = entry.substringBefore(":")
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, 9999), 8000)
                        socket.soTimeout = 60000
                        val output = DataOutputStream(socket.getOutputStream())
                        val input = DataInputStream(socket.getInputStream())
                        output.writeUTF(token)
                        output.writeUTF("GET " + index)
                        output.flush()
                        val size = input.readLong()
                        if (size < 0) throw IOException("File unavailable or too large")
                        if (size > MAX_FILE_BYTES) throw IOException("Peer sent an oversized file")
                        val folder = getExternalFilesDir(null) ?: filesDir
                        val temp = File(folder, "incoming-" + System.currentTimeMillis() + ".tmp")
                        var receivedFile: File? = null
                        try {
                            temp.outputStream().buffered().use { out ->
                                val buffer = ByteArray(16 * 1024)
                                var remaining = size
                                while (remaining > 0) {
                                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (count < 0) throw EOFException("File transfer ended early")
                                    out.write(buffer, 0, count)
                                    remaining -= count.toLong()
                                }
                            }
                            val rawName = input.readUTF().substringAfterLast('/').takeLast(80)
                            val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
                            var dest = File(folder, "recv-" + safeName)
                            if (dest.exists()) dest = File(folder, "recv-" + System.currentTimeMillis() + "-" + safeName)
                            if (!temp.renameTo(dest)) {
                                temp.copyTo(dest, overwrite = true)
                                temp.delete()
                            }
                            receivedFile = dest
                        } finally {
                            temp.delete()
                        }
                        received++
                        val savedName = receivedFile?.name ?: "file"
                        runOnUiThread {
                            log.add(0, "Received: " + savedName)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
                runOnUiThread { status.text = "Received " + received + " file(s)." }
            } catch (error: Exception) {
                runOnUiThread { status.text = "Transfer failed: " + (error.message ?: "connection error") }
            }
        }.start()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
