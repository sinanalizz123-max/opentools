package com.opentools.box.tools

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import org.json.JSONArray

/** Checklist with local persistence, accessible rows, and explicit empty state. */
class ChecklistActivity : AppCompatActivity() {
    private data class Item(var text: String, var done: Boolean)
    private val items = mutableListOf<Item>()
    private lateinit var adapter: Adapter
    private lateinit var emptyView: TextView
    private lateinit var list: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 4f))
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        adapter = Adapter()
        list.adapter = adapter
        emptyView = TextView(this).apply {
            text = "Your checklist is empty. Add an item below to get started."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 15f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 28f), UiStyle.dp(context, 28f),
                UiStyle.dp(context, 28f), UiStyle.dp(context, 28f))
        }
        val input = EditText(this).apply {
            hint = "Add a checklist item"
            UiStyle.styleInput(this)
            setSingleLine(true)
        }
        val add = NativeButton(this).apply { text = "Add" }
        add.setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isNotEmpty()) {
                val index = items.size
                items.add(Item(value, false))
                input.text.clear()
                save()
                adapter.notifyItemInserted(index)
                updateEmptyState()
            } else {
                input.error = "Enter an item first"
            }
        }
        input.setOnEditorActionListener { _, _, _ ->
            add.performClick()
            true
        }
        val entryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = UiStyle.dp(context, 8f)
            })
            addView(add, LinearLayout.LayoutParams(-2, -2))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Your checklist"
                UiStyle.styleText(this, 24f, true)
            })
            addView(TextView(context).apply {
                text = "Tap an item to mark it done. Long-press an item to delete it."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 14f))
            })
            addView(emptyView, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL
                addView(NativeButton(context, false).apply { text = "Check all"; setOnClickListener { items.forEach { it.done = true }; save(); adapter.notifyDataSetChanged() } }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(NativeButton(context, false).apply { text = "Clear done"; setOnClickListener { items.removeAll { it.done }; save(); adapter.notifyDataSetChanged(); updateEmptyState() } }, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(entryRow, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 10f)
            })
        }
        updateEmptyState()
        setContentView(ToolScaffold.wrap(this, "Checklist", content))
    }

    private fun updateEmptyState() {
        if (!::emptyView.isInitialized || !::list.isInitialized) return
        val empty = items.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        list.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun prefs() = getSharedPreferences("checklist", Context.MODE_PRIVATE)

    private fun load() {
        try {
            val array = JSONArray(prefs().getString("items", "[]"))
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                items.add(Item(item.getString("t"), item.getBoolean("d")))
            }
        } catch (_: Exception) { }
    }

    private fun renameItem(index: Int) {
        if (index !in items.indices) return
        val edit = EditText(this).apply { setText(items[index].text); UiStyle.styleInput(this) }
        android.app.AlertDialog.Builder(this).setTitle("Rename item").setView(edit)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> val v = edit.text.toString().trim(); if (v.isNotEmpty()) { items[index].text = v; save(); adapter.notifyItemChanged(index) } }.show()
    }
    private fun save() {
        val array = JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().put("t", item.text).put("d", item.done))
        }
        prefs().edit().putString("items", array.toString()).apply()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val box: CheckBox = view.findViewById(android.R.id.checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val box = CheckBox(parent.context).apply {
                id = android.R.id.checkbox
                UiStyle.styleText(this, 16f, false)
                setPadding(UiStyle.dp(context, 10f), UiStyle.dp(context, 12f),
                    UiStyle.dp(context, 10f), UiStyle.dp(context, 12f))
                background = UiStyle.rounded(context, UiStyle.surface(context), 12f, UiStyle.border(context))
                isFocusable = true
                isClickable = true
            }
            return Holder(box)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.box.setOnCheckedChangeListener(null)
            holder.box.text = item.text
            holder.box.isChecked = item.done
            holder.box.contentDescription = if (item.done) "Completed: ${item.text}" else "Not completed: ${item.text}"
            holder.box.setOnCheckedChangeListener { _, checked ->
                item.done = checked
                save()
                holder.box.contentDescription = if (checked) "Completed: ${item.text}" else "Not completed: ${item.text}"
            }
            holder.box.setOnLongClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION && index in items.indices) {
                    android.app.AlertDialog.Builder(holder.box.context).setTitle(items[index].text)
                        .setItems(arrayOf("Rename", "Delete")) { _, which ->
                            if (which == 0) renameItem(index)
                            else { items.removeAt(index); notifyItemRemoved(index); save(); updateEmptyState() }
                        }.show()
                }
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
