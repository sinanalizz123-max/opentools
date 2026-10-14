package com.opentools.box.tools

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray

/** Checklist with local persistence. Original implementation. */
class ChecklistActivity : AppCompatActivity() {

    private data class Item(var text: String, var done: Boolean)

    private val items = mutableListOf<Item>()
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        val list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context) }
        adapter = Adapter()
        list.adapter = adapter
        val input = EditText(this).apply { hint = "New item" }
        val add = MaterialButton(this).apply { text = "Add" }
        add.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                items.add(Item(t, false))
                input.text.clear()
                save()
                adapter.notifyItemInserted(items.size - 1)
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Checklist",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(input, LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(add)
            })
        }))
    }

    private fun prefs() = getSharedPreferences("checklist", Context.MODE_PRIVATE)

    private fun load() {
        try {
            val arr = JSONArray(prefs().getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Item(o.getString("t"), o.getBoolean("d")))
            }
        } catch (_: Exception) { }
    }

    private fun save() {
        val arr = JSONArray()
        for (it in items) {
            arr.put(org.json.JSONObject().put("t", it.text).put("d", it.done))
        }
        prefs().edit().putString("items", arr.toString()).apply()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.H>() {
        inner class H(v: View) : RecyclerView.ViewHolder(v) {
            val box: CheckBox = v.findViewById(android.R.id.checkbox)
        }
        override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
            val box = CheckBox(p.context).apply { id = android.R.id.checkbox }
            return H(box)
        }
        override fun onBindViewHolder(h: H, pos: Int) {
            val item = items[pos]
            h.box.text = item.text
            h.box.setOnCheckedChangeListener(null)
            h.box.isChecked = item.done
            h.box.setOnCheckedChangeListener { _, checked ->
                item.done = checked
                save()
            }
            h.box.setOnLongClickListener {
                items.removeAt(h.bindingAdapterPosition)
                notifyItemRemoved(h.bindingAdapterPosition)
                save()
                true
            }
        }
        override fun getItemCount() = items.size
    }
}
