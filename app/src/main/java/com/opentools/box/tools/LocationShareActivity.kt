package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton

/** Location share: show + share coordinates via any app. Original implementation. */
class LocationShareActivity : AppCompatActivity(), LocationListener {

    private lateinit var manager: LocationManager
    private lateinit var coords: TextView
    private var last: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        coords = TextView(this).apply { textSize = 22f; text = "Waiting for GPS…" }
        val share = MaterialButton(this).apply { text = "Share my location" }
        share.setOnClickListener {
            val l = last ?: return@setOnClickListener
            val text = "https://maps.google.com/?q=${l.latitude},${l.longitude}"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share location"))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(coords); addView(share)
        })
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
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2f, this)
        } catch (_: SecurityException) { }
    }

    override fun onPause() {
        manager.removeUpdates(this)
        super.onPause()
    }

    override fun onLocationChanged(l: Location) {
        last = l
        coords.text = "%.6f, %.6f\n±%.0f m".format(l.latitude, l.longitude, l.accuracy)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
    }
}
