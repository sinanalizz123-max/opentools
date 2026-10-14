package com.opentools.box.tools
import android.os.Bundle
class GpsActivity : GpsHubActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { intent.putExtra("tab", 0); super.onCreate(savedInstanceState) }
}
