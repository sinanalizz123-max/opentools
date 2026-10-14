package com.opentools.box.tools

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Bright white screen with adjustable display brightness. */
class WhiteScreenActivity : AppCompatActivity() {
    private lateinit var brightnessLabel: TextView
    private lateinit var brightnessSeek: SeekBar
    private lateinit var controls: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = View(this).apply {
            setBackgroundColor(Color.WHITE)
            contentDescription = "White light surface. Tap to show or hide brightness controls."
        }
        brightnessLabel = TextView(this).apply {
            text = "Brightness · 100%"
            UiStyle.styleText(this, 14f, true)
            setTextColor(UiStyle.text(context))
            setPadding(0, 0, 0, UiStyle.dp(context, 4f))
        }
        brightnessSeek = SeekBar(this).apply {
            max = 100
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            contentDescription = "Display brightness"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val brightness = progress.coerceAtLeast(1) / 100f
                    window.attributes = window.attributes.apply { screenBrightness = brightness }
                    brightnessLabel.text = "Brightness · ${progress.coerceAtLeast(1)}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 14f)
            setPadding(pad, pad, pad, pad)
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
            addView(brightnessLabel)
            addView(brightnessSeek)
        }
        var visible = true
        surface.setOnClickListener {
            visible = !visible
            controls.visibility = if (visible) View.VISIBLE else View.GONE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        val frame = FrameLayout(this).apply {
            addView(surface, FrameLayout.LayoutParams(-1, -1))
            addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                val margin = UiStyle.dp(context, 10f)
                setMargins(margin, 0, margin, margin)
            })
        }
        setContentView(ToolScaffold.wrap(this, "White Screen", frame))
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        super.onDestroy()
    }
}
