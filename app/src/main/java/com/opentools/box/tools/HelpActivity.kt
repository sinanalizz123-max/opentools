package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Help and troubleshooting guidance for the utility toolbox. */
class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Getting started"
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 12f))
            })
            addView(helpCard("Find a tool",
                "Search by name or category on the home screen. Add up to four frequently used tools to Quick access."))
            addView(helpCard("Permissions",
                "Camera, microphone, location, Bluetooth and activity permissions are requested only when a tool needs them. If you deny one, the related feature may be unavailable; you can change permissions in Android Settings."))
            addView(helpCard("Local sharing",
                "For LAN file sharing, CCTV and video calls, exchange the access token displayed by the hosting device. Only share it with a trusted peer on a network you trust."))
            addView(helpCard("Hardware support",
                "Sensors, IR transmitters, camera features and Bluetooth capabilities vary by device. A tool reports when required hardware or permissions are unavailable."))
            addView(helpCard("Troubleshooting",
                "If a tool stops working, reopen it, check the relevant permission, and confirm that the required sensor, network or connected device is available."))
        }
        setContentView(ToolScaffold.wrap(this, "Help", ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }))
    }

    private fun helpCard(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = UiStyle.dp(context, 16f)
        setPadding(pad, pad, pad, pad)
        background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        addView(TextView(context).apply {
            text = title
            UiStyle.styleText(this, 17f, true)
        })
        addView(TextView(context).apply {
            text = body
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 6f), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = UiStyle.dp(context, 10f)
        }
    }
}
