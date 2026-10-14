package com.opentools.box.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** PDF viewer (system picker + PdfRenderer). Original implementation. */
class PdfViewerActivity : AppCompatActivity() {

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var page = 0
    private var pageCount = 0
    private lateinit var image: ImageView
    private lateinit var label: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { open(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        image = ImageView(this).apply { adjustViewBounds = true }
        label = TextView(this).apply { textSize = 16f }
        val pick = MaterialButton(this).apply { text = "Open PDF" }
        val prev = MaterialButton(this).apply { text = "‹" }
        val next = MaterialButton(this).apply { text = "›" }
        pick.setOnClickListener { picker.launch(arrayOf("application/pdf")) }
        prev.setOnClickListener { if (page > 0) { page--; render() } }
        next.setOnClickListener { if (page < pageCount - 1) { page++; render() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(image, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(label)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(pick); addView(prev); addView(next)
            })
        })
    }

    private fun open(uri: Uri) {
        close()
        pfd = contentResolver.openFileDescriptor(uri, "r") ?: return
        renderer = PdfRenderer(pfd!!)
        pageCount = renderer!!.pageCount
        page = 0
        render()
    }

    private fun render() {
        val r = renderer ?: return
        r.openPage(page).use { pg ->
            val scale = 2f
            val bmp = Bitmap.createBitmap(
                (pg.width * scale).toInt(), (pg.height * scale).toInt(),
                Bitmap.Config.ARGB_8888)
            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.setImageBitmap(bmp)
        }
        label.text = "Page ${page + 1} / $pageCount"
    }

    private fun close() {
        renderer?.close(); renderer = null
        pfd?.close(); pfd = null
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }
}
