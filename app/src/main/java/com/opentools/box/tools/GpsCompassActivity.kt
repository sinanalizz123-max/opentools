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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** GPS compass: relative bearing and distance to a target coordinate. */
class GpsCompassActivity : AppCompatActivity(), SensorEventListener, LocationListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var azimuth = 0.0
    private var current: Location? = null
    private lateinit var bearingView: TextView
    private lateinit var distView: TextView
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        bearingView = TextView(this).apply {
            text = "—°"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 42f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 22f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 22f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
        }
        distView = TextView(this).apply {
            text = "Enter a target coordinate"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 20f, true)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 16f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        latInput = EditText(this).apply {
            hint = "Target latitude (−90 to 90)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        lonInput = EditText(this).apply {
            hint = "Target longitude (−180 to 180)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        status = TextView(this).apply {
            text = "Allow location and keep the phone level for best results."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
            override fun afterTextChanged(s: Editable?) = Unit
        }
        latInput.addTextChangedListener(watcher)
        lonInput.addTextChangedListener(watcher)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 18f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Navigate to coordinates"; UiStyle.styleText(this, 23f, true) })
            addView(TextView(context).apply {
                text = "Enter a destination and follow the relative bearing and distance."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 16f))
            })
            addView(bearingView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(distView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(latInput, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(lonInput, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(status)
        }
        setContentView(ToolScaffold.wrap(this, "GPS Compass", content))
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run { status.text = "Rotation sensor unavailable; bearing may be inaccurate." }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this) }
            catch (_: Exception) { status.text = "GPS location is unavailable." }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotation, orientation)
        azimuth = (Math.toDegrees(orientation[0].toDouble()) + 360) % 360
        render()
    }

    override fun onLocationChanged(location: Location) {
        current = location
        render()
    }

    private fun render() {
        val lat = latInput.text.toString().trim().toDoubleOrNull()
        val lon = lonInput.text.toString().trim().toDoubleOrNull()
        val location = current
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            bearingView.text = "%.0f° · %s".format(azimuth, directionName(azimuth))
            distView.text = "Enter valid target coordinates"
            return
        }
        if (location == null) {
            distView.text = "Waiting for GPS fix…"
            return
        }
        val results = FloatArray(3)
        Location.distanceBetween(location.latitude, location.longitude, lat, lon, results)
        val relative = ((results[1] - azimuth) + 360) % 360
        bearingView.text = "%.0f° →".format(relative)
        distView.text = if (results[0] < 1000) "%.0f m to target".format(results[0])
            else "%.2f km to target".format(results[0] / 1000)
        status.text = "Target bearing %.0f° from north".format(results[1])
    }

    private fun directionName(degrees: Double): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return directions[((degrees + 22.5) / 45).toInt() % 8]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_LOCATION) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this) }
            catch (_: Exception) { status.text = "GPS location is unavailable." }
        } else status.text = "Location permission denied."
    }

    companion object { private const val REQUEST_LOCATION = 1 }
}
