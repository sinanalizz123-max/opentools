package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/** Cellular + WiFi signal strength. Original implementation. */
class SignalActivity : AppCompatActivity() {

    private lateinit var valueView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        valueView = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Signal Strength",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(valueView)
        }))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        show()
    }

    private fun show() {
        val sb = StringBuilder()
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cells = if (Build.VERSION.SDK_INT >= 17) tm.allCellInfo else null
            if (cells.isNullOrEmpty()) sb.append("No cell info\n")
            for (cell in cells.orEmpty()) {
                if (cell is CellInfoLte) {
                    sb.append("LTE RSRP: ${cell.cellSignalStrength.dbm} dBm\n")
                } else {
                    sb.append("${cell.javaClass.simpleName}\n")
                }
            }
        } catch (_: SecurityException) {
            sb.append("Permission denied\n")
        }
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
                as android.net.wifi.WifiManager
            sb.append("WiFi RSSI: ${wm.connectionInfo.rssi} dBm")
        } catch (_: Exception) { }
        valueView.text = sb.toString()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) show()
        else finish()
    }
}
