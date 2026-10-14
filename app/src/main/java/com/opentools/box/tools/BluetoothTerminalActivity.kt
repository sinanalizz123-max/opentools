package com.opentools.box.tools

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.UiStyle
import java.util.UUID

/** Bluetooth serial terminal (SPP, e.g. Arduino). Original implementation. */
class BluetoothTerminalActivity : AppCompatActivity() {

    private var socket: BluetoothSocket? = null
    private var reading = false
    private lateinit var logView: ListView
    private lateinit var bondedView: TextView
    private lateinit var statusView: TextView
    private lateinit var pairedList: ListView
    private lateinit var sendButton: NativeButton
    private val pairedEntries = mutableListOf<Pair<String, String>>()
    private lateinit var pairedAdapter: ArrayAdapter<String>
    private var connectedName: String? = null
    private var connecting = false
    private lateinit var adapter: ArrayAdapter<String>
    private val lines = mutableListOf<String>()

    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = ListView(this)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, lines) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(this@BluetoothTerminalActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 13f, false)
                row.setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 10f),
                    UiStyle.dp(context, 12f), UiStyle.dp(context, 10f))
                return row
            }
        }
        logView.adapter = adapter
        val address = EditText(this).apply {
            hint = "Device MAC address"
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val connect = NativeButton(this).apply { text = "Connect" }
        val input = EditText(this).apply {
            hint = "Message to send"
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val send = NativeButton(this).apply { text = "Send"; isEnabled = false }
        sendButton = send
        connect.setOnClickListener { connectTo(address.text.toString().trim()) }
        send.setOnClickListener {
            try {
                socket?.outputStream?.write((input.text.toString() + "\n").toByteArray())
                input.text.clear()
            } catch (e: Exception) { addLine("Send failed: ${e.message}") }
        }
        bondedView = TextView(this).apply {
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            text = "Connect to Bluetooth serial devices like Arduino HC-05/HC-06.\n1. Pair in system Settings → Bluetooth\n2. Pick a device below or enter its MAC\n3. Tap Connect\n4. Type a message + Send"
        }
        statusView = TextView(this).apply {
            UiStyle.styleText(this, 14f, true)
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 10f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        pairedList = ListView(this)
        pairedAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        pairedList.adapter = pairedAdapter
        pairedList.setOnItemClickListener { _, _, pos, _ ->
            pairedEntries.getOrNull(pos)?.let { address.setText(it.second) }
        }
        updateStatus()
        refreshPairedDevices()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "BT Terminal", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Bluetooth serial terminal"; UiStyle.styleText(this, 23f, true) })
            addView(bondedView, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 10f)
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(statusView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(pairedList, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 110f)).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(address, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(connect, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 12f) })
            addView(logView, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(send, LinearLayout.LayoutParams(-1, -2))
        }))
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 31 &&
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1)
        }
    }

    private fun updateStatus() {
        val text = when {
            socket != null && socket?.isConnected == true -> "Connected" + (connectedName?.let { " — $it" } ?: "")
            connecting -> "Connecting…"
            else -> "Disconnected"
        }
        statusView.text = "Status: $text"
        if (::sendButton.isInitialized) sendButton.isEnabled = socket?.isConnected == true
    }

    private fun refreshPairedDevices() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            bondedView.text = "Bluetooth permission is required to list paired devices."
            return
        }
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetooth = manager.adapter
            pairedEntries.clear()
            val labels = mutableListOf<String>()
            when {
                bluetooth == null -> labels.add("Bluetooth is not available on this device.")
                !bluetooth.isEnabled -> labels.add("Turn Bluetooth on to see paired devices.")
                bluetooth.bondedDevices.isEmpty() -> labels.add("No paired devices. Pair a device in Android Settings first.")
                else -> bluetooth.bondedDevices.forEach {
                    pairedEntries.add((it.name ?: "Unknown device") to it.address)
                    labels.add((it.name ?: "Unknown device") + " — " + it.address)
                }
            }
            if (::pairedAdapter.isInitialized) {
                pairedAdapter.clear()
                pairedAdapter.addAll(labels)
                pairedAdapter.notifyDataSetChanged()
            }
        } catch (error: Exception) {
            if (::pairedAdapter.isInitialized) {
                pairedAdapter.clear()
                pairedAdapter.add("Could not read paired devices: " + (error.message ?: "Bluetooth unavailable"))
            }
        }
    }

    private fun connectTo(mac: String) {
        if (mac.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 31 &&
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1)
            return
        }
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        connectedName = null
        connecting = true
        runOnUiThread { updateStatus() }
        Thread {
            var connection: BluetoothSocket? = null
            try {
                val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = manager.adapter.getRemoteDevice(mac)
                connectedName = try { device.name } catch (_: Exception) { null }
                runOnUiThread { updateStatus() }
                connection = device.createRfcommSocketToServiceRecord(spp)
                socket = connection
                manager.adapter.cancelDiscovery()
                connection.connect()
                connecting = false
                addLine("Connected to $mac")
                runOnUiThread { updateStatus() }
                startReading()
            } catch (e: Exception) {
                try { connection?.close() } catch (_: Exception) { }
                if (socket === connection) socket = null
                connecting = false
                connectedName = null
                addLine("Connect failed: ${e.message}")
                runOnUiThread { updateStatus() }
            }
        }.start()
    }

    private fun startReading() {
        reading = true
        Thread {
            val buf = ByteArray(1024)
            val pending = StringBuilder()
            while (reading) {
                try {
                    val n = socket?.inputStream?.read(buf) ?: break
                    if (n <= 0) break
                    pending.append(String(buf, 0, n))
                    var idx = pending.indexOf("\n")
                    while (idx >= 0) {
                        addLine("« " + pending.substring(0, idx).trim())
                        pending.delete(0, idx + 1)
                        idx = pending.indexOf("\n")
                    }
                } catch (_: Exception) { break }
            }
        }.start()
    }

    private fun addLine(line: String) {
        runOnUiThread {
            lines.add(line)
            if (lines.size > 200) lines.removeAt(0)
            adapter.notifyDataSetChanged()
            logView.setSelection(lines.size - 1)
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 26")
    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != 1) return
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) refreshPairedDevices()
        else bondedView.text = "Bluetooth permission denied."
    }

    override fun onPause() {
        reading = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        connecting = false
        connectedName = null
        try { updateStatus() } catch (_: Exception) { }
        super.onPause()
    }
}
