package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Local image viewer with on-device folder browser and system picker fallback. */
class GalleryActivity : AppCompatActivity() {
    private lateinit var image: ImageView
    private lateinit var status: TextView
    private lateinit var caption: TextView
    private lateinit var browser: MediaBrowserHelper

    private fun neededPermission(): String = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    /** Full access, or partial (user-selected) access on API 34+. */
    private fun hasMediaAccess(): Boolean {
        if (ContextCompat.checkSelfPermission(this, neededPermission()) == PackageManager.PERMISSION_GRANTED)
            return true
        return Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun show(uri: Uri, name: String = "") {
        caption.text = name
        try {
            image.setImageURI(uri)
            status.text = "Image opened"
        } catch (_: Exception) {
            image.setImageDrawable(null)
            status.text = "Couldn't open this image."
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
            setPadding(UiStyle.dp(this@GalleryActivity, 12f), UiStyle.dp(this@GalleryActivity, 10f),
                UiStyle.dp(this@GalleryActivity, 12f), UiStyle.dp(this@GalleryActivity, 10f))
        }
        caption = TextView(this).apply {
            text = ""
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
        }
        val browserStatus = TextView(this).apply {
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
        }
        val browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val retryPerm = NativeButton(this, false)
        val backToFolders = NativeButton(this, false)
        browser = MediaBrowserHelper(
            this, "image", neededPermission(), { hasMediaAccess() },
            browserStatus, retryPerm, backToFolders, browserList
        ) { uri, name -> show(uri, name) }
        val browserScroll = ScrollView(this).apply {
            addView(browserList, LinearLayout.LayoutParams(-1, -2))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(this@GalleryActivity, 16f)
            setPadding(pad, pad, pad, pad)
            addView(backToFolders, LinearLayout.LayoutParams(-1, -2))
            addView(browserStatus, LinearLayout.LayoutParams(-1, -2))
            addView(retryPerm, LinearLayout.LayoutParams(-1, -2))
            addView(browserScroll, LinearLayout.LayoutParams(-1, UiStyle.dp(this@GalleryActivity, 150f)).apply {
                topMargin = UiStyle.dp(this@GalleryActivity, 6f)
                bottomMargin = UiStyle.dp(this@GalleryActivity, 8f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(image, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = UiStyle.dp(this@GalleryActivity, 8f)
                bottomMargin = UiStyle.dp(this@GalleryActivity, 4f)
            })
            addView(caption, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Gallery", content))
        browser.ensureBrowser()
    }

    override fun onResume() {
        super.onResume()
        try { browser.ensureBrowser() } catch (_: Exception) { }
    }
}
