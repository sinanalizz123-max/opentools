package com.opentools.box

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/** Lightweight visual system built on ordinary Android views, not Material 3 components. */
object UiStyle {
    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun background(context: Context) = if (isDark(context)) Color.rgb(18, 21, 27) else Color.rgb(246, 247, 249)
    fun surface(context: Context) = if (isDark(context)) Color.rgb(29, 33, 41) else Color.WHITE
    fun raised(context: Context) = if (isDark(context)) Color.rgb(38, 43, 53) else Color.rgb(238, 242, 247)
    fun text(context: Context) = if (isDark(context)) Color.rgb(239, 242, 247) else Color.rgb(25, 31, 42)
    fun muted(context: Context) = if (isDark(context)) Color.rgb(160, 170, 185) else Color.rgb(105, 115, 130)
    fun accent(context: Context) = if (isDark(context)) Color.rgb(130, 177, 255) else Color.rgb(38, 91, 180)
    fun border(context: Context) = if (isDark(context)) Color.rgb(57, 64, 77) else Color.rgb(224, 229, 236)

    fun rounded(context: Context, color: Int, radiusDp: Float = 16f, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(context, 1f), strokeColor)
        }

    fun styleText(view: TextView, size: Float, bold: Boolean = false, color: Int = text(view.context)) {
        view.setTextColor(color)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        view.typeface = if (bold) android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }

    fun clickable(view: View) {
        view.isClickable = true
        view.isFocusable = true
        view.background = rounded(view.context, surface(view.context), 14f, border(view.context))
        view.setPadding(dp(view.context, 12f), dp(view.context, 10f), dp(view.context, 12f), dp(view.context, 10f))
    }
}
