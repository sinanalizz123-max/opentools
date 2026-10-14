package com.opentools.box.tools

import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Device info: model, Android version, hardware. Original implementation. */
class DevInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = TextView(this).apply {
            textSize = 16f
            text = """
                Manufacturer: ${Build.MANUFACTURER}
                Model: ${Build.MODEL}
                Device: ${Build.DEVICE}
                Board: ${Build.BOARD}
                Hardware: ${Build.HARDWARE}
                Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                Fingerprint: ${Build.FINGERPRINT}
                ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}
                Cores: ${Runtime.getRuntime().availableProcessors()}
                Memory class: ${(getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass} MB
            """.trimIndent()
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Device Info",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(info)
        }))
    }
}
