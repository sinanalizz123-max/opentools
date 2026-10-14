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

/** GPS speedometer with a large speed readout and selectable units. */
class SpeedometerActivity : AppCompatActivity(), LocationListener {
    private lateinit var manager: LocationManager
    private lateinit var speedView: TextView
    private lateinit var unitButton: NativeButton
    private lateinit var status: TextView
    private var useMph = false
    private var lastSpeedKmh: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        speedView = TextView(this).apply {
            text = "—"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 76f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 28f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 28f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
        }
        unitButton = NativeButton(this, false).apply { text = "Units · km/h" }
        unitButton.setOnClickListener {
            useMph = !useMph
            unitButton.text = if (useMph) "Units · mph" else "Units · km/h"
            renderSpeed()
        }
        status = TextView(this).apply {
            text = "Waiting for a GPS speed fix…"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 14f), 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Current speed"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(speedView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(unitButton, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "GPS Speedometer", content))
    }

    override fun onResume() {
        super.onResume()
        ensureLocationPermission()
    }

    private fun ensureLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            status.text = "Location permission is needed to measure speed."
            return
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this)
            status.text = "Waiting for GPS…"
        } catch (_: SecurityException) {
            status.text = "Location permission was revoked."
        } catch (_: Exception) {
            status.text = "GPS is unavailable."
        }
    }

    override fun onPause() {
        manager.removeUpdates(this)
        super.onPause()
    }

    override fun onLocationChanged(location: Location) {
        lastSpeedKmh = (location.speed * 3.6).coerceAtLeast(0.0)
        renderSpeed()
        status.text = if (location.hasAccuracy()) "GPS accuracy ±%.0f m".format(location.accuracy) else "GPS speed fix received"
    }

    private fun renderSpeed() {
        val kmh = lastSpeedKmh
        speedView.text = kmh?.let { "%.0f".format(if (useMph) it * 0.621371 else it) } ?: "—"
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_LOCATION) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) ensureLocationPermission()
        else status.text = "Location permission denied."
    }

    override fun onProviderDisabled(provider: String) { status.text = "Location provider disabled." }
    override fun onProviderEnabled(provider: String) { status.text = "Location provider enabled." }
    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 26")
    override fun onStatusChanged(provider: String?, statusCode: Int, extras: Bundle?) = Unit

    companion object { private const val REQUEST_LOCATION = 1 }
}
