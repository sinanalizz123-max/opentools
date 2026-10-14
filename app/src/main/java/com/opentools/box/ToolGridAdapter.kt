package com.opentools.box

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Compact native-view tool tiles with a consistent icon, label, and touch target. */
class ToolGridAdapter(
    private var tools: List<ToolEntry>,
    private val onClick: (ToolEntry) -> Unit,
) : RecyclerView.Adapter<ToolGridAdapter.Holder>() {

    class Holder(view: View, val icon: TextView, val label: TextView, val category: TextView) :
        RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        fun size(v: Int) = UiStyle.dp(context, v.toFloat())
        val icon = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 25f
            background = UiStyle.rounded(context, UiStyle.raised(context), 13f)
            layoutParams = LinearLayout.LayoutParams(size(48), size(48))
        }
        val label = TextView(context).apply {
            UiStyle.styleText(this, 13f, true)
            maxLines = 2
            gravity = Gravity.START
        }
        val category = TextView(context).apply {
            UiStyle.styleText(this, 11f, false, UiStyle.muted(context))
            maxLines = 1
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(size(13), size(14), size(10), size(13))
            addView(icon)
            addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = size(12) })
            addView(category, LinearLayout.LayoutParams(-1, -2).apply { topMargin = size(3) })
        }
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                setMargins(size(4), size(4), size(4), size(4))
            }
            background = UiStyle.rounded(context, UiStyle.surface(context), 17f, UiStyle.border(context))
            isClickable = true
            isFocusable = true
            addView(content)
        }
        return Holder(tile, icon, label, category)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = tools[position]
        holder.icon.text = glyph(entry)
        holder.label.text = entry.title
        holder.category.text = entry.category.label
        holder.itemView.contentDescription = entry.title
        holder.itemView.setOnClickListener { onClick(entry) }
    }

    private fun glyph(entry: ToolEntry): String = when (entry.id) {
        "torch" -> "☼"
        "calculator" -> "＋"
        "compass", "gpscompass" -> "◎"
        "qrgenerate" -> "▦"
        "recorder", "audioply", "vidply", "mike" -> "♫"
        "files", "filetrans", "vault" -> "▤"
        "wifiscan", "wifiscanip", "wifispeed", "sigstr" -> "⌁"
        "camera", "shutter", "cctvserver", "cctvviewer", "vcall" -> "◉"
        "stopwatch", "timer" -> "◷"
        "bluetooth", "aurdino" -> "ᛒ"
        else -> when (entry.category) {
            ToolCategory.WIFI -> "⌁"
            ToolCategory.CAMERA -> "◉"
            ToolCategory.HEALTH -> "♡"
            ToolCategory.MEASURE -> "⌖"
            ToolCategory.EDITORS -> "▤"
            ToolCategory.AUDIO -> "♫"
            ToolCategory.EXTRA -> "✳"
            ToolCategory.BASIC -> "✦"
        }
    }

    override fun getItemCount() = tools.size

    fun update(next: List<ToolEntry>) {
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize() = tools.size
                override fun getNewListSize() = next.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                    tools[oldPos].id == next[newPos].id
                override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                    tools[oldPos] == next[newPos]
            })
        tools = next
        diff.dispatchUpdatesTo(this)
    }
}
