package com.opentools.box.tools

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity

/** White-screen light with brightness boost. Original implementation. */
class WhiteScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = View(this).apply { setBackgroundColor(Color.WHITE) }
        val seek = SeekBar(this).apply {
            max = 100
            progress = 100
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    window.attributes = window.attributes.apply {
                        screenBrightness = (p.coerceAtLeast(1)) / 100f
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        view.setOnClickListener { seek.visibility = if (seek.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "White Screen",android.widget.FrameLayout(this).apply {
            addView(view, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
            addView(seek, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
            })
        }))
    }
}
