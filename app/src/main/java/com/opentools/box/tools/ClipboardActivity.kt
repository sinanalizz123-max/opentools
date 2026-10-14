package com.opentools.box.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Clipboard helper with a local in-memory history and native controls. */
class ClipboardActivity : AppCompatActivity() {
    private val history = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var clipboard: ClipboardManager
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, history) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(this@ClipboardActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 14f, false)
                row.maxLines = 3
                row.setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                    UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
                return row
            }
        }
        list.adapter = adapter
        val input = EditText(this).apply {
            hint = "Text to copy"
            minLines = 2
            UiStyle.styleInput(this)
        }
        status = TextView(this).apply {
            text = "Tap a history item to copy it again."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 8f), 0, UiStyle.dp(context, 8f))
        }
        val copy = NativeButton(this).apply { text = "Copy text" }
        val paste = NativeButton(this, false).apply { text = "Paste current" }
        copy.setOnClickListener {
            val value = input.text.toString()
            if (value.isNotEmpty()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("note", value))
                history.add(0, value)
                if (history.size > 100) history.removeAt(history.lastIndex)
                adapter.notifyDataSetChanged()
                input.text.clear()
                status.text = "Copied to clipboard."
            } else {
                input.error = "Enter text to copy"
            }
        }
        paste.setOnClickListener {
            val item = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (item != null) {
                if (history.isEmpty() || history[0] != item) {
                    history.add(0, item)
                    if (history.size > 100) history.removeAt(history.lastIndex)
                    adapter.notifyDataSetChanged()
                }
                status.text = "Current clipboard text added to history."
            } else {
                status.text = "Clipboard is empty."
            }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val value = history.getOrNull(position) ?: return@setOnItemClickListener
            clipboard.setPrimaryClip(ClipData.newPlainText("note", value))
            status.text = "Copied history item to clipboard."
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            if (position in history.indices) {
                history.removeAt(position)
                adapter.notifyDataSetChanged()
                status.text = "History item removed."
            }
            true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Clipboard history"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Copy text, paste the current clipboard, or tap a previous item."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 12f))
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(status)
            addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = UiStyle.dp(context, 6f) })
                addView(paste, LinearLayout.LayoutParams(0, -2, 1f))
            })
        }
        setContentView(ToolScaffold.wrap(this, "Clipboard", content))
    }
}
