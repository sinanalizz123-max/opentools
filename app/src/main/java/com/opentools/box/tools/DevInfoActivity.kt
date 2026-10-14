package com.opentools.box.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Device information screen with selectable details and a one-tap copy action. */
class DevInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val details = buildReport()
        val info = TextView(this).apply {
            text = details
            UiStyle.styleText(this, 14f, false)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 16f))
            setTextIsSelectable(true)
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val copy = NativeButton(this).apply { text = "Copy device details" }
        copy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Device information", details))
            Toast.makeText(this, "Device details copied", Toast.LENGTH_SHORT).show()
        }
        val share = NativeButton(this).apply { text = "Share as text" }
        share.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Device information")
                putExtra(Intent.EXTRA_TEXT, details)
            }
            startActivity(Intent.createChooser(send, "Share device details"))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Device information"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "These details are read locally from Android."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 14f))
            })
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(info)
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(copy, LinearLayout.LayoutParams(-1, -2))
            addView(share, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 8f)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Device Info", content))
    }

    private fun safe(block: () -> String): String = try {
        block()
    } catch (_: Exception) {
        "unavailable"
    }

    private fun fmtBytes(bytes: Long): String = when {
        bytes < 0 -> "unavailable"
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    private fun statFsInfo(path: java.io.File): String = safe {
        val fs = StatFs(path.absolutePath)
        val total = fs.blockCountLong * fs.blockSizeLong
        val free = fs.availableBlocksLong * fs.blockSizeLong
        "${fmtBytes(total)} total / ${fmtBytes(free)} free"
    }

    private fun buildReport(): String {
        val base = StringBuilder()
        base.appendLine("Manufacturer: ${safe { Build.MANUFACTURER }}")
        base.appendLine("Model: ${safe { Build.MODEL }}")
        base.appendLine("Device: ${safe { Build.DEVICE }}")
        base.appendLine("Board: ${safe { Build.BOARD }}")
        base.appendLine("Hardware: ${safe { Build.HARDWARE }}")
        base.appendLine("SoC: ${safe { Build.HARDWARE }}")
        base.appendLine("ABIs: ${safe { Build.SUPPORTED_ABIS.joinToString(", ") }}")
        base.appendLine("CPU cores: ${safe { Runtime.getRuntime().availableProcessors().toString() }}")
        base.appendLine("Memory class: ${safe { (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass.toString() + " MB" }}")
        base.appendLine("RAM: ${safe {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            "${fmtBytes(mi.totalMem)} total / ${fmtBytes(mi.availMem)} avail"
        }}")
        base.appendLine("Storage (internal): ${statFsInfo(filesDir)}")
        base.appendLine("Storage (external): ${safe {
            val dir = getExternalFilesDir(null) ?: filesDir
            statFsInfo(dir)
        }}")
        base.appendLine("External state: ${safe { Environment.getExternalStorageState() }}")
        base.appendLine("Battery: ${safe {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val cap = try { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toString() } catch (_: Exception) { "n/a" }
            val health = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i ->
                val h = i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val pluggedStr = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    0 -> "Unplugged"
                    else -> plugged.toString()
                }
                "health=$h plugged=$pluggedStr"
            } ?: ""
            "$level% capacity=$cap $health"
        }}")
        base.appendLine("Display: ${safe {
            val m = resources.displayMetrics
            "${m.widthPixels}x${m.heightPixels} density=${m.density} dpi=${m.densityDpi} scaled=${m.scaledDensity}"
        }}")
        base.appendLine("Refresh rate: ${safe {
            if (Build.VERSION.SDK_INT >= 30) "${display?.refreshRate} Hz" else "n/a"
        }}")
        base.appendLine("Sensors: ${safe {
            val sm = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
            sm.getSensorList(android.hardware.Sensor.TYPE_ALL).joinToString(", ") { it.name }.ifEmpty { "none" }
        }}")
        base.appendLine("Features: ${safe {
            val pm = packageManager
            listOf(
                "bluetooth" to android.content.pm.PackageManager.FEATURE_BLUETOOTH,
                "nfc" to android.content.pm.PackageManager.FEATURE_NFC,
                "location-gps" to android.content.pm.PackageManager.FEATURE_LOCATION_GPS,
                "wifi" to android.content.pm.PackageManager.FEATURE_WIFI,
                "telephony" to android.content.pm.PackageManager.FEATURE_TELEPHONY,
                "camera" to android.content.pm.PackageManager.FEATURE_CAMERA_ANY
            ).joinToString(", ") { (label, feat) -> "$label=${pm.hasSystemFeature(feat)}" }
        }}")
        base.appendLine("Android: ${safe { "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }}")
        base.appendLine("OS/Build: ${safe { "CODENAME=${Build.VERSION.CODENAME} INCREMENTAL=${Build.VERSION.INCREMENTAL} SDK=${Build.VERSION.SDK_INT}" }}")
        base.appendLine("Fingerprint: ${safe { Build.FINGERPRINT }}")
        base.appendLine("Security patch: ${safe { if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "n/a" }}")
        return base.toString().trim()
    }
}
