package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Plain-text document viewer. Original implementation. */
class DocViewerActivity : AppCompatActivity() {

    private lateinit var text: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { ins ->
                    // Cap at 1 MB for display.
                    val bytes = ins.readBytes().take(1024 * 1024).toByteArray()
                    text.text = String(bytes, Charsets.UTF_8)
                }
            } catch (_: Exception) {
                text.text = "Could not open file"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply { textSize = 15f }
        val open = MaterialButton(this).apply { text = "Open text file" }
        open.setOnClickListener { picker.launch(arrayOf("text/*")) }
        val scroll = ScrollView(this).apply { addView(text) }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Text Viewer",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(open)
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }))
    }
}
