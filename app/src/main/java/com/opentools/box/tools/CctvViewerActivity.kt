package com.opentools.box.tools

import android.os.Bundle
import android.view.Gravity
import android.Manifest
import android.content.pm.PackageManager
import android.net.nsd.NsdServiceInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.core.app.ActivityCompat
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Token-authenticated CCTV viewer for a camera on the local network. */
class CctvViewerActivity : AppCompatActivity() {
    private var client: CctvLan.MjpegClient? = null
    private var lastFrameView: android.widget.ImageView? = null
    private fun snapShotBtn(view: android.widget.ImageView, status: android.widget.TextView) { try { (view.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { bmp -> val f = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "cctv_" + System.currentTimeMillis() + ".jpg"); f.parentFile?.mkdirs(); f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }; status.text = "Snapshot: " + f.absolutePath } ?: run { status.text = "No frame yet" } } catch (_: Exception) { status.text = "Snapshot failed" } }
    private var browseHandle: AutoCloseable? = null
    private val peers = mutableListOf<NsdServiceInfo>()
    private lateinit var peerAdapter: ArrayAdapter<String>
    private fun peerLabel(s: NsdServiceInfo): String {
        val ip = try { s.host?.hostAddress ?: "?" } catch (_: Exception) { "?" }
        return (s.serviceName ?: "?") + " · " + ip + ":" + s.port
    }
    private fun ensureLocBrowse() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 9001)
            return
        }
        startBrowse()
    }
    private fun startBrowse() {
        try { browseHandle?.close() } catch (_: Exception) { }
        browseHandle = LanDiscovery.browse(this, "_opentools-cam._tcp.") { list ->
            runOnUiThread {
                peers.clear(); peers.addAll(list)
                if (::peerAdapter.isInitialized) {
                    peerAdapter.clear(); peerAdapter.addAll(peers.map { peerLabel(it) })
                    if (peers.isEmpty()) peerAdapter.add("(searching for cameras…)")
                    peerAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    private fun connectTo(ip: String, secret: String, status: android.widget.TextView, watch: NativeButton, view: android.widget.ImageView) {
        if (ip.isBlank() || secret.isBlank()) { status.text = "Tap a camera and enter its access token."; return }
        client?.stop(); client = null
        status.text = "Connecting to camera…"
        watch.isEnabled = false
        client = CctvLan.MjpegClient(view,
            { message -> runOnUiThread { status.text = message; watch.isEnabled = true } },
            { runOnUiThread { status.text = "Connected to camera"; watch.isEnabled = true } }
        ).also { it.start("http://" + ip + ":" + CctvLan.PORT + "/", secret) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        peerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf("(searching for cameras…)"))
        val peerList = ListView(this).apply { adapter = peerAdapter }
        var pickedIp = ""
        var pickedLabel = ""
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
        peerList.setOnItemClickListener { _, _, pos, _ ->
            peers.getOrNull(pos)?.let { s ->
                try { pickedIp = s.host?.hostAddress ?: "" } catch (_: Exception) { }
                pickedLabel = peerLabel(s)
                status.text = "Selected: " + pickedLabel + " · enter token, tap Connect."
            }
        }
        watch.setOnClickListener { connectTo(pickedIp, token.text.toString().trim(), status, watch, view) }
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
            addView(peerList, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 150f)).apply {
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

    override fun onResume() {
        super.onResume()
        ensureLocBrowse()
    }
    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 9001 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startBrowse()
    }
    override fun onPause() {
        try { browseHandle?.close() } catch (_: Exception) { }
        browseHandle = null
        client?.stop()
        client = null
        super.onPause()
    }
}
