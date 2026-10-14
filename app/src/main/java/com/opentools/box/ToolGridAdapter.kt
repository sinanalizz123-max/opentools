package com.opentools.box

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ToolGridAdapter(
    private var tools: List<ToolEntry>,
    private val onClick: (ToolEntry) -> Unit,
) : RecyclerView.Adapter<ToolGridAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return Holder(v)
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
