package com.opentools.box

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Lightweight visual system built on ordinary Android views, not Material components. */
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
        view.typeface = Typeface.create(
            if (bold) "sans-serif-medium" else "sans-serif",
            Typeface.NORMAL
        )
    }

    /** Consistent native button with an Android ripple and no Material dependency. */
    fun styleButton(view: Button, primary: Boolean = true) {
        val context = view.context
        view.isAllCaps = false
        view.minHeight = dp(context, 48f)
        view.minimumHeight = dp(context, 48f)
        view.setPadding(dp(context, 16f), dp(context, 10f), dp(context, 16f), dp(context, 10f))
        view.textSize = 14f
        view.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        view.setTextColor(if (primary) Color.WHITE else text(context))
        val fill = if (primary) accent(context) else surface(context)
        view.background = RippleDrawable(
            ColorStateList.valueOf(if (primary) 0x33FFFFFF else 0x22000000),
            rounded(context, fill, 14f, if (primary) null else border(context)),
            null
        )
        view.backgroundTintList = null
    }

    /** Native edit field styling shared by tool screens. */
    fun styleInput(view: EditText) {
        val context = view.context
        view.setTextColor(text(context))
        view.setHintTextColor(muted(context))
        view.textSize = 16f
        view.setPadding(dp(context, 14f), dp(context, 12f), dp(context, 14f), dp(context, 12f))
        view.background = rounded(context, surface(context), 12f, border(context))
    }

    fun clickable(view: View) {
        view.isClickable = true
        view.isFocusable = true
        view.background = rounded(view.context, surface(view.context), 14f, border(view.context))
        view.setPadding(dp(view.context, 12f), dp(view.context, 10f), dp(view.context, 12f), dp(view.context, 10f))
    }
}
