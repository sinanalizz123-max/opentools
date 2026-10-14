package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Local image viewer using the system document picker. */
class GalleryActivity : AppCompatActivity() {
    private lateinit var image: ImageView
    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                image.setImageURI(uri)
                status.text = "Image opened"
            } catch (_: Exception) {
                image.setImageDrawable(null)
                status.text = "Couldn't open this image."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Selected image"
        }
        status = TextView(this).apply {
            text = "Choose an image stored on your device."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 10f))
        }
        val pick = NativeButton(this).apply { text = "Open image" }
        pick.setOnClickListener { picker.launch(arrayOf("image/*")) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(image, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = UiStyle.dp(context, 8f)
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(pick, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Gallery", content))
    }
}
