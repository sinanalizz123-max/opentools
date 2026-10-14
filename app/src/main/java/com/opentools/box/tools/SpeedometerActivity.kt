package com.opentools.box.tools
import android.os.Bundle
class SpeedometerActivity : GpsHubActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { intent.putExtra("tab", 1); super.onCreate(savedInstanceState) }
}
