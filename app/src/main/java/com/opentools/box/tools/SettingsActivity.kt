package com.opentools.box.tools

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Native settings screen for appearance preferences and privacy information. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = getSharedPreferences("home", MODE_PRIVATE).getString("theme_mode", "system") ?: "system"
        val currentLabel = TextView(this).apply {
            text = "Current appearance: " + when (current) {
                "light" -> "Light"
                "dark" -> "Dark"
                else -> "Follow system"
            }
            UiStyle.styleText(this, 14f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 14f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val light = NativeButton(this, current == "light").apply { text = "Light theme" }
        val dark = NativeButton(this, current == "dark").apply { text = "Dark theme" }
        val system = NativeButton(this, current == "system").apply { text = "Follow system" }
        light.setOnClickListener { saveTheme("light", AppCompatDelegate.MODE_NIGHT_NO) }
        dark.setOnClickListener { saveTheme("dark", AppCompatDelegate.MODE_NIGHT_YES) }
        system.setOnClickListener { saveTheme("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Appearance"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Choose a theme for every OpenTools screen. Your choice is stored on this device."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 16f))
            })
            addView(currentLabel, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            listOf(light, dark, system).forEach { button ->
                addView(button, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(context, 8f)
                })
            }
            addView(TextView(context).apply {
                text = "Privacy"
                UiStyle.styleText(this, 20f, true)
                setPadding(0, UiStyle.dp(context, 20f), 0, UiStyle.dp(context, 10f))
            })
            addView(TextView(context).apply {
                text = "OpenTools is designed without ads or trackers. Vault files are encrypted on this device. LAN sharing and camera streaming require peer access tokens. Features that need hardware or permissions may be unavailable on some devices."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 16f),
                    UiStyle.dp(context, 16f), UiStyle.dp(context, 16f))
                background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            })
        }
        setContentView(ToolScaffold.wrap(this, "Settings", content))
    }

    private fun saveTheme(value: String, mode: Int) {
        getSharedPreferences("home", MODE_PRIVATE).edit().putString("theme_mode", value).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
