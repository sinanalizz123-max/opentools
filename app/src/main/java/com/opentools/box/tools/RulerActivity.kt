package com.opentools.box.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import kotlin.math.floor

/** On-screen centimetre/inch ruler with zero offset and display-scale calibration. */
class RulerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ruler = RulerView(this)
        val calibrationLabel = TextView(this).apply {
            text = "Display calibration · 1.00×"
            UiStyle.styleText(this, 13f, true)
        }
        val calibration = SeekBar(this).apply {
            max = 40
            progress = 20
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            contentDescription = "Ruler display calibration"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val scale = 0.8f + progress * 0.01f
                    ruler.scaleFactor = scale
                    calibrationLabel.text = "Display calibration · %.2f×".format(scale)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val zero = NativeButton(this, false).apply { text = "Reset zero point" }
        zero.setOnClickListener { ruler.resetZero() }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 14f)
            setPadding(pad, pad, pad, pad)
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            addView(TextView(context).apply {
                text = "Calibration"
                UiStyle.styleText(this, 17f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 4f))
            })
            addView(TextView(context).apply {
                text = "Adjust the scale against a physical ruler. Drag the ruler to move the zero point."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(context, 8f))
            })
            addView(calibrationLabel)
            addView(calibration)
            addView(zero, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 8f)
            })
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ruler, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(-1, -2).apply {
                val margin = UiStyle.dp(context, 10f)
                setMargins(margin, margin, margin, margin)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Ruler", content))
    }

    private class RulerView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiStyle.text(context)
            textSize = UiStyle.dp(context, 14f).toFloat()
            strokeWidth = UiStyle.dp(context, 1f).toFloat()
        }
        private var xOffset = 0f
        var scaleFactor = 1f
            set(value) { field = value.coerceIn(0.8f, 1.2f); invalidate() }

        init {
            setBackgroundColor(UiStyle.surface(context))
            isClickable = true
        }

        fun resetZero() {
            xOffset = 0f
            invalidate()
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val pxPerCm = resources.displayMetrics.xdpi / 2.54f * scaleFactor
                    xOffset = event.x % pxPerCm
                    invalidate()
                }
                MotionEvent.ACTION_UP -> performClick()
                else -> return false
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pxPerCm = resources.displayMetrics.xdpi / 2.54f * scaleFactor
            val pxPerInch = resources.displayMetrics.xdpi * scaleFactor
            val majorTick = UiStyle.dp(context, 34f).toFloat()
            val mediumTick = UiStyle.dp(context, 24f).toFloat()
            val minorTick = UiStyle.dp(context, 14f).toFloat()
            var x = -xOffset
            while (x < width) {
                val mm = floor((x + xOffset) / pxPerCm * 10).toInt()
                val len = when {
                    mm % 10 == 0 -> majorTick
                    mm % 5 == 0 -> mediumTick
                    else -> minorTick
                }
                canvas.drawLine(x, 0f, x, len, paint)
                if (mm % 10 == 0) canvas.drawText((mm / 10).toString(), x + UiStyle.dp(context, 4f), len + paint.textSize + UiStyle.dp(context, 4f), paint)
                x += pxPerCm / 10
            }
            var xi = width - xOffset
            while (xi > 0) {
                val ticksFromRight = ((width - xOffset - xi) / pxPerInch * 16).toInt()
                val len = when {
                    ticksFromRight % 16 == 0 -> majorTick
                    ticksFromRight % 8 == 0 -> mediumTick
                    ticksFromRight % 4 == 0 -> UiStyle.dp(context, 18f).toFloat()
                    else -> minorTick
                }
                canvas.drawLine(xi, height.toFloat(), xi, height - len, paint)
                if (ticksFromRight % 16 == 0) canvas.drawText("${ticksFromRight / 16}\"", xi, height - len - UiStyle.dp(context, 6f), paint)
                xi -= pxPerInch / 16
            }
            paint.color = UiStyle.muted(context)
            canvas.drawText("cm", UiStyle.dp(context, 8f).toFloat(), height / 2f, paint)
            paint.color = UiStyle.text(context)
        }
    }
}
