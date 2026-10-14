package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Shows the current GPS fix and shares coordinates through Android's share sheet. */
class LocationShareActivity : AppCompatActivity(), LocationListener {
    private lateinit var manager: LocationManager
    private lateinit var coords: TextView
    private lateinit var status: TextView
    private lateinit var share: NativeButton
    private var last: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        coords = TextView(this).apply {
            text = "—"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 26f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 24f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 24f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
        }
        status = TextView(this).apply {
            text = "Waiting for a GPS location…"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
        }
        share = NativeButton(this).apply {
            text = "Share my location"
            isEnabled = false
        }
        share.setOnClickListener {
            val location = last ?: run {
                status.text = "Waiting for a GPS fix before sharing."
                return@setOnClickListener
            }
            val link = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
            }, "Share location"))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Your coordinates"
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 16f))
            })
            addView(coords, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 20f)
            })
            addView(share, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Share Location", content))
    }

    override fun onResume() {
        super.onResume()
        requestLocationIfAllowed()
    }

    private fun requestLocationIfAllowed() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            status.text = "Location permission is needed to find your coordinates."
            return
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this)
            status.text = "Waiting for a GPS fix…"
        } catch (_: SecurityException) {
            status.text = "Location permission was revoked. Allow it in Settings."
        } catch (_: Exception) {
            status.text = "GPS is unavailable. Check that location is enabled."
        }
    }

    override fun onPause() {
        manager.removeUpdates(this)
        super.onPause()
    }

    override fun onLocationChanged(location: Location) {
        last = location
        coords.text = "%.6f, %.6f".format(location.latitude, location.longitude)
        status.text = "Accuracy ±%.0f m".format(location.accuracy)
        share.isEnabled = true
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_LOCATION) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestLocationIfAllowed()
        } else {
            status.text = "Location permission denied."
            share.isEnabled = false
        }
    }

    companion object { private const val REQUEST_LOCATION = 1 }
}
