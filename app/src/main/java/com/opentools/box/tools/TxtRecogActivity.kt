package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** On-device image OCR using ML Kit and the system image picker. */
class TxtRecogActivity : AppCompatActivity() {
    private lateinit var image: ImageView
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var pick: NativeButton
    private var ocrLang = "Latin"
    private fun copyAllText() { try { val cb = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("ocr", output.text.toString())) } catch (_: Exception) {} }
    private fun langSpinner(ctx: android.content.Context): android.widget.Spinner = android.widget.Spinner(ctx).apply { adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_item, listOf("Latin", "Chinese", "Devanagari", "Japanese", "Korean")).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }; setSelection(0); onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener { override fun onItemSelected(a: android.widget.AdapterView<*>?, v: android.view.View?, p: Int, id: Long) { ocrLang = a?.getItemAtPosition(p).toString() } override fun onNothingSelected(a: android.widget.AdapterView<*>?) {} } }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { recognize(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Selected image for text recognition"
        }
        output = TextView(this).apply {
            text = "Recognized text will appear here."
            gravity = Gravity.TOP or Gravity.START
            UiStyle.styleText(this, 15f, false)
            setPadding(UiStyle.dp(context, 14f), UiStyle.dp(context, 14f),
                UiStyle.dp(context, 14f), UiStyle.dp(context, 14f))
            setTextIsSelectable(true)
        }
        status = TextView(this).apply {
            text = "Choose an image containing text."
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        pick = NativeButton(this).apply { text = "Open image" }
        pick.setOnClickListener { picker.launch(arrayOf("image/*")) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Extract text from image"; UiStyle.styleText(this, 23f, true) })
            addView(TextView(context).apply {
                text = "Text is recognized on-device when the required model is available."
                UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 10f))
            })
            addView(pick, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(image, LinearLayout.LayoutParams(-1, UiStyle.dp(context, 180f)).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 6f)
            })
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(output)
                background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Image OCR", content))
    }

    private fun recognize(uri: Uri) {
        try {
            image.setImageURI(uri)
            output.text = ""
            status.text = "Recognizing text…"
            pick.isEnabled = false
            val input = InputImage.fromFilePath(this, uri)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    output.text = result.text.ifEmpty { "No text found in this image." }
                    status.text = if (result.text.isBlank()) "Recognition complete · no text found" else "Recognition complete"
                    pick.isEnabled = true
                }
                .addOnFailureListener { error ->
                    output.text = "Could not recognize text."
                    status.text = "Recognition failed: ${error.message ?: "unknown error"}"
                    pick.isEnabled = true
                }
        } catch (error: Exception) {
            status.text = "Could not open image: ${error.message ?: "invalid image"}"
            pick.isEnabled = true
        }
    }

    override fun onDestroy() {
        recognizer.close()
        super.onDestroy()
    }
}
