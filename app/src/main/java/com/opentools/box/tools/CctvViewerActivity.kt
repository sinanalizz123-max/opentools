package com.opentools.box.tools

import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Token-authenticated CCTV viewer for a camera on the local network. */
class CctvViewerActivity : AppCompatActivity() {
    private var client: CctvLan.MjpegClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ip = EditText(this).apply {
            hint = "Camera IP address"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            UiStyle.styleInput(this)
        }
        val token = EditText(this).apply {
            hint = "Camera access token"
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val status = TextView(this).apply {
            text = "Enter the camera IP and token shown on its screen."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val view = ImageView(this).apply {
            adjustViewBounds = true
            contentDescription = "Live camera stream"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val watch = NativeButton(this).apply { text = "Connect to camera" }
        watch.setOnClickListener {
            client?.stop()
            client = null
            val address = ip.text.toString().trim()
            val secret = token.text.toString().trim()
            if (address.isBlank() || secret.isBlank()) {
                status.text = "Enter both the camera IP and access token."
                return@setOnClickListener
            }
            status.text = "Connecting to camera…"
            watch.isEnabled = false
            client = CctvLan.MjpegClient(
                view,
                { message -> runOnUiThread { status.text = message; watch.isEnabled = true } },
                { runOnUiThread { status.text = "Connected to camera"; watch.isEnabled = true } }
            ).also {
                it.start("http://" + address + ":" + CctvLan.PORT + "/", secret)
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Connect to a camera"; UiStyle.styleText(this, 23f, true) })
            addView(TextView(context).apply {
                text = "Both devices must be on a reachable local network."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 14f))
            })
            addView(ip, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(token, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(watch, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "CCTV Viewer", content))
    }

    override fun onPause() {
        client?.stop()
        client = null
        super.onPause()
    }
}
