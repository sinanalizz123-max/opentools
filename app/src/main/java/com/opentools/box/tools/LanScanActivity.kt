package com.opentools.box.tools

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/** LAN host scan on the current Wi-Fi subnet with native status and result rows. */
class LanScanActivity : AppCompatActivity() {
    private val hosts = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private lateinit var scanButton: NativeButton
    @Volatile private var scanning = false
    @Volatile private var scanGeneration = 0
    private val isCancelled = AtomicBoolean(false)
    private lateinit var chart: SparkGraphView
    private fun aliasPrefs() = getSharedPreferences("device_alias", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Scan your current Wi-Fi network to look for reachable devices."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, hosts) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(this@LanScanActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 14f, false)
                row.setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 14f),
                    UiStyle.dp(context, 12f), UiStyle.dp(context, 14f))
                return row
            }
        }
        chart = SparkGraphView(this).apply { minimumHeight = UiStyle.dp(context, 80f) }; chart.setRange(0f, 10f)
        list.adapter = adapter
        scanButton = NativeButton(this).apply { text = "Scan local network" }
        scanButton.setOnClickListener { scan() }
        list.setOnItemLongClickListener { _, _, pos, _ -> val h = hosts.getOrNull(pos) ?: return@setOnItemLongClickListener true; val ip = h.substringAfterLast("·").trim().split(" ").firstOrNull() ?: return@setOnItemLongClickListener true; val ed = android.widget.EditText(this).apply { setText(aliasPrefs().getString(ip, "")) }; android.app.AlertDialog.Builder(this).setTitle("Alias for " + ip).setView(ed).setPositiveButton("Save") { _, _ -> aliasPrefs().edit().putString(ip, ed.text.toString()).apply() }.setNegativeButton("Cancel", null).show(); true }
        val exportBtn = NativeButton(this).apply { text = "Share scan results" }; exportBtn.setOnClickListener { val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, hosts.joinToString("\n")) }; startActivity(android.content.Intent.createChooser(i, "Share LAN scan")) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Nearby devices"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Only devices that respond to a reachability probe may appear."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 14f))
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(scanButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(chart, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 80f)))
            addView(exportBtn)
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "LAN Scanner", content))
    }

    private fun scan() {
        if (scanning) return
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifi.connectionInfo.ipAddress
        if (ip == 0) {
            status.text = "Not connected to Wi-Fi. Connect to a network and try again."
            return
        }
        val prefix = Formatter.formatIpAddress(ip).substringBeforeLast(".") + "."
        scanning = true
        isCancelled.set(false)
        val generation = ++scanGeneration
        hosts.clear()
        adapter.notifyDataSetChanged()
        scanButton.isEnabled = false
        status.text = "Scanning $prefix*… This may take a little while."
        Thread {
            for (host in 1..254) {
                if (isCancelled.get() || Thread.currentThread().isInterrupted || generation != scanGeneration || isFinishing) break
                try {
                    val address = InetAddress.getByName(prefix + host)
                    if (address.isReachable(300)) {
                        val name = try { address.hostName } catch (_: Exception) { prefix + host }
                        runOnUiThread {
                            if (generation == scanGeneration && !isFinishing) {
                                val alias = aliasPrefs().getString(address.hostAddress ?: "", null); hosts.add((alias ?: name) + "  ·  " + address.hostAddress); try { chart.push(hosts.size.toFloat()) } catch (_: Exception) {}
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
            runOnUiThread {
                if (generation == scanGeneration && !isFinishing) {
                    status.text = if (hosts.isEmpty()) "No responding devices found. Some devices block these probes."
                        else "${hosts.size} device(s) found"
                    scanning = false
                    scanButton.isEnabled = true
                }
            }
        }.start()
    }

    override fun onDestroy() {
        isCancelled.set(true)
        scanGeneration++
        scanning = false
        super.onDestroy()
    }
}
