package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Small settings hub for theme preference and home-screen behavior. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = TextView(this).apply {
            text = "Choose the appearance that feels right. Your preference is saved on this device."
            UiStyle.styleText(this, 14f, false, UiStyle.muted(this@SettingsActivity))
            setPadding(0, 0, 0, UiStyle.dp(context, 18f))
        }
        val light = MaterialButton(this).apply {
            text = "Light theme"
            setOnClickListener { saveTheme("light", AppCompatDelegate.MODE_NIGHT_NO) }
        }
        val dark = MaterialButton(this).apply {
            text = "Dark theme"
            setOnClickListener { saveTheme("dark", AppCompatDelegate.MODE_NIGHT_YES) }
        }
        val system = MaterialButton(this).apply {
            text = "Follow system"
            setOnClickListener { saveTheme("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(ToolScaffold.sectionTitle(context, "Appearance"))
            addView(info)
            addView(light)
            addView(dark)
            addView(system)
            addView(ToolScaffold.sectionTitle(context, "Privacy"))
            addView(TextView(context).apply {
                text = "OpenTools has no ads or trackers. The file vault encrypts imported files on this device. LAN sharing and camera streaming require a peer access token."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            })
        }
        setContentView(ToolScaffold.wrap(this, "Settings", content))
    }

    private fun saveTheme(value: String, mode: Int) {
        getSharedPreferences("home", MODE_PRIVATE).edit().putString("theme_mode", value).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
