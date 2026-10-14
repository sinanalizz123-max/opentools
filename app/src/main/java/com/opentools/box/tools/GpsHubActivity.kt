package com.opentools.box.tools
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
open class GpsHubActivity : AppCompatActivity(), LocationListener {
    protected var tab = 0
    private lateinit var pages: FrameLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var locView: TextView
    private lateinit var speedView: TextView
    private lateinit var compView: TextView
    private lateinit var shareView: TextView
    private lateinit var tripView: TextView
    private lateinit var threshInput: EditText
    private var last: Location? = null
    private var tripDist = 0f; private var tripStart = 0L; private var tripping = false
    private var tone: ToneGenerator? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = intent.getIntExtra("tab", 0)
        locView = TextView(this).apply { text = "Waiting for GPS…"; gravity = Gravity.CENTER; UiStyle.styleText(this, 24f, true) }
        speedView = TextView(this).apply { text = "— km/h"; gravity = Gravity.CENTER; UiStyle.styleText(this, 56f, true) }
        compView = TextView(this).apply { text = "—°"; gravity = Gravity.CENTER; UiStyle.styleText(this, 48f, true) }
        shareView = TextView(this).apply { text = "—"; gravity = Gravity.CENTER; UiStyle.styleText(this, 22f, true) }
        tripView = TextView(this).apply { gravity = Gravity.CENTER; UiStyle.styleText(this, 14f, false, UiStyle.muted(context)) }
        threshInput = EditText(this).apply { hint = "Overspeed km/h (e.g. 80)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; UiStyle.styleInput(this) }
        val startStop = NativeButton(this).apply { text = "Start trip" }
        startStop.setOnClickListener {
            if (!tripping) { tripping = true; tripDist = 0f; tripStart = System.currentTimeMillis(); last = null; startStop.text = "Stop trip" }
            else { tripping = false; startStop.text = "Start trip"; saveTrip() }
        }
        val shareBtn = NativeButton(this, false).apply { text = "Share location" }
        shareBtn.setOnClickListener { val l = last; if (l != null) { val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "https://maps.google.com/?q=${l.latitude},${l.longitude}") }; startActivity(android.content.Intent.createChooser(i, "Share")) } }
        fun page(v: android.view.View): ScrollView = ScrollView(this).apply { addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24); addView(v); addView(tripView); addView(threshInput); addView(startStop); addView(shareBtn); addView(TextView(context).apply { text = tripLog(); UiStyle.styleText(this, 13f, false, UiStyle.muted(context)) }) }) }
        val p0 = page(locView); val p1 = page(speedView); val p2 = page(compView); val p3 = page(shareView)
        pages = FrameLayout(this)
        tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = arrayOf("Location","Speed","Compass","Share")
        names.forEachIndexed { i, n -> val b = NativeButton(this, i != tab).apply { text = n }; b.setOnClickListener { tab = i; showTab(p0,p1,p2,p3); refreshTabs() }; tabRow.addView(b, LinearLayout.LayoutParams(0,-2,1f)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(tabRow); addView(pages, LinearLayout.LayoutParams(-1,0,1f)) }
        showTab(p0,p1,p2,p3)
        setContentView(ToolScaffold.wrap(this, "GPS Hub", root))
    }
    private fun refreshTabs() { for (i in 0 until tabRow.childCount) { val b = tabRow.getChildAt(i) as Button; UiStyle.styleButton(b, i == tab) } }
    private fun showTab(vararg ps: ScrollView) { pages.removeAllViews(); pages.addView(ps[tab.coerceIn(0,3)]) }
    override fun onResume() { super.onResume(); try { val m = getSystemService(Context.LOCATION_SERVICE) as LocationManager; if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1); return }; m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this) } catch (_: Exception) { } }
    override fun onPause() { try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (_: Exception) { }; try { tone?.release() } catch (_: Exception) { }; tone = null; super.onPause() }
    override fun onLocationChanged(l: Location) {
        val prev = last; last = l
        if (tripping && prev != null) tripDist += prev.distanceTo(l)
        val kmh = l.speed * 3.6
        locView.text = "%.6f, %.6f\n±%.0fm".format(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 0f)
        speedView.text = "%.0f km/h".format(kmh)
        compView.text = if (l.hasBearing()) "%.0f°".format(l.bearing) else "—°"
        shareView.text = "%.6f, %.6f".format(l.latitude, l.longitude)
        val mins = if (tripping) (System.currentTimeMillis() - tripStart) / 60000.0 else 0.0
        tripView.text = "Trip: %.2f km  %.1f min".format(tripDist / 1000, mins)
        val th = threshInput.text.toString().toDoubleOrNull()
        if (th != null && kmh > th) { try { if (tone == null) tone = ToneGenerator(AudioManager.STREAM_ALARM, 80); tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300) } catch (_: Exception) { }; try { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(300) } catch (_: Exception) { } }
    }
    private fun tripLog(): String = getPreferences(Context.MODE_PRIVATE).getStringSet("trips", emptySet())?.joinToString("\n") ?: ""
    private fun saveTrip() { try { val p = getPreferences(Context.MODE_PRIVATE); val s = p.getStringSet("trips", mutableSetOf())?.toMutableSet() ?: mutableSetOf(); s.add("%.2f km on %s".format(tripDist/1000, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()))); p.edit().putStringSet("trips", s).apply() } catch (_: Exception) { } }
    override fun onProviderEnabled(p: String) = Unit
    override fun onProviderDisabled(p: String) = Unit
}
