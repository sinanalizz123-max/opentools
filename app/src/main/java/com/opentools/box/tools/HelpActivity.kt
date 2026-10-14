package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Help screen. */
class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(ToolScaffold.sectionTitle(context, "Using OpenTools"))
            addView(TextView(context).apply {
                text = "Choose a tool from the home screen or search by name. Use the category filters to narrow the list, and add your most-used tools to Quick access."
                UiStyle.styleText(this, 15f)
            })
            addView(ToolScaffold.sectionTitle(context, "Permissions"))
            addView(TextView(context).apply {
                text = "Camera, microphone, location, Bluetooth and activity permissions are requested only when a tool needs them. You can revoke permissions at any time in Android Settings."
                UiStyle.styleText(this, 15f)
            })
            addView(ToolScaffold.sectionTitle(context, "Local sharing"))
            addView(TextView(context).apply {
                text = "For LAN file sharing, CCTV and video calls, exchange the access token displayed by the device hosting the connection. Only share it with a trusted peer on your local network."
                UiStyle.styleText(this, 15f)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Help", content))
    }
}
