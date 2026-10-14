package com.opentools.box.tools

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Nearby Wi-Fi scanner with native controls and permission feedback. */
class WifiScanActivity : AppCompatActivity() {
    private lateinit var wifi: WifiManager
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private lateinit var scanButton: NativeButton
    private var reenableTask: Runnable? = null
    private var destroyed = false

    override fun onDestroy() {
        destroyed = true
        if (::scanButton.isInitialized) reenableTask?.let { scanButton.removeCallbacks(it) }
        super.onDestroy()
    }
    private val networks = mutableListOf<String>()
    private val rawResults = mutableListOf<Triple<String,String,Int>>()
    private lateinit var chart: SparkGraphView
    private fun aliasPrefs() = getSharedPreferences("device_alias", MODE_PRIVATE)
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true) == false) {
                status.text = "Scan did not complete. Wait a moment and try again."
                return
            }
            networks.clear()
            try {
                wifi.scanResults.forEach { result ->
                    rawResults.add(Triple(result.SSID.ifBlank { "Hidden" }, result.BSSID, result.level))
                    try { chart.push(result.level.toFloat()) } catch (_: Exception) {}
                    val name = result.SSID.ifBlank { "Hidden network" }
                    networks.add("$name  ·  ${result.level} dBm  ·  ${result.frequency} MHz")
                }
            } catch (_: SecurityException) {
                status.text = "Location permission is required to read scan results."
            }
            val ap = aliasPrefs()
            networks.clear()
            rawResults.forEach { (ssid, bssid, lvl) -> val alias = ap.getString(bssid, null); networks.add((alias ?: ssid) + "  ·  " + lvl + " dBm  ·  " + bssid) }
            adapter.notifyDataSetChanged()
            if (networks.isEmpty()) status.text = "No networks found. Try scanning again."
            else status.text = "${networks.size} network(s) found"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        status = TextView(this).apply {
            text = "Scanning requires Wi-Fi and location permission."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 10f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 12f, UiStyle.border(context))
        }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, networks) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(this@WifiScanActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 14f, false)
                row.setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 12f),
                    UiStyle.dp(context, 12f), UiStyle.dp(context, 12f))
                return row
            }
        }
        chart = SparkGraphView(this).apply { minimumHeight = UiStyle.dp(context, 80f) }; try { chart.setRange(-100f, -30f) } catch (_: Exception) {}
        list.adapter = adapter
        scanButton = NativeButton(this).apply { text = "Scan Wi-Fi networks" }
        scanButton.setOnClickListener { startScan() }
        list.setOnItemLongClickListener { _, _, pos, _ -> val r = rawResults.getOrNull(pos) ?: return@setOnItemLongClickListener true; val ed = android.widget.EditText(this).apply { setText(aliasPrefs().getString(r.second, "")) }; android.app.AlertDialog.Builder(this).setTitle("Alias for " + r.second).setView(ed).setPositiveButton("Save") { _, _ -> aliasPrefs().edit().putString(r.second, ed.text.toString()).apply(); adapter.notifyDataSetChanged() }.setNegativeButton("Cancel", null).show(); true }
        val exportBtn = NativeButton(this).apply { text = "Share scan results" }; exportBtn.setOnClickListener { val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, networks.joinToString("\n")) }; startActivity(android.content.Intent.createChooser(i, "Share Wi-Fi scan")) };
        (list.parent as? android.widget.LinearLayout)?.addView(exportBtn)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Nearby Wi-Fi networks"; UiStyle.styleText(this, 23f, true) })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 8f)
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(scanButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "WiFi Scanner", content))
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            receiverRegistered = true
        }
        startScan()
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Grant location permission to scan nearby Wi-Fi networks."
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            return
        }
        try {
            status.text = "Scanning nearby networks…"
            scanButton.isEnabled = false
            val started = wifi.startScan()
            if (!started) status.text = "Android throttled the scan. Wait a moment and try again."
            reenableTask = Runnable { if (!destroyed) scanButton.isEnabled = true }
            scanButton.postDelayed(reenableTask, 1500)
        } catch (_: Exception) {
            status.text = "Could not start a Wi-Fi scan."
            scanButton.isEnabled = true
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_LOCATION) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startScan()
        else status.text = "Location permission denied. Wi-Fi scan results are unavailable."
    }

    companion object { private const val REQUEST_LOCATION = 1 }
}
