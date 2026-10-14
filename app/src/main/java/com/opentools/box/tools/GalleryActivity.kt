package com.opentools.box.tools

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.io.File

/** Local image viewer with on-device folder browser and system picker fallback. */
class GalleryActivity : AppCompatActivity() {
    private lateinit var image: ImageView
    private lateinit var status: TextView
    private lateinit var browserStatus: TextView
    private lateinit var browserList: LinearLayout
    private lateinit var caption: TextView
    private lateinit var backToFolders: NativeButton
    private lateinit var retryPerm: NativeButton
    private var permRequested = false
    private var folders: Map<String, List<Pair<String, Uri>>> = emptyMap()
    private var openFolder: String? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { retryPerm.visibility = android.view.View.GONE; loadBrowser() } else {
            retryPerm.visibility = android.view.View.VISIBLE
            if (!shouldShowRequestPermissionRationale(neededPermission())) {
                retryPerm.text = "Open Settings"
            }
            browserStatus.text = "Image access is needed to browse on-device images. Grant it to load automatically."
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)))
        } catch (_: Exception) { }
    }

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
        backToFolders = NativeButton(this, false).apply {
            text = "← Back to folders"
            visibility = android.view.View.GONE
            setOnClickListener { openFolder = null; renderBrowser() }
        }
        browserStatus = TextView(this).apply {
            text = "On-device images"
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
        }
        browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        retryPerm = NativeButton(this, false).apply {
            text = "Grant image access"
            visibility = android.view.View.GONE
            setOnClickListener { if (retryPerm.text == "Open Settings") openAppSettings() else { permRequested = false; ensureBrowser() } }
        }
        val browserScroll = ScrollView(this).apply {
            addView(browserList, LinearLayout.LayoutParams(-1, -2))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(this@GalleryActivity, 16f)
            setPadding(pad, pad, pad, pad)
            addView(browserStatus, LinearLayout.LayoutParams(-1, -2))
            addView(backToFolders, LinearLayout.LayoutParams(-1, -2))
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
        ensureBrowser()
    }

    private fun ensureBrowser() {
        if (hasMediaAccess()) {
            retryPerm.visibility = android.view.View.GONE
            loadBrowser()
        } else if (!permRequested) {
            browserStatus.text = "Loading on-device images…"
            permRequested = true
            retryPerm.text = "Grant image access"
            try { permLauncher.launch(neededPermission()) } catch (_: Exception) {
                browserStatus.text = "Image access is needed to browse on-device images. Grant it to load automatically."
                retryPerm.visibility = android.view.View.VISIBLE
            }
        } else {
            browserStatus.text = "Image access is needed to browse on-device images. Grant it to load automatically."
            retryPerm.visibility = android.view.View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        try { ensureBrowser() } catch (_: Exception) { }
    }

    private fun loadBrowser() {
        browserStatus.text = "Loading on-device images…"
        browserList.removeAllViews()
        openFolder = null
        Thread {
            val grouped = linkedMapOf<String, MutableList<Pair<String, Uri>>>()
            try {
                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val proj = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )
                contentResolver.query(uri, proj, null, null, MediaStore.Images.Media.DISPLAY_NAME + " ASC")?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA)
                    val bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "Image"
                        val data = if (dataCol >= 0) c.getString(dataCol) else null
                        val bucket = if (bucketCol >= 0) c.getString(bucketCol) else null
                        val folder = data?.let { try { File(it).parentFile?.name } catch (_: Exception) { null } }
                            ?: bucket ?: "Device images"
                        grouped.getOrPut(folder) { mutableListOf() }
                            .add(name to ContentUris.withAppendedId(uri, id))
                    }
                }
            } catch (_: Exception) { }
            folders = grouped
            runOnUiThread { renderBrowser() }
        }.start()
    }

    private fun renderBrowser() {
        browserList.removeAllViews()
        val current = openFolder
        if (current == null) {
            backToFolders.visibility = android.view.View.GONE
            browserStatus.text = if (folders.isEmpty()) "No on-device images found."
                else "On-device images — choose a folder"
            retryPerm.visibility = if (folders.isEmpty()) android.view.View.VISIBLE else retryPerm.visibility
            folders.forEach { (folder, files) ->
                browserList.addView(NativeButton(this, false).apply {
                    text = "$folder (${files.size})"
                    setOnClickListener { openFolder = folder; renderBrowser() }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(this@GalleryActivity, 6f)
                })
            }
        } else {
            backToFolders.visibility = android.view.View.VISIBLE
            browserStatus.text = current
            browserList.addView(NativeButton(this, false).apply {
                text = "← All folders"
                setOnClickListener { openFolder = null; renderBrowser() }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@GalleryActivity, 6f)
            })
            folders[current]?.forEach { (name, contentUri) ->
                browserList.addView(NativeButton(this, false).apply {
                    text = name
                    setOnClickListener { show(contentUri, name) }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(this@GalleryActivity, 6f)
                })
            }
        }
    }
}
