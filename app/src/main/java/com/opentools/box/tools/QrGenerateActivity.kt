package com.opentools.box.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** QR code generator. Original implementation (ZXing core). */
class QrGenerateActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private lateinit var image: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = EditText(this).apply { hint = "Text or URL"; minLines = 2 }
        image = ImageView(this)
        val go = MaterialButton(this).apply { text = "Generate" }
        go.setOnClickListener {
            val text = input.text.toString()
            if (text.isNotEmpty()) image.setImageBitmap(makeQr(text, 768))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(input); addView(go); addView(image)
        })
    }

    private fun makeQr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return bmp
    }
}
