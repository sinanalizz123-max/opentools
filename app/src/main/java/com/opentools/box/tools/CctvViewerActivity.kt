package com.opentools.box.tools

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Token-authenticated CCTV viewer for a camera on the local network. */
class CctvViewerActivity : AppCompatActivity() {
    private var client: CctvLan.MjpegClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ip = EditText(this).apply { hint = "Camera IP, e.g. 192.168.1.5" }
        val token = EditText(this).apply { hint = "Camera access token" }
        val status = TextView(this).apply { text = "Enter the camera IP and token shown on its screen." }
        val view = ImageView(this).apply { adjustViewBounds = true; contentDescription = "Live camera stream" }
        val watch = MaterialButton(this).apply { text = "Connect" }
        watch.setOnClickListener {
            client?.stop()
            val address = ip.text.toString().trim()
            val secret = token.text.toString().trim()
            if (address.isBlank() || secret.isBlank()) {
                status.text = "Enter both the camera IP and access token."
                return@setOnClickListener
            }
            status.text = "Connecting to camera…"
            client = CctvLan.MjpegClient(view) { message -> status.text = message }.also {
                it.start("http://" + address + ":" + CctvLan.PORT + "/", secret)
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "CCTV Viewer", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ip)
            addView(token)
            addView(watch)
            addView(status)
            addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        }))
    }

    override fun onPause() {
        client?.stop()
        client = null
        super.onPause()
    }
}
