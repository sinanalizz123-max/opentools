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

/** Simple file browser (app files + shared storage listing). Original implementation. */
class FileBrowserActivity : AppCompatActivity() {

    private var dir: File = Environment.getExternalStorageDirectory()
    private val names = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var path: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = TextView(this).apply { textSize = 14f }
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        list.adapter = adapter
        val up = MaterialButton(this).apply { text = "Up" }
        val home = MaterialButton(this).apply { text = "App files" }
        up.setOnClickListener { dir.parentFile?.let { dir = it; refresh() } }
        home.setOnClickListener { dir = filesDir; refresh() }
        list.setOnItemClickListener { _, _, pos, _ ->
            val f = File(dir, names[pos].substringAfter("] ").substringAfter("  "))
            if (f.isDirectory) { dir = f; refresh() }
        }
        if (!dir.canRead()) dir = filesDir
        refresh()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Browser",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(path)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(up); addView(home)
            })
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }))
    }

    private fun refresh() {
        path.text = dir.absolutePath
        names.clear()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (f in dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()) {
            val tag = if (f.isDirectory) "[DIR]" else "[${humanSize(f.length())}]"
            names.add("$tag  ${f.name}  ${fmt.format(Date(f.lastModified()))}")
        }
        if (names.isEmpty()) names.add("(empty)")
        adapter.notifyDataSetChanged()
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var v = bytes / 1024.0
        var u = 0
        while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
        return "%.1f %s".format(v, units[u])
    }
}
