package com.opentools.box.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File

/** Finger-paint notepad with save/clear. Original implementation. */
class PaintActivity : AppCompatActivity() {

    private lateinit var canvas: PaintView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canvas = PaintView(this)
        val black = MaterialButton(this).apply { text = "Black" }
        val red = MaterialButton(this).apply { text = "Red" }
        val blue = MaterialButton(this).apply { text = "Blue" }
        val clear = MaterialButton(this).apply { text = "Clear" }
        val save = MaterialButton(this).apply { text = "Save" }
        val thin = MaterialButton(this).apply { text = "Thin" }
        val thick = MaterialButton(this).apply { text = "Thick" }
        black.setOnClickListener { canvas.color = Color.BLACK }
        red.setOnClickListener { canvas.color = Color.RED }
        blue.setOnClickListener { canvas.color = Color.BLUE }
        clear.setOnClickListener { canvas.clear() }
        thin.setOnClickListener { canvas.strokeWidth = 5f }
        thick.setOnClickListener { canvas.strokeWidth = 22f }
        save.setOnClickListener { canvas.saveTo(File(filesDir, "note-${System.currentTimeMillis()}.png")) }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Paint Notes",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(canvas, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(LinearLayout(context).apply {
                addView(black); addView(red); addView(blue); addView(thin); addView(thick); addView(clear); addView(save)
            })
        }))
    }

    class PaintView(context: Context) : View(context) {
        var color = Color.BLACK
        var strokeWidth = 10f
        private val paths = mutableListOf<Pair<Path, Paint>>()
        private var current: Path? = null

        private fun newPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@PaintView.color
            style = Paint.Style.STROKE
            strokeWidth = this@PaintView.strokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    current = Path().apply { moveTo(e.x, e.y) }
                    paths.add(current!! to newPaint())
                }
                MotionEvent.ACTION_MOVE -> current?.lineTo(e.x, e.y)
                MotionEvent.ACTION_UP -> {
                    current = null
                    performClick()
                }
            }
            invalidate()
            return true
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            c.drawColor(Color.WHITE)
            for ((p, paint) in paths) c.drawPath(p, paint)
        }

        fun clear() {
            paths.clear()
            invalidate()
        }

        fun saveTo(file: File) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            draw(Canvas(bmp))
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
