package com.opentools.box.tools

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider

/** Full-screen color light (white + hues) with brightness boost. Original implementation. */
class ScreenLightActivity : AppCompatActivity() {

    private var hue = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = View(this).apply { setBackgroundColor(Color.WHITE) }
        var white = true
        val hueSlider = Slider(this).apply {
            valueFrom = 0f; valueTo = 360f; value = 0f
            addOnChangeListener { _, v, _ ->
                hue = v
                white = false
                screen.setBackgroundColor(Color.HSVToColor(floatArrayOf(hue, 0.6f, 1f)))
            }
        }
        val whiteBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "White"
        }
        whiteBtn.setOnClickListener {
            white = true
            screen.setBackgroundColor(Color.WHITE)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        var uiVisible = true
        screen.setOnClickListener {
            uiVisible = !uiVisible
            hueSlider.visibility = if (uiVisible) View.VISIBLE else View.GONE
            whiteBtn.visibility = if (uiVisible) View.VISIBLE else View.GONE
        }
        setContentView(android.widget.FrameLayout(this).apply {
            addView(screen, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(hueSlider); addView(whiteBtn)
            }, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
            })
        })
    }
}
