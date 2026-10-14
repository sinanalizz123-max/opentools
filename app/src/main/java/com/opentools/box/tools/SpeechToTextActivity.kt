package com.opentools.box.tools

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.util.Locale

/** Speech-to-text through the system recognizer, with clear status and result feedback. */
class SpeechToTextActivity : AppCompatActivity() {
    private lateinit var output: TextView
    private lateinit var statusView: TextView

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            output.text = matches?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Nothing was recognized."
            statusView.text = "Recognition complete"
        } else {
            statusView.text = "Speech recognition was cancelled."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            text = "Your recognized words will appear here."
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            UiStyle.styleText(this, 17f, false)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 18f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
            textIsSelectable = true
        }
        statusView = TextView(this).apply {
            text = "Ready to listen"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val listen = Button(this).apply { text = "Start listening"; UiStyle.styleButton(this) }
        listen.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
            }
            try {
                statusView.text = "Listening…"
                launcher.launch(intent)
            } catch (_: Exception) {
                statusView.text = "No speech recognizer is available on this device."
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "Speech to text"; UiStyle.styleText(this, 24f, true) })
            addView(TextView(context).apply {
                text = "Speak naturally and review the recognized text below."
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 4f), 0, UiStyle.dp(context, 18f))
            })
            addView(listen, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(statusView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 14f)
            })
            addView(output, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(ToolScaffold.wrap(this, "Speech to Text", content))
    }
}
