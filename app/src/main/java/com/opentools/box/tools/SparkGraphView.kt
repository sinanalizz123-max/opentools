package com.opentools.box.tools
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.opentools.box.UiStyle
class SparkGraphView(context: Context) : View(context) {
    private val vals = ArrayDeque<Float>()
    private var min = 0f; private var max = 100f
    var peakHold: Boolean = true
    private var peak = Float.NEGATIVE_INFINITY
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f; style = Paint.Style.STROKE }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; style = Paint.Style.STROKE }
    init { minimumHeight = (160 * resources.displayMetrics.density + 0.5f).toInt() }
    fun push(v: Float) { vals.addLast(v); while (vals.size > 120) vals.removeFirst(); if (v > peak) peak = v; invalidate() }
    fun setRange(a: Float, b: Float) { min = a; max = b; invalidate() }
    fun clear() { vals.clear(); peak = Float.NEGATIVE_INFINITY; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        line.color = UiStyle.accent(context); peakPaint.color = UiStyle.muted(context); grid.color = UiStyle.border(context)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawLine(0f, h / 2, w, h / 2, grid)
        if (peakHold && peak != Float.NEGATIVE_INFINITY) {
            val span = ((max - min).takeIf { it != 0f } ?: 1f)
            val y = h - ((peak - min) / span).coerceIn(0f, 1f) * h
            canvas.drawLine(0f, y, w, y, peakPaint)
        }
        if (vals.size < 2) return
        val span = ((max - min).takeIf { it != 0f } ?: 1f)
        val n = vals.size; val dx = if (n > 1) w / 119f else 0f
        var x = w - dx * (n - 1); var prev: Pair<Float,Float>? = null
        for (v in vals) { val y = h - ((v - min) / span).coerceIn(0f,1f) * h
            prev?.let { canvas.drawLine(it.first, it.second, x, y, line) }; prev = x to y; x += dx }
    }
}
