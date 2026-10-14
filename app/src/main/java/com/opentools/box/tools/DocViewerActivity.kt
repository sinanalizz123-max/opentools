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
                    if (total == MAX_DISPLAY_BYTES && input.read() != -1) truncated = true
                    String(output.toByteArray(), Charsets.UTF_8) to truncated
                } ?: throw IllegalStateException("File could not be opened")
                text.text = result.first + if (result.second) "\n\n[Preview limited to 1 MB]" else ""
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
            textIsSelectable = true
        }
        status = TextView(this).apply {
            text = "Text preview"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val open = NativeButton(this).apply { text = "Open text file" }
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
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Text Viewer", content))
    }

    companion object { private const val MAX_DISPLAY_BYTES = 1024 * 1024 }
}
