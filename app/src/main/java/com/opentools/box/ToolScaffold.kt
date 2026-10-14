package com.opentools.box

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Shared, restrained native screen chrome. No Material 3 widgets. */
object ToolScaffold {
    fun wrap(activity: AppCompatActivity, title: String, content: View): View {
        activity.window.statusBarColor = UiStyle.background(activity)
        activity.window.navigationBarColor = UiStyle.background(activity)
        activity.window.decorView.systemUiVisibility = if (UiStyle.isDark(activity)) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        val bar = Toolbar(activity).apply {
            setBackgroundColor(UiStyle.background(activity))
            setTitleTextColor(UiStyle.text(activity))
            this.title = title
            navigationIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                activity, R.drawable.ic_arrow_back)
            setNavigationOnClickListener { activity.finish() }
            elevation = 0f
        }
        val body = FrameLayout(activity).apply {
            setBackgroundColor(UiStyle.background(activity))
            addView(content, FrameLayout.LayoutParams(-1, -1))
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiStyle.background(activity))
            applySystemBarInsets(this)
            addView(bar, LinearLayout.LayoutParams(-1, dp(activity, 56)))
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    fun pad(context: Context): Int = dp(context, 16)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun applySystemBarInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    fun sectionTitle(context: Context, title: String): TextView = TextView(context).apply {
        text = title
        UiStyle.styleText(this, 18f, true)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 2), dp(context, 18), dp(context, 2), dp(context, 10))
    }
}
