package com.opentools.box.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** QR generator with a native layout, input validation, and readable output. */
class QrGenerateActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var image: ImageView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = EditText(this).apply {
            hint = "Enter text or a URL"
            minLines = 2
            maxLines = 4
            UiStyle.styleInput(this)
        }
        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Generated QR code"
            setPadding(UiStyle.dp(context, 8f), UiStyle.dp(context, 8f),
                UiStyle.dp(context, 8f), UiStyle.dp(context, 8f))
            background = UiStyle.rounded(context, Color.WHITE, 16f, UiStyle.border(context))
        }
        status = TextView(this).apply {
            text = "Your QR code will appear here."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(0, UiStyle.dp(context, 10f), 0, UiStyle.dp(context, 10f))
        }
        val go = Button(this).apply { text = "Generate QR code"; UiStyle.styleButton(this) }
        go.setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isEmpty()) {
                input.error = "Enter text or a URL"
                status.text = "Add some text before generating a QR code."
                return@setOnClickListener
            }
            try {
                image.setImageBitmap(makeQr(value, 768))
                status.text = "QR code generated. Keep the code visible to scan it."
            } catch (_: Exception) {
                image.setImageDrawable(null)
                status.text = "Could not generate this QR code. Try shorter text."
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Create a QR code"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Turn a link or text into a scannable code."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(go, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(image, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = UiStyle.dp(context, 8f)
            })
        }
        setContentView(ToolScaffold.wrap(this, "QR Generator", content))
    }

    private fun makeQr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return bitmap
    }
}
