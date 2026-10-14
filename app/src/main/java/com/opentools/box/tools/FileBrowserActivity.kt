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
import com.opentools.box.UiStyle
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
    private var sortMode = "Name"
    private var query = ""
    private fun getSort(): String = getSharedPreferences("filebrowser", MODE_PRIVATE).getString("filebrowser_sort", "Name") ?: "Name"
    private fun setSort(v: String) { getSharedPreferences("filebrowser", MODE_PRIVATE).edit().putString("filebrowser_sort", v).apply(); sortMode = v }
    private lateinit var search: android.widget.EditText
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var path: TextView

    private val treePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) openTree(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localDir = filesDir
        path = TextView(this).apply {
            maxLines = 2
            setTextIsSelectable(true)
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(UiStyle.border(context))
            dividerHeight = UiStyle.dp(context, 1f)
            setBackgroundColor(UiStyle.background(context))
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val row = (convertView as? TextView) ?: TextView(this@FileBrowserActivity)
                row.text = getItem(position)
                UiStyle.styleText(row, 14f, false)
                row.maxLines = 2
                row.setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 12f),
                    UiStyle.dp(context, 14f), UiStyle.dp(context, 12f))
                return row
            }
        }
        list.adapter = adapter
        val up = NativeButton(this, false).apply { text = "Up" }
        val home = NativeButton(this).apply { text = "App files" }
        val choose = NativeButton(this).apply { text = "Choose shared folder" }
        up.setOnClickListener { navigateUp() }
        home.setOnClickListener {
            usingTree = false
            treeStack.clear()
            localDir = filesDir
            refresh()
        }
        choose.setOnClickListener { treePicker.launch(null) }
        sortMode = getSort()
        search = android.widget.EditText(this).apply { hint = "Search files"; UiStyle.styleInput(this); addTextChangedListener(object : android.text.TextWatcher { override fun afterTextChanged(s: android.text.Editable?) { query = s.toString(); refresh() } override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) = Unit; override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) = Unit }) }
        val sortRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL
            listOf("Name", "Date", "Size").forEach { m -> addView(NativeButton(this@FileBrowserActivity, false).apply { text = m; setOnClickListener { setSort(m); refresh() } }, LinearLayout.LayoutParams(0, -2, 1f)) }
        }
        refresh()
        list.setOnItemLongClickListener { _, _, pos, _ -> if (usingTree) confirmDocAction(pos) else confirmLocalAction(pos); true }
        list.setOnItemClickListener { _, _, position, _ ->
            if (usingTree) {
                val sel = documentEntries.getOrNull(position) ?: return@setOnItemClickListener
                if (sel.isDirectory) { treeStack.add(sel); refresh() } else openDoc(sel)
            } else {
                val sel = localEntries.getOrNull(position) ?: return@setOnItemClickListener
                if (sel.isDirectory && sel.canRead()) { localDir = sel; refresh() } else openLocal(sel)
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "File Browser", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Browse files"
                UiStyle.styleText(this, 23f, true)
                setPadding(0, 0, 0, UiStyle.dp(context, 12f))
            })
            addView(path, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(up, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = UiStyle.dp(context, 6f)
                })
                addView(home, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(sortRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(choose, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
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
            val q = query.lowercase()
            for (item in children.filter { q.isBlank() || (it.name ?: "").lowercase().contains(q) }.let { list ->
                when (sortMode) { "Date" -> list.sortedByDescending { it.lastModified() }; "Size" -> list.sortedByDescending { it.length() }; else -> list.sortedBy { (it.name ?: "").lowercase() } }
            }) {
                documentEntries.add(item)
                val kind = if (item.isDirectory) "📁[DIR]" else "[" + humanSize(item.length()) + "]"
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
            val q2 = query.lowercase()
            for (item in children.filter { q2.isBlank() || it.name.lowercase().contains(q2) }.let { list ->
                when (sortMode) { "Date" -> list.sortedByDescending { it.lastModified() }; "Size" -> list.sortedByDescending { it.length() }; else -> list.sortedBy { it.name.lowercase() } }
            }) {
                localEntries.add(item)
                val kind = if (item.isDirectory) "📁[DIR]" else "[" + humanSize(item.length()) + "]"
                labels.add(kind + "  " + item.name + "  " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.lastModified())))
            }
        }
        if (labels.isEmpty()) labels.add("(empty or inaccessible)")
        adapter.notifyDataSetChanged()
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png","jpg","jpeg","gif","webp" -> "image/*"; "pdf" -> "application/pdf"; "txt","md","log" -> "text/plain"; "mp3","wav","ogg" -> "audio/*"; "mp4","mkv","webm" -> "video/*"; "html","htm" -> "text/html"; else -> "*/*"
    }
    private fun openLocal(f: File) {
        try { val uri = androidx.core.content.FileProvider.getUriForFile(this, packageName + ".fileprovider", f); val i = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(f.name)).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(android.content.Intent.createChooser(i, "Open with")) }
        catch (_: Exception) { path.text = "No app can open this file." }
    }
    private fun openDoc(d: DocumentFile) {
        try { val i = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(d.uri, mimeOf(d.name ?: "")).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(android.content.Intent.createChooser(i, "Open with")) }
        catch (_: Exception) { path.text = "No app can open this file." }
    }
    private fun confirmLocalAction(pos: Int) {
        val f = localEntries.getOrNull(pos) ?: return
        val name = android.widget.EditText(this).apply { setText(f.name) }
        android.app.AlertDialog.Builder(this).setTitle(f.name).setItems(arrayOf("Rename", "Copy", "Move", "Delete")) { _, w ->
            when (w) {
                0 -> android.app.AlertDialog.Builder(this).setTitle("Rename").setView(name).setPositiveButton("Save") { _, _ -> try { f.renameTo(java.io.File(f.parent, name.text.toString())); refresh() } catch (_: Exception) {} }.setNegativeButton("Cancel", null).show()
                3 -> android.app.AlertDialog.Builder(this).setTitle("Delete?").setPositiveButton("Delete") { _, _ -> try { if (f.isDirectory) f.deleteRecursively() else f.delete(); refresh() } catch (_: Exception) {} }.setNegativeButton("Cancel", null).show()
                1, 2 -> { val dest = android.widget.EditText(this).apply { setText(f.parent) }; android.app.AlertDialog.Builder(this).setTitle(if (w == 1) "Copy to path" else "Move to path").setView(dest).setPositiveButton("Go") { _, _ -> try { val d = java.io.File(dest.text.toString(), f.name); if (w == 1) f.copyRecursively(d) else { f.copyRecursively(d); if (f.isDirectory) f.deleteRecursively() else f.delete() }; refresh() } catch (_: Exception) { path.text = "Operation failed." } }.setNegativeButton("Cancel", null).show() }
            }
        }.show()
    }
    private fun confirmDocAction(pos: Int) {
        val d = documentEntries.getOrNull(pos) ?: return
        android.app.AlertDialog.Builder(this).setTitle(d.name ?: "file").setItems(arrayOf("Rename", "Delete")) { _, w ->
            when (w) {
                0 -> { val e = android.widget.EditText(this).apply { setText(d.name ?: "") }; android.app.AlertDialog.Builder(this).setTitle("Rename").setView(e).setPositiveButton("Save") { _, _ -> try { d.renameTo(e.text.toString()); refresh() } catch (_: Exception) {} }.setNegativeButton("Cancel", null).show() }
                1 -> android.app.AlertDialog.Builder(this).setTitle("Delete?").setPositiveButton("Delete") { _, _ -> try { d.delete(); refresh() } catch (_: Exception) {} }.setNegativeButton("Cancel", null).show()
            }
        }.show()
    }
    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return bytes.toString() + " B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) { value /= 1024; unit++ }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}
