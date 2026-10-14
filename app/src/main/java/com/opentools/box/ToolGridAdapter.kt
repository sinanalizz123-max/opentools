package com.opentools.box

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ToolGridAdapter(
    private var tools: List<ToolEntry>,
    private val onClick: (ToolEntry) -> Unit,
) : RecyclerView.Adapter<ToolGridAdapter.Holder>() {

    class Holder(view: View, val label: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = parent.context.resources.displayMetrics.density
        val pad = (8 * density).toInt()
        val label = TextView(parent.context).apply {
            // Two tidy centered lines max; never split mid-word across 3 lines.
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            textSize = 13f
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val cell = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(label)
        }
        return Holder(cell, label)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = tools[position]
        holder.label.text = entry.title
        holder.itemView.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount() = tools.size

    fun update(next: List<ToolEntry>) {
        tools = next
        notifyDataSetChanged()
    }
}
