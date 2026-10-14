package com.opentools.box

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Featured-tool cards for the home carousel. Original implementation. */
class FeaturedAdapter(
    private val tools: List<ToolEntry>,
    private val onClick: (ToolEntry) -> Unit,
) : RecyclerView.Adapter<FeaturedAdapter.Holder>() {

    class Holder(val card: MaterialCardView, val title: TextView) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // ViewPager2 requires pages to be match_parent, else it throws
        // IllegalStateException during layout.
        val card = MaterialCardView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val title = TextView(parent.context).apply {
            textSize = 26f
            val pad = ToolScaffold.pad(context)
            setPadding(pad, pad, pad, 0)
        }
        val open = MaterialButton(parent.context).apply { text = "Open" }
        card.addView(LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(open)
        })
        return Holder(card, title)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = tools[position]
        holder.title.text = entry.title
        holder.card.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount() = tools.size
}
