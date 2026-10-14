package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** About screen. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "OpenTools"
                UiStyle.styleText(this, 28f, true)
            })
            addView(TextView(context).apply {
                text = "An open-source everyday utility toolbox for Android."
                UiStyle.styleText(this, 16f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 6f), 0, UiStyle.dp(context, 18f))
            })
            addView(ToolScaffold.sectionTitle(context, "Project principles"))
            addView(TextView(context).apply {
                text = "Original implementation · No ads · No trackers · Local-first tools · Minimal permissions"
                UiStyle.styleText(this, 15f)
            })
            addView(ToolScaffold.sectionTitle(context, "Version"))
            addView(TextView(context).apply {
                text = "0.3.3"
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            })
        }
        setContentView(ToolScaffold.wrap(this, "About", content))
    }
}
