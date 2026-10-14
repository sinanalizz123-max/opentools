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

/** GPS location: coordinates, accuracy, speed. Original implementation. */
class GpsActivity : AppCompatActivity(), LocationListener {

    private lateinit var manager: LocationManager
    private lateinit var coords: TextView
    private lateinit var extra: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        coords = TextView(this).apply { textSize = 24f; gravity = Gravity.CENTER }
        extra = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(coords); addView(extra)
        })
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            return
        }
        startUpdates()
    }

    private fun startUpdates() {
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1f, this)
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
        } catch (_: SecurityException) { }
    }

    override fun onPause() {
        manager.removeUpdates(this)
        super.onPause()
    }

    override fun onLocationChanged(l: Location) {
        coords.text = "%.6f, %.6f".format(l.latitude, l.longitude)
        val kmh = l.speed * 3.6
        extra.text = "±%.0f m   %.1f km/h".format(l.accuracy, kmh)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startUpdates()
        else finish()
    }
}
