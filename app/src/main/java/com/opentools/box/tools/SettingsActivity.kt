package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** App info / settings hub. Original implementation. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = MaterialToolbar(this).apply { title = "Settings" }
        val info = TextView(this).apply {
            text = """
                OpenTools — open-source utility box.
                No ads. No trackers. Minimal permissions.
                
                Version 0.1.0
            """.trimIndent()
            textSize = 16f
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Settings",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(info)
        }))
    }
}
