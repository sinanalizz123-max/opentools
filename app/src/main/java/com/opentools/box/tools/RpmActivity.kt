package com.opentools.box.tools

import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Tap-tempo RPM meter. Original implementation. */
class RpmActivity : AppCompatActivity() {

    private val taps = ArrayDeque<Long>()
    private lateinit var valueView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        valueView = TextView(this).apply { textSize = 56f; gravity = Gravity.CENTER; text = "Tap!" }
        val tap = MaterialButton(this).apply { text = "TAP" }
        val reset = MaterialButton(this).apply { text = "Reset" }
        tap.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            taps.addLast(now)
            while (taps.size > 8) taps.removeFirst()
            if (taps.size >= 2) {
                val spanMin = (taps.last() - taps.first()) / 60000.0
                val rpm = (taps.size - 1) / spanMin
                valueView.text = "%.0f RPM".format(rpm)
            }
        }
        reset.setOnClickListener { taps.clear(); valueView.text = "Tap!" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(valueView); addView(tap); addView(reset)
        })
    }
}
