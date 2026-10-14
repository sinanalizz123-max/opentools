package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Help screen. Original implementation. */
class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Help",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(MaterialToolbar(context).apply { title = "Help" })
            addView(TextView(context).apply {
                text = "Tap a tool to open it.\nLong-press a checklist item to delete it.\nGrant camera/mic permissions only for the tools that need them."
                textSize = 16f
            })
        }))
    }
}
