package com.opentools.box.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
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
        val details = """
            Manufacturer: ${Build.MANUFACTURER}
            Model: ${Build.MODEL}
            Device: ${Build.DEVICE}
            Board: ${Build.BOARD}
            Hardware: ${Build.HARDWARE}
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Fingerprint: ${Build.FINGERPRINT}
            ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}
            CPU cores: ${Runtime.getRuntime().availableProcessors()}
            Memory class: ${(getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass} MB
        """.trimIndent()
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
        }
        setContentView(ToolScaffold.wrap(this, "Device Info", content))
    }
}
