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

/** GPS speedometer (km/h + mph). Original implementation. */
class SpeedometerActivity : AppCompatActivity(), LocationListener {

    private lateinit var manager: LocationManager
    private lateinit var speedView: TextView
    private var useMph = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        speedView = TextView(this).apply { textSize = 72f; gravity = Gravity.CENTER; text = "--" }
        val unit = TextView(this).apply { textSize = 24f; gravity = Gravity.CENTER; text = "km/h" }
        unit.setOnClickListener {
            useMph = !useMph
            unit.text = if (useMph) "mph" else "km/h"
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "GPS Speedometer",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(speedView); addView(unit)
        }))
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0f, this)
        } catch (_: SecurityException) { }
    }

    override fun onPause() {
        manager.removeUpdates(this)
        super.onPause()
    }

    override fun onLocationChanged(l: Location) {
        val kmh = l.speed * 3.6
        speedView.text = if (useMph) "%.0f".format(kmh * 0.621371) else "%.0f".format(kmh)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) onResume()
        else finish()
    }
}
