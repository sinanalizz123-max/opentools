package com.opentools.box.tools
import android.os.Bundle
class GpsCompassActivity : GpsHubActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { intent.putExtra("tab", 2); super.onCreate(savedInstanceState) }
}
