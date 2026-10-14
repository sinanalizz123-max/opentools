package com.opentools.box.tools

import android.Manifest
import android.content.Context
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

/** GPS location: coordinates, accuracy, and speed. */
class GpsActivity : AppCompatActivity(), LocationListener {
    private lateinit var manager: LocationManager
    private lateinit var coords: TextView
    private lateinit var extra: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        coords = TextView(this).apply {
            text = "Waiting for GPS…"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 28f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 24f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 24f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
            setTextIsSelectable(true)
            contentDescription = "Coordinates. Long-press to copy."
            setOnLongClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("coords", text.toString()))
                android.widget.Toast.makeText(this@GpsActivity, "Coordinates copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        }
        extra = TextView(this).apply {
            text = "Accuracy: —"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 18f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 16f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        status = TextView(this).apply {
            text = "Waiting for location permission and a satellite fix."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 12f), 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Current position"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(coords, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(extra, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "GPS Location", content))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), REQUEST_LOCATION)
            status.text = "Location permission is needed to read coordinates."
            return
        }
        startUpdates()
    }

    private fun startUpdates() {
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            try { manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, this) } catch (_: Exception) { }
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
            status.text = "Waiting for a fresh location fix…"
        } catch (_: SecurityException) {
            status.text = "Location permission was revoked."
        } catch (_: Exception) {
            status.text = "GPS is unavailable. Check that location is enabled."
        }
    }

    override fun onPause() {
        manager.removeUpdates(this)
        super.onPause()
    }

    override fun onLocationChanged(location: Location) {
        coords.text = "%.6f, %.6f".format(location.latitude, location.longitude)
        extra.text = "Accuracy ±%.0f m\nSpeed %.1f km/h".format(location.accuracy, location.speed * 3.6)
        status.text = if (location.hasAccuracy()) "Location updated" else "Location received · accuracy unavailable"
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_LOCATION) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startUpdates()
        else status.text = "Location permission denied."
    }

    companion object { private const val REQUEST_LOCATION = 1 }
}
