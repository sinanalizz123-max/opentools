package com.opentools.box

import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Native featured-tool cards retained for compatibility with the optional carousel. */
class FeaturedAdapter(
    private val tools: List<ToolEntry>,
    private val onClick: (ToolEntry) -> Unit,
) : RecyclerView.Adapter<FeaturedAdapter.Holder>() {

    class Holder(val card: LinearLayout, val title: TextView, val open: Button) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val title = TextView(context).apply {
            UiStyle.styleText(this, 24f, true)
            setPadding(ToolScaffold.pad(context), ToolScaffold.pad(context), ToolScaffold.pad(context), 0)
        }
        val open = Button(context).apply { UiStyle.styleButton(this) }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 18f, UiStyle.border(context))
            addView(title, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(open, LinearLayout.LayoutParams(-1, -2))
        }
        return Holder(card, title, open)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = tools[position]
        holder.title.text = entry.title
        holder.open.text = "Open"
        holder.card.setOnClickListener { onClick(entry) }
        holder.open.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount() = tools.size
}
