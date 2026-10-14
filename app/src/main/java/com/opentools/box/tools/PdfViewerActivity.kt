package com.opentools.box.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** PDF viewer using Android PdfRenderer with native navigation controls. */
class PdfViewerActivity : AppCompatActivity() {
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var page = 0
    private var pageCount = 0
    private var currentBitmap: Bitmap? = null
    private lateinit var image: ImageView
    private lateinit var label: TextView
    private lateinit var previous: NativeButton
    private lateinit var next: NativeButton

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { open(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "PDF page preview"
        }
        label = TextView(this).apply {
            text = "Choose a PDF to view."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 8f), UiStyle.dp(context, 8f),
                UiStyle.dp(context, 8f), UiStyle.dp(context, 8f))
        }
        val pick = NativeButton(this).apply { text = "Open PDF" }
        previous = NativeButton(this, false).apply { text = "Previous" ; isEnabled = false }
        next = NativeButton(this, false).apply { text = "Next" ; isEnabled = false }
        pick.setOnClickListener { picker.launch(arrayOf("application/pdf")) }
        previous.setOnClickListener { if (page > 0) { page--; render() } }
        next.setOnClickListener { if (page < pageCount - 1) { page++; render() } }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(pick, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(previous, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = UiStyle.dp(context, 6f)
                })
                addView(next, LinearLayout.LayoutParams(0, -2, 1f))
            })
        }
        setContentView(ToolScaffold.wrap(this, "PDF Viewer", content))
    }

    private fun open(uri: Uri) {
        close()
        try {
            pfd = contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Could not open this PDF")
            renderer = PdfRenderer(pfd!!)
            pageCount = renderer!!.pageCount
            page = 0
            if (pageCount <= 0) throw IllegalStateException("This PDF has no pages")
            render()
        } catch (error: Exception) {
            close()
            label.text = "Unable to open PDF: ${error.message ?: "invalid file"}"
            previous.isEnabled = false
            next.isEnabled = false
        }
    }

    private fun render() {
        val pdf = renderer ?: return
        try {
            pdf.openPage(page).use { pdfPage ->
                val width = (resources.displayMetrics.widthPixels - UiStyle.dp(this, 32f)).coerceAtLeast(1)
                val scale = (width.toFloat() / pdfPage.width).coerceAtLeast(1f).coerceAtMost(3f)
                val bitmap = Bitmap.createBitmap(
                    (pdfPage.width * scale).toInt().coerceAtLeast(1),
                    (pdfPage.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                image.setImageBitmap(bitmap)
                currentBitmap?.recycle()
                currentBitmap = bitmap
            }
            label.text = "Page ${page + 1} of $pageCount"
            previous.isEnabled = page > 0
            next.isEnabled = page < pageCount - 1
        } catch (error: Exception) {
            label.text = "Could not render page: ${error.message ?: "render error"}"
        }
    }

    private fun close() {
        image.setImageDrawable(null)
        currentBitmap?.recycle()
        currentBitmap = null
        renderer?.close()
        renderer = null
        pfd?.close()
        pfd = null
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }
}
