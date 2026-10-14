package com.opentools.box.tools

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Screen light with full-brightness color surface and native hue controls. */
class ScreenLightActivity : AppCompatActivity() {
    private var hue = 0f
    private lateinit var screen: View
    private lateinit var controls: LinearLayout
    private lateinit var hueSlider: SeekBar
    private lateinit var whiteButton: NativeButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = View(this).apply { setBackgroundColor(Color.WHITE); contentDescription = "Color light surface" }
        hueSlider = SeekBar(this).apply {
            max = 360
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            contentDescription = "Light hue"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    hue = progress.toFloat()
                    screen.setBackgroundColor(Color.HSVToColor(floatArrayOf(hue, 0.6f, 1f)))
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        whiteButton = NativeButton(this).apply { text = "White light" }
        whiteButton.setOnClickListener {
            screen.setBackgroundColor(Color.WHITE)
            hueSlider.progress = 0
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
            addView(hueSlider)
            addView(whiteButton, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 6f)
            })
        }
        var controlsVisible = true
        screen.setOnClickListener {
            controlsVisible = !controlsVisible
            controls.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        }
        val frame = FrameLayout(this).apply {
            addView(screen, FrameLayout.LayoutParams(-1, -1))
            addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        setContentView(ToolScaffold.wrap(this, "Color Light", frame))
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        super.onDestroy()
    }
}
