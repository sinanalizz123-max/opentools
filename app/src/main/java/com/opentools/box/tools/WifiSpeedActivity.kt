package com.opentools.box.tools

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Internet speed test: auto-picks the fastest of several working servers. */
class WifiSpeedActivity : AppCompatActivity() {

    private data class Server(val name: String, val url: String)

    private val servers = listOf(
        Server("Hetzner DE", "https://speed.hetzner.de/100MB.bin"),
        Server("Hetzner FI", "https://fsn-speed.hetzner.com/100MB.bin"),
        Server("Hetzner US", "https://ash-speed.hetzner.com/100MB.bin"),
        Server("Cloudflare", "https://speed.cloudflare.com/__down?bytes=50000000"),
        Server("OVH FR", "https://proof.ovh.net/files/100Mb.dat"),
        Server("Tele2 EU", "https://speedtest.tele2.net/100MB.zip"),
    )

    private lateinit var info: TextView
    private lateinit var result: TextView
    private lateinit var go: NativeButton
    @Volatile private var testing = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        info = TextView(this).apply {
            UiStyle.styleText(this, 14f, false)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        result = TextView(this).apply {
            text = "Speed results will appear here."
            UiStyle.styleText(this, 22f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 18f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        go = NativeButton(this).apply { text = "Test internet speed" }
        go.setOnClickListener { runTest() }
        showInfo()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Connection"; UiStyle.styleText(this, 24f, true) })
            addView(info, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 12f)
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(TextView(context).apply { text = "Speed test"; UiStyle.styleText(this, 19f, true) })
            addView(TextView(context).apply {
                text = "Automatically picks the fastest server, then measures download speed over your active connection (Wi-Fi or mobile data)."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 10f))
            })
            addView(go, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(result, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Internet Speed", content))
    }

    @SuppressLint("SetTextI18n")
    private fun showInfo() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connection = wifi.connectionInfo
            val ssid = connection.ssid.trim('"')
            info.text = if (connection.networkId == -1) {
                "Not on Wi-Fi — test will use mobile data."
            } else {
                "SSID: $ssid\nSignal: ${connection.rssi} dBm\n" +
                    "Link speed: ${connection.linkSpeed} Mbps\nIP: ${android.text.format.Formatter.formatIpAddress(connection.ipAddress)}"
            }
        } catch (_: Exception) {
            info.text = "Connection details are unavailable."
        }
    }

    private fun runTest() {
        if (testing) return
        testing = true
        go.isEnabled = false
        result.text = "Finding the fastest server…"
        Thread {
            try {
                val best = pickServer()
                if (best == null) {
                    ui("No test server reachable. Check your connection.")
                    return@Thread
                }
                ui("Testing via ${best.first.name} (ping ${best.second} ms)…")
                val mbps = download(best.first.url)
                ui(String.format(Locale.getDefault(),
                    "%.1f Mbps\nvia %s · ping %d ms",
                    mbps, best.first.name, best.second))
            } catch (error: Exception) {
                ui("Test failed: ${error.message ?: "connection error"}")
            } finally {
                testing = false
                runOnUiThread { go.isEnabled = true }
            }
        }.start()
    }

    /** Returns (server, pingMs) of the fastest reachable server, or null. */
    private fun pickServer(): Pair<Server, Long>? {
        var best: Pair<Server, Long>? = null
        for (server in servers) {
            if (!testing) return best
            val ping = ping(server.url)
            runOnUiThread { result.text = "Pinging ${server.name}…" }
            if (ping != null && (best == null || ping < best.second)) {
                best = server to ping
            }
        }
        return best
    }

    /** Minimal HEAD-style latency probe using a 1-byte range request. */
    private fun ping(urlString: String): Long? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                setRequestProperty("Range", "bytes=0-0")
            }
            val start = System.nanoTime()
            conn.inputStream.use { it.read() }
            val ms = (System.nanoTime() - start) / 1_000_000
            conn.disconnect()
            if (ms in 1..15000) ms else null
        } catch (_: Exception) {
            null
        }
    }

    private fun download(urlString: String): Double {
        val conn = URL(urlString).openConnection().apply {
            connectTimeout = 10000
            readTimeout = 15000
            useCaches = false
        }
        val start = System.nanoTime()
        var bytes = 0L
        conn.getInputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (bytes < MAX_TEST_BYTES) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                bytes += read
                if ((System.nanoTime() - start) / 1_000_000_000.0 >= MAX_TEST_SECONDS) break
            }
        }
        val seconds = ((System.nanoTime() - start) / 1_000_000_000.0).coerceAtLeast(0.001)
        return bytes * 8.0 / 1_000_000.0 / seconds
    }

    private fun ui(text: String) {
        runOnUiThread { result.text = text }
    }

    companion object {
        private const val MAX_TEST_BYTES = 25L * 1024L * 1024L
        private const val MAX_TEST_SECONDS = 15.0
    }
}
