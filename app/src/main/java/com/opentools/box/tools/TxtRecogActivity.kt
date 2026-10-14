package com.opentools.box.tools

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** OCR on a picked image (ML Kit). Original implementation. */
class TxtRecogActivity : AppCompatActivity() {

    private lateinit var image: ImageView
    private lateinit var output: TextView
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { recognize(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        image = ImageView(this).apply { adjustViewBounds = true }
        output = TextView(this).apply { textSize = 16f }
        val pick = MaterialButton(this).apply { text = "Open image" }
        pick.setOnClickListener { picker.launch(arrayOf("image/*")) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(image, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(pick); addView(output)
        })
    }

    private fun recognize(uri: Uri) {
        image.setImageURI(uri)
        output.text = "Reading…"
        val input = InputImage.fromFilePath(this, uri)
        recognizer.process(input)
            .addOnSuccessListener { output.text = it.text.ifEmpty { "(no text found)" } }
            .addOnFailureListener { output.text = "Failed: ${it.message}" }
    }

    override fun onDestroy() {
        recognizer.close()
        super.onDestroy()
    }
}
