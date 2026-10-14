package com.opentools.box.tools

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton

/** Nearby WiFi network scan. Original implementation. */
class WifiScanActivity : AppCompatActivity() {

    private lateinit var wm: WifiManager
    private lateinit var adapter: ArrayAdapter<String>
    private val networks = mutableListOf<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            networks.clear()
            try {
                for (r in wm.scanResults) {
                    networks.add("${r.SSID}  (${r.level} dBm, ${r.frequency} MHz)")
                }
            } catch (_: SecurityException) { }
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, networks)
        list.adapter = adapter
        val scan = MaterialButton(this).apply { text = "Scan" }
        scan.setOnClickListener { startScan() }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "WiFi Scanner",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scan)
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }))
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else startScan()
    }

    override fun onPause() {
        unregisterReceiver(receiver)
        super.onPause()
    }

    private fun startScan() {
        try { wm.startScan() } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startScan()
    }
}
