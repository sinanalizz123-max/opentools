package com.opentools.box.tools

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.net.URL

/** WiFi info + HTTP download speed test. Original implementation. */
class WifiSpeedActivity : AppCompatActivity() {

    private lateinit var info: TextView
    private lateinit var result: TextView
    private var testing = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        info = TextView(this).apply { textSize = 16f }
        result = TextView(this).apply { textSize = 28f }
        val url = EditText(this).apply {
            setText("https://speed.hetzner.de/10MB.bin")
        }
        val go = MaterialButton(this).apply { text = "Test speed" }
        go.setOnClickListener { test(url.text.toString()) }
        showInfo()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "WiFi Speed",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(info); addView(url); addView(go); addView(result)
        }))
    }

    @SuppressLint("SetTextI18n")
    private fun showInfo() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ci = wm.connectionInfo
        info.text = "SSID: ${ci.ssid}\nRSSI: ${ci.rssi} dBm\n" +
            "Link: ${ci.linkSpeed} Mbps\nIP: ${Formatter.formatIpAddress(ci.ipAddress)}"
    }

    private fun test(urlStr: String) {
        if (testing) return
        testing = true
        result.text = "Testing…"
        Thread {
            try {
                val conn = URL(urlStr).openConnection()
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                val start = System.currentTimeMillis()
                var bytes = 0L
                conn.getInputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        bytes += n
                        if (System.currentTimeMillis() - start > 20000) break
                    }
                }
                val secs = (System.currentTimeMillis() - start) / 1000.0
                val mbps = bytes * 8 / 1e6 / secs
                runOnUiThread { result.text = "%.1f Mbps".format(mbps) }
            } catch (e: Exception) {
                runOnUiThread { result.text = "Failed: ${e.message}" }
            } finally {
                testing = false
            }
        }.start()
    }
}
