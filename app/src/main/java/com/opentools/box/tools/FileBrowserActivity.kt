package com.opentools.box.tools

import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lightweight file browser. Keeps real File references separate from presentation labels. */
class FileBrowserActivity : AppCompatActivity() {
    private var dir: File = Environment.getExternalStorageDirectory()
    private val entries = mutableListOf<File>()
    private val labels = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var path: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = TextView(this).apply {
            textSize = 13f
            setPadding(12, 10, 12, 10)
            setTextIsSelectable(true)
        }
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.adapter = adapter
        val up = MaterialButton(this).apply { text = "Up" }
        val home = MaterialButton(this).apply { text = "App files" }
        up.setOnClickListener { dir.parentFile?.takeIf { it.canRead() }?.let { dir = it; refresh() } }
        home.setOnClickListener { dir = filesDir; refresh() }
        list.setOnItemClickListener { _, _, position, _ ->
            val selected = entries.getOrNull(position) ?: return@setOnItemClickListener
            if (selected.isDirectory && selected.canRead()) {
                dir = selected
                refresh()
            }
        }
        if (!dir.canRead()) dir = filesDir
        refresh()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Browser", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(path)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(up)
                addView(home)
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }))
    }

    private fun refresh() {
        path.text = dir.absolutePath
        entries.clear()
        labels.clear()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val files = try {
            dir.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                .orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        for (file in files) {
            entries.add(file)
            val tag = if (file.isDirectory) "[DIR]" else "[" + humanSize(file.length()) + "]"
            labels.add(tag + "  " + file.name + "  " + fmt.format(Date(file.lastModified())))
        }
        if (entries.isEmpty()) labels.add("(empty or inaccessible)")
        adapter.notifyDataSetChanged()
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return bytes.toString() + " B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) { value /= 1024; unit++ }
        return "%.1f %s".format(Locale.US, value, units[unit])
    }
}
