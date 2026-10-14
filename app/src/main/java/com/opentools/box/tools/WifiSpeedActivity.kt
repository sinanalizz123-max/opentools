package com.opentools.box.tools

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.net.URL
import java.util.Locale

/** Wi-Fi connection details and a bounded HTTP(S) download speed test. */
class WifiSpeedActivity : AppCompatActivity() {
    private lateinit var info: TextView
    private lateinit var result: TextView
    private lateinit var url: EditText
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
        url = EditText(this).apply {
            setText("https://speed.hetzner.de/10MB.bin")
            hint = "HTTP or HTTPS test file URL"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        go = NativeButton(this).apply { text = "Test download speed" }
        go.setOnClickListener { test(url.text.toString().trim()) }
        showInfo()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Wi-Fi connection"; UiStyle.styleText(this, 24f, true) })
            addView(info, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 12f)
                bottomMargin = UiStyle.dp(context, 18f)
            })
            addView(TextView(context).apply { text = "Download test"; UiStyle.styleText(this, 19f, true) })
            addView(TextView(context).apply {
                text = "Uses mobile or Wi-Fi data depending on your active connection."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 10f))
            })
            addView(url, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(go, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(result, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "WiFi Speed", content))
    }

    @SuppressLint("SetTextI18n")
    private fun showInfo() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connection = wifi.connectionInfo
            info.text = "SSID: ${connection.ssid}\nSignal: ${connection.rssi} dBm\n" +
                "Link speed: ${connection.linkSpeed} Mbps\nIP: ${android.text.format.Formatter.formatIpAddress(connection.ipAddress)}"
        } catch (_: Exception) {
            info.text = "Wi-Fi details are unavailable."
        }
    }

    private fun test(urlString: String) {
        if (testing) return
        val parsed = try { URL(urlString) } catch (_: Exception) { null }
        if (parsed == null || parsed.protocol !in setOf("http", "https")) {
            url.error = "Enter a valid HTTP or HTTPS URL"
            return
        }
        testing = true
        go.isEnabled = false
        result.text = "Testing download speed…"
        Thread {
            try {
                val connection = parsed.openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    useCaches = false
                }
                val start = System.nanoTime()
                var bytes = 0L
                connection.getInputStream().use { input ->
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
                val mbps = bytes * 8.0 / 1_000_000.0 / seconds
                runOnUiThread {
                    result.text = String.format(Locale.getDefault(), "%.1f Mbps\n%.2f MB received", mbps, bytes / 1_000_000.0)
                }
            } catch (error: Exception) {
                runOnUiThread { result.text = "Test failed: ${error.message ?: "connection error"}" }
            } finally {
                testing = false
                runOnUiThread { go.isEnabled = true }
            }
        }.start()
    }

    companion object {
        private const val MAX_TEST_BYTES = 50L * 1024L * 1024L
        private const val MAX_TEST_SECONDS = 20.0
    }
}
