package com.opentools.box.tools

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale

/** Speech-to-text via system recognizer. Original implementation. */
class SpeechToTextActivity : AppCompatActivity() {

    private lateinit var output: TextView

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            output.text = matches?.firstOrNull() ?: "(nothing recognized)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { textSize = 20f; minLines = 4 }
        val listen = MaterialButton(this).apply { text = "Listen" }
        listen.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
            }
            try {
                launcher.launch(intent)
            } catch (_: Exception) {
                output.text = "No speech recognizer on this device"
            }
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Speech to Text",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(listen); addView(output)
        }))
    }
}
