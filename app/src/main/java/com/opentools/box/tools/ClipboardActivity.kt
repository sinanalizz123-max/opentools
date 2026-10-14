package com.opentools.box.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Clipboard manager: view history, copy new text, paste current. Original implementation. */
class ClipboardActivity : AppCompatActivity() {

    private val history = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var clipboard: ClipboardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, history)
        list.adapter = adapter
        val input = EditText(this).apply { hint = "Text to copy" }
        val copy = MaterialButton(this).apply { text = "Copy" }
        val paste = MaterialButton(this).apply { text = "Paste current" }
        copy.setOnClickListener {
            val t = input.text.toString()
            if (t.isNotEmpty()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("note", t))
                history.add(0, t)
                adapter.notifyDataSetChanged()
                input.text.clear()
            }
        }
        paste.setOnClickListener {
            val item = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (item != null && (history.isEmpty() || history[0] != item)) {
                history.add(0, item)
                adapter.notifyDataSetChanged()
            }
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            clipboard.setPrimaryClip(ClipData.newPlainText("note", history[pos]))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(input)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(copy); addView(paste)
            })
        })
    }
}
