package com.opentools.box.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/** GPS compass: bearing + distance to a waypoint. Original implementation. */
class GpsCompassActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var azimuth = 0.0
    private var current: Location? = null
    private lateinit var bearingView: TextView
    private lateinit var distView: TextView
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        bearingView = TextView(this).apply { textSize = 48f }
        distView = TextView(this).apply { textSize = 24f }
        latInput = EditText(this).apply {
            hint = "Target latitude"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        lonInput = EditText(this).apply {
            hint = "Target longitude"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "GPS Compass",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(bearingView); addView(distView); addView(latInput); addView(lonInput)
        }))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
            } catch (_: SecurityException) { }
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        val rot = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        val o = FloatArray(3)
        SensorManager.getOrientation(rot, o)
        azimuth = (Math.toDegrees(o[0].toDouble()) + 360) % 360
        render()
    }

    override fun onLocationChanged(l: Location) {
        current = l
        render()
    }

    private fun render() {
        val lat = latInput.text.toString().toDoubleOrNull()
        val lon = lonInput.text.toString().toDoubleOrNull()
        val cur = current
        if (lat == null || lon == null || cur == null) {
            bearingView.text = "%.0f°".format(azimuth)
            distView.text = "Enter target + wait for GPS"
            return
        }
        val results = FloatArray(3)
        Location.distanceBetween(cur.latitude, cur.longitude, lat, lon, results)
        val rel = ((results[1] - azimuth) + 360) % 360
        bearingView.text = "%.0f° →".format(rel)
        distView.text = if (results[0] < 1000) "%.0f m".format(results[0])
            else "%.2f km".format(results[0] / 1000)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
    }
}
