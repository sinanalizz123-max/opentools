package com.opentools.box.tools
import android.os.Bundle
class LocationShareActivity : GpsHubActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { intent.putExtra("tab", 3); super.onCreate(savedInstanceState) }
}
