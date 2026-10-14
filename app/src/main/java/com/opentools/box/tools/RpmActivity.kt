package com.opentools.box.tools

import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Tap-tempo RPM meter with a large reading and robust timing calculation. */
class RpmActivity : AppCompatActivity() {
    private val taps = ArrayDeque<Long>()
    private lateinit var valueView: TextView
    private lateinit var hintView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        valueView = TextView(this).apply {
            text = "Tap to start"
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 40f, true)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 28f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 28f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 20f, UiStyle.border(context))
        }
        hintView = TextView(this).apply {
            text = "Tap steadily several times to estimate revolutions per minute."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
        }
        val tap = NativeButton(this).apply { text = "TAP" }
        val reset = NativeButton(this, false).apply { text = "Reset" }
        tap.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            taps.addLast(now)
            while (taps.size > 8) taps.removeFirst()
            if (taps.size >= 2) {
                val spanMs = taps.last() - taps.first()
                if (spanMs > 0L) {
                    val rpm = 60000.0 * (taps.size - 1) / spanMs
                    valueView.text = "%.1f RPM".format(rpm)
                    hintView.text = "Based on ${taps.size} taps. Keep tapping for a steadier estimate."
                }
            } else {
                valueView.text = "Keep tapping…"
                hintView.text = "One more tap is needed to calculate RPM."
            }
        }
        reset.setOnClickListener {
            taps.clear()
            valueView.text = "Tap to start"
            hintView.text = "Tap steadily several times to estimate revolutions per minute."
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = UiStyle.dp(context, 24f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Tap tempo"
                gravity = Gravity.CENTER
                UiStyle.styleText(this, 24f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 18f))
            })
            addView(valueView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(hintView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 24f)
            })
            addView(tap, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(reset, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Tap RPM", content))
    }
}
