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
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.UiStyle
import java.io.File

/** Finger-paint notepad with save/clear. Original implementation. */
class PaintActivity : AppCompatActivity() {

    private lateinit var canvas: PaintView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canvas = PaintView(this)
        val black = NativeButton(this, false).apply { text = "Black" }
        val red = NativeButton(this).apply { text = "Red" }
        val blue = NativeButton(this).apply { text = "Blue" }
        val clear = NativeButton(this).apply { text = "Clear" }
        val save = NativeButton(this).apply { text = "Save" }
        val thin = NativeButton(this).apply { text = "Thin" }
        val thick = NativeButton(this).apply { text = "Thick" }
        black.setOnClickListener { canvas.color = Color.BLACK; canvas.announceForAccessibility("Black selected") }
        red.setOnClickListener { canvas.color = Color.RED; canvas.announceForAccessibility("Red selected") }
        blue.setOnClickListener { canvas.color = Color.BLUE; canvas.announceForAccessibility("Blue selected") }
        clear.setOnClickListener { canvas.clear() }
        thin.setOnClickListener { canvas.strokeWidth = 5f }
        thick.setOnClickListener { canvas.strokeWidth = 22f }
        val green = NativeButton(this, false).apply { text = "Green" }
        val undo = NativeButton(this, false).apply { text = "Undo" }
        green.setOnClickListener { canvas.color = android.graphics.Color.GREEN }
        undo.setOnClickListener { canvas.undo() }
        val sizeBar = android.widget.SeekBar(this).apply { max = 40; progress = 10 }
        sizeBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: android.widget.SeekBar?, p: Int, u: Boolean) { canvas.strokeWidth = (p + 2).toFloat() }
            override fun onStartTrackingTouch(b: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(b: android.widget.SeekBar?) = Unit
        })
        save.setOnClickListener {
            val file = File(filesDir, "note-${System.currentTimeMillis()}.png")
            var msg = if (canvas.saveTo(file)) "Drawing saved privately" else "Couldn't save drawing"
            try { val v = android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name); put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png"); put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenTools") }; val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v); if (uri != null) { contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }; msg += " + gallery" } } catch (_: Exception) {}
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Paint Notes", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "Draw with your finger"
                UiStyle.styleText(this, 20f, true)
                setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 10f), UiStyle.dp(context, 16f), UiStyle.dp(context, 10f))
            })
            addView(android.widget.TextView(context).apply { text = "Brush size"; UiStyle.styleText(this, 13f, true, UiStyle.muted(context)) })
            addView(sizeBar, LinearLayout.LayoutParams(-1, -2))
            addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    listOf(black, red, blue, green, thin, thick, undo, clear, save).forEach { button ->
                        addView(button, LinearLayout.LayoutParams(-2, -2).apply {
                            marginEnd = UiStyle.dp(context, 6f)
                        })
                    }
                })
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

        fun clear() { paths.clear(); undoStack.clear(); invalidate() }
        private val undoStack = mutableListOf<Pair<Path, Paint>>()
        fun undo() { if (paths.isNotEmpty()) { undoStack.add(paths.removeAt(paths.lastIndex)); invalidate() } }

        fun saveTo(file: File): Boolean {
            if (width <= 0 || height <= 0) return false
            return try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                draw(Canvas(bitmap))
                file.outputStream().use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw java.io.IOException("PNG encoding failed")
                }
                bitmap.recycle()
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
