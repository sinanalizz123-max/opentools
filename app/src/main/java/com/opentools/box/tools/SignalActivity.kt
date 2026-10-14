package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Cellular and Wi-Fi signal details, read locally from Android. */
class SignalActivity : AppCompatActivity() {
    private lateinit var valueView: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        valueView = TextView(this).apply {
            text = "Signal details will appear here."
            UiStyle.styleText(this, 15f, false)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 16f))
            textIsSelectable = true
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        status = TextView(this).apply {
            text = "Location permission is required for cellular details on many Android versions."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Network signal"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Signal readings vary with location, carrier, and device hardware."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 14f))
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(valueView)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Signal Strength", content))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            status.text = "Grant location permission to read cellular signal information."
            return
        }
        show()
    }

    private fun show() {
        val details = StringBuilder()
        try {
            val manager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cells = manager.allCellInfo
            if (cells.isNullOrEmpty()) details.append("No cellular information available.\n")
            for (cell in cells.orEmpty()) {
                if (cell is CellInfoLte) {
                    details.append("LTE RSRP: ${cell.cellSignalStrength.dbm} dBm\n")
                    details.append("LTE signal: ${cell.cellSignalStrength.level}/4\n")
                } else details.append("${cell.javaClass.simpleName}\n")
            }
        } catch (_: SecurityException) {
            details.append("Cellular permission denied.\n")
        } catch (_: Exception) {
            details.append("Cellular information unavailable.\n")
        }
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            details.append("Wi-Fi RSSI: ${wifi.connectionInfo.rssi} dBm")
        } catch (_: Exception) {
            details.append("Wi-Fi signal unavailable.")
        }
        valueView.text = details.toString()
        status.text = "Readings are estimates from the active radios."
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_LOCATION) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) show()
        else status.text = "Location permission denied."
    }

    companion object { private const val REQUEST_LOCATION = 1 }
}
