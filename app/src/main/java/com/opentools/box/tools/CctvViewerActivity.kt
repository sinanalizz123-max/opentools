package com.opentools.box.tools

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** CCTV viewer: watch a camera server on the LAN. Original implementation. */
class CctvViewerActivity : AppCompatActivity() {

    private var client: CctvLan.MjpegClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ip = EditText(this).apply { hint = "Camera IP, e.g. 192.168.1.5" }
        val status = TextView(this)
        val view = ImageView(this).apply { adjustViewBounds = true }
        val watch = MaterialButton(this).apply { text = "Watch" }
        watch.setOnClickListener {
            client?.stop()
            val address = ip.text.toString().trim()
            if (address.isEmpty()) return@setOnClickListener
            status.text = "Connecting…"
            client = CctvLan.MjpegClient(view) { status.text = it }.also {
                it.start("http://$address:${CctvLan.PORT}/")
            }
            status.text = "Streaming"
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ip); addView(watch); addView(status)
            addView(view, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    override fun onPause() {
        client?.stop()
        client = null
        super.onPause()
    }
}
