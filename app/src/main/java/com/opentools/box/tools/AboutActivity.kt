package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** About screen. Original implementation. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "About",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(MaterialToolbar(context).apply { title = "About" })
            addView(TextView(context).apply {
                text = "OpenTools\nAn open-source everyday-utility toolbox.\nClean-room implementation; no third-party SDKs."
                textSize = 16f
            })
        }))
    }
}
