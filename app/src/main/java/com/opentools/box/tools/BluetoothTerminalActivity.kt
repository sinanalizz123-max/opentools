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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import java.util.UUID

/** Bluetooth serial terminal (SPP, e.g. Arduino). Original implementation. */
class BluetoothTerminalActivity : AppCompatActivity() {

    private var socket: BluetoothSocket? = null
    private var reading = false
    private lateinit var logView: ListView
    private lateinit var bondedView: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val lines = mutableListOf<String>()

    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lines)
        logView.adapter = adapter
        val address = EditText(this).apply { hint = "Device MAC, e.g. 00:11:22:33:44:55" }
        val connect = MaterialButton(this).apply { text = "Connect" }
        val input = EditText(this).apply { hint = "Send text…" }
        val send = MaterialButton(this).apply { text = "Send" }
        connect.setOnClickListener { connectTo(address.text.toString().trim()) }
        send.setOnClickListener {
            try {
                socket?.outputStream?.write((input.text.toString() + "\n").toByteArray())
                input.text.clear()
            } catch (e: Exception) { addLine("Send failed: ${e.message}") }
        }
        bondedView = TextView(this).apply { textSize = 14f; setPadding(12, 8, 12, 8) }
        refreshPairedDevices()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "BT Terminal",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bondedView)
            addView(address); addView(connect)
            addView(logView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(input); addView(send)
        }))
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
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
            bondedView.text = when {
                bluetooth == null -> "Bluetooth is not available on this device."
                !bluetooth.isEnabled -> "Turn Bluetooth on to see paired devices."
                bluetooth.bondedDevices.isEmpty() -> "No paired devices. Pair a device in Android Settings first."
                else -> "Paired devices:\n" + bluetooth.bondedDevices.joinToString("\n") { (it.name ?: "Unknown device") + " — " + it.address }
            }
        } catch (error: Exception) {
            bondedView.text = "Could not read paired devices: " + (error.message ?: "Bluetooth unavailable")
        }
    }

    private fun connectTo(mac: String) {
        if (mac.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }
        Thread {
            try {
                val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = manager.adapter.getRemoteDevice(mac)
                socket = device.createRfcommSocketToServiceRecord(spp)
                manager.adapter.cancelDiscovery()
                socket?.connect()
                addLine("Connected to $mac")
                startReading()
            } catch (e: Exception) {
                addLine("Connect failed: ${e.message}")
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
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) refreshPairedDevices()
        else bondedView.text = "Bluetooth permission denied."
    }

    override fun onPause() {
        reading = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        super.onPause()
    }
}
