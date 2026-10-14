package com.opentools.box.tools

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.net.InetAddress

/** LAN host scan: pings /24 of current WiFi. Original implementation. */
class LanScanActivity : AppCompatActivity() {

    private val hosts = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 16f }
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, hosts)
        list.adapter = adapter
        val go = MaterialButton(this).apply { text = "Scan LAN" }
        go.setOnClickListener { scan() }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status); addView(go)
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    private fun scan() {
        if (scanning) return
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        if (ip == 0) { status.text = "Not on WiFi"; return }
        val prefix = Formatter.formatIpAddress(ip).substringBeforeLast(".") + "."
        scanning = true
        hosts.clear()
        adapter.notifyDataSetChanged()
        status.text = "Scanning $prefix* …"
        Thread {
            for (i in 1..254) {
                try {
                    val addr = InetAddress.getByName(prefix + i)
                    if (addr.isReachable(400)) {
                        val name = try { addr.hostName } catch (_: Exception) { prefix + i }
                        runOnUiThread {
                            hosts.add("$name  (${addr.hostAddress})")
                            adapter.notifyDataSetChanged()
                        }
                    }
                } catch (_: Exception) { }
            }
            runOnUiThread {
                status.text = "${hosts.size} host(s) found"
                scanning = false
            }
        }.start()
    }
}
