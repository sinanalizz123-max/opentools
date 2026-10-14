package com.opentools.box.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/** On-screen ruler (cm + inch) with touch calibration offset. Original implementation. */
class RulerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(RulerView(this))
    }

    private class RulerView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 36f
            strokeWidth = 3f
        }
        private var xOffset = 0f

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                xOffset = event.x % (resources.displayMetrics.xdpi / 2.54f)
                invalidate()
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pxPerCm = resources.displayMetrics.xdpi / 2.54f
            val pxPerInch = resources.displayMetrics.xdpi
            var cm = 0
            var x = -xOffset
            while (x < width) {
                val mm = ((x + xOffset) / pxPerCm * 10).toInt()
                val len = when {
                    mm % 10 == 0 -> 120f
                    mm % 5 == 0 -> 80f
                    else -> 45f
                }
                canvas.drawLine(x, 0f, x, len, paint)
                if (mm % 10 == 0) canvas.drawText("$cm", x + 8f, 170f, paint)
                if (mm % 10 == 0) cm++
                x += pxPerCm / 10
            }
            var inch = 0
            var xi = width - 120f
            while (xi > 0) {
                val frac = ((width - 120f - xi) / pxPerInch * 16).toInt()
                val len = when {
                    frac % 16 == 0 -> 120f
                    frac % 8 == 0 -> 80f
                    frac % 4 == 0 -> 60f
                    else -> 40f
                }
                canvas.drawLine(xi, height.toFloat(), xi, height - len, paint)
                if (frac % 16 == 0) {
                    canvas.drawText("${inch}\"", xi - 20f, height - 170f, paint)
                    inch++
                }
                xi -= pxPerInch / 16
            }
            canvas.drawText("Tap/drag top edge to calibrate zero", 24f, height - 40f, paint)
        }
    }
}
