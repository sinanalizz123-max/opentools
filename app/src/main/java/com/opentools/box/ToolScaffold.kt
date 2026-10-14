package com.opentools.box

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * Shared M3 Expressive screen chrome: toolbar with back + title,
 * content below. Original implementation.
 */
object ToolScaffold {
    fun wrap(activity: AppCompatActivity, title: String, content: View): View {
        val bar = MaterialToolbar(activity).apply {
            this.title = title
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { activity.finish() }
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    fun pad(context: Context): Int =
        (16 * context.resources.displayMetrics.density).toInt()
}
