package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File browser using private files and the Storage Access Framework for shared folders. */
class FileBrowserActivity : AppCompatActivity() {
    private var localDir: File? = null
    private var usingTree = false
    private val localEntries = mutableListOf<File>()
    private val documentEntries = mutableListOf<DocumentFile>()
    private val labels = mutableListOf<String>()
    private val treeStack = mutableListOf<DocumentFile>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var path: TextView

    private val treePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) openTree(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localDir = filesDir
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
        val choose = MaterialButton(this).apply { text = "Choose shared folder" }
        up.setOnClickListener { navigateUp() }
        home.setOnClickListener {
            usingTree = false
            treeStack.clear()
            localDir = filesDir
            refresh()
        }
        choose.setOnClickListener { treePicker.launch(null) }
        list.setOnItemClickListener { _, _, position, _ ->
            if (usingTree) {
                val selected = documentEntries.getOrNull(position) ?: return@setOnItemClickListener
                if (selected.isDirectory) {
                    treeStack.add(selected)
                    refresh()
                }
            } else {
                val selected = localEntries.getOrNull(position) ?: return@setOnItemClickListener
                if (selected.isDirectory && selected.canRead()) {
                    localDir = selected
                    refresh()
                }
            }
        }
        refresh()
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Browser", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(path)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(up)
                addView(home)
            })
            addView(choose)
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }))
    }

    private fun openTree(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) { }
        }
        val root = DocumentFile.fromTreeUri(this, uri)
        if (root == null || !root.isDirectory) {
            path.text = "Could not open that folder."
            return
        }
        usingTree = true
        treeStack.clear()
        treeStack.add(root)
        refresh()
    }

    private fun navigateUp() {
        if (usingTree) {
            if (treeStack.size > 1) {
                treeStack.removeAt(treeStack.lastIndex)
                refresh()
            }
        } else {
            val parent = localDir?.parentFile
            if (parent != null && parent.canRead()) {
                localDir = parent
                refresh()
            }
        }
    }

    private fun refresh() {
        labels.clear()
        localEntries.clear()
        documentEntries.clear()
        if (usingTree) {
            val current = treeStack.lastOrNull()
            path.text = "Shared folder: " + (treeStack.mapNotNull { it.name }.joinToString(" / ").ifBlank { current?.uri?.lastPathSegment ?: "Selected folder" })
            val children = try {
                current?.listFiles()?.sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase(Locale.ROOT).orEmpty() })).orEmpty()
            } catch (_: SecurityException) {
                emptyList()
            }
            for (item in children) {
                documentEntries.add(item)
                val kind = if (item.isDirectory) "[DIR]" else "[" + humanSize(item.length()) + "]"
                labels.add(kind + "  " + (item.name ?: "Unnamed"))
            }
        } else {
            val current = localDir ?: filesDir
            path.text = current.absolutePath
            val children = try {
                current.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })).orEmpty()
            } catch (_: SecurityException) {
                emptyList()
            }
            for (item in children) {
                localEntries.add(item)
                val kind = if (item.isDirectory) "[DIR]" else "[" + humanSize(item.length()) + "]"
                labels.add(kind + "  " + item.name + "  " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.lastModified())))
            }
        }
        if (labels.isEmpty()) labels.add("(empty or inaccessible)")
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
