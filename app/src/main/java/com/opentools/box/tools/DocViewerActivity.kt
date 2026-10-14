package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.io.ByteArrayOutputStream

/** Plain-text viewer using bounded streaming reads to avoid loading huge files into memory. */
class DocViewerActivity : AppCompatActivity() {
    private lateinit var text: TextView
    private var fontSize = 15f
    private var dark = false
    private var fullText = ""
    private var findBox: android.widget.EditText? = null
    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val result = contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    val output = ByteArrayOutputStream()
                    var total = 0
                    var truncated = false
                    while (total < MAX_DISPLAY_BYTES) {
                        val limit = minOf(buffer.size, MAX_DISPLAY_BYTES - total)
                        val read = input.read(buffer, 0, limit)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        total += read
                    }
                    if (total == MAX_DISPLAY_BYTES) {
                        val probe = ByteArray(1)
                        if (input.read(probe) > 0) truncated = true
                    }
                    String(output.toByteArray(), Charsets.UTF_8) to truncated
                } ?: throw IllegalStateException("File could not be opened")
                fullText = result.first + if (result.second) "\n\n[Preview limited to 1 MB]" else ""
                text.text = highlight(fullText, findBox?.text.toString() ?: "")
                status.text = "Opened: " + (uri.lastPathSegment ?: "text file")
            } catch (_: Exception) {
                text.text = "The file could not be opened as text."
                status.text = "Unable to open file"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            text = "Choose a text file to preview its contents."
            textSize = 15f
            gravity = Gravity.TOP or Gravity.START
            UiStyle.styleText(this, 15f, false)
            setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 16f),
                UiStyle.dp(context, 16f), UiStyle.dp(context, 16f))
            setTextIsSelectable(true)
        }
        status = TextView(this).apply {
            text = "Text preview"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val open = NativeButton(this).apply { text = "Open text file" }
        findBox = android.widget.EditText(this).apply { hint = "Search in text"; UiStyle.styleInput(this); addTextChangedListener(object : android.text.TextWatcher { override fun afterTextChanged(s: android.text.Editable?) { this@DocViewerActivity.text.text = highlight(fullText, s.toString()) } override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) = Unit; override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) = Unit }) }
        val smaller = NativeButton(this, false).apply { text = "A−" }
        val bigger = NativeButton(this, false).apply { text = "A+" }
        smaller.setOnClickListener { fontSize = (fontSize - 1f).coerceAtLeast(10f); this@DocViewerActivity.text.textSize = fontSize }
        bigger.setOnClickListener { fontSize = (fontSize + 1f).coerceAtMost(28f); this@DocViewerActivity.text.textSize = fontSize }
        val theme = NativeButton(this, false).apply { text = "Light/Dark" }
        theme.setOnClickListener { dark = !dark; this@DocViewerActivity.text.setBackgroundColor(if (dark) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT); this@DocViewerActivity.text.setTextColor(if (dark) android.graphics.Color.WHITE else UiStyle.text(this)) }
        open.setOnClickListener { picker.launch(arrayOf("text/*")) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(text)
            background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(open, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(findBox!!, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL
                addView(smaller, LinearLayout.LayoutParams(0, -2, 1f)); addView(bigger, LinearLayout.LayoutParams(0, -2, 1f)); addView(theme, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiStyle.dp(context, 8f) })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Text Viewer", content))
    }

    private fun highlight(src: String, q: String): CharSequence {
        if (q.isBlank()) return src
        val s = android.text.SpannableString(src)
        var i = src.lowercase().indexOf(q.lowercase())
        while (i >= 0) { s.setSpan(android.text.style.BackgroundColorSpan(0xFFFFFF00.toInt()), i, i + q.length, 0); i = src.lowercase().indexOf(q.lowercase(), i + q.length) }
        return s
    }
    companion object { private const val MAX_DISPLAY_BYTES = 1024 * 1024 }
}
