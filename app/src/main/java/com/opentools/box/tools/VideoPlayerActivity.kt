package com.opentools.box.tools

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import java.io.File

/** Local video player with on-device folder browser, VideoView and system picker fallback. */
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var video: VideoView
    private lateinit var status: TextView
    private lateinit var browserStatus: TextView
    private lateinit var browserList: LinearLayout
    private lateinit var retryPerm: NativeButton
    private lateinit var backBtn: NativeButton
    private lateinit var mediaControls: MediaController
    private var permRequested = false
    private var folders: Map<String, List<Pair<String, Uri>>> = emptyMap()
    private var openFolder: String? = null


    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { retryPerm.visibility = android.view.View.GONE; loadBrowser() } else {
            retryPerm.visibility = android.view.View.VISIBLE
            if (!shouldShowRequestPermissionRationale(neededPermission())) {
                retryPerm.text = "Open Settings"
            }
            browserStatus.text = "Video access is needed to browse on-device videos. Grant it to load automatically."
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
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    /** Full access, or partial (user-selected) access on API 34+. */
    private fun hasMediaAccess(): Boolean {
        if (ContextCompat.checkSelfPermission(this, neededPermission()) == PackageManager.PERMISSION_GRANTED)
            return true
        return Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun play(uri: Uri, name: String = "Video") {
        try {
            video.stopPlayback()
            status.text = "Now playing: " + name
            video.setVideoURI(uri)
        } catch (_: Exception) {
            status.text = "Couldn't open this video."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Choose a video stored on your device."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(this@VideoPlayerActivity, 12f), UiStyle.dp(this@VideoPlayerActivity, 10f),
                UiStyle.dp(this@VideoPlayerActivity, 12f), UiStyle.dp(this@VideoPlayerActivity, 10f))
        }
        video = VideoView(this).apply {
            setOnPreparedListener {
                status.text = "Video ready"
                start()
            }
            setOnCompletionListener { status.text = "Playback finished" }
            setOnErrorListener { _, what, extra ->
                status.text = "Playback failed (error $what/$extra). Try another file."
                true
            }
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        mediaControls = MediaController(this).also { video.setMediaController(it); it.setAnchorView(video) }
        backBtn = NativeButton(this, false).apply {
            text = "← Back to folders"
            visibility = android.view.View.GONE
            setOnClickListener { openFolder = null; renderBrowser() }
        }
        browserStatus = TextView(this).apply {
            text = "On-device videos"
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
        }
        browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        retryPerm = NativeButton(this, false).apply {
            text = "Grant video access"
            visibility = android.view.View.GONE
            setOnClickListener { if (retryPerm.text == "Open Settings") openAppSettings() else { permRequested = false; ensureBrowser() } }
        }
        val browserScroll = ScrollView(this).apply {
            addView(browserList, LinearLayout.LayoutParams(-1, -2))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(this@VideoPlayerActivity, 12f)
            setPadding(pad, pad, pad, pad)
            addView(backBtn, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@VideoPlayerActivity, 6f)
            })
            addView(browserStatus, LinearLayout.LayoutParams(-1, -2))
            addView(retryPerm, LinearLayout.LayoutParams(-1, -2))
            addView(browserScroll, LinearLayout.LayoutParams(-1, UiStyle.dp(this@VideoPlayerActivity, 150f)).apply {
                topMargin = UiStyle.dp(this@VideoPlayerActivity, 6f)
                bottomMargin = UiStyle.dp(this@VideoPlayerActivity, 8f)
            })
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(video, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = UiStyle.dp(this@VideoPlayerActivity, 8f)
                bottomMargin = UiStyle.dp(this@VideoPlayerActivity, 10f)
            })
        }
        setContentView(ToolScaffold.wrap(this, "Video Player", content))
        ensureBrowser()
    }

    private fun ensureBrowser() {
        if (hasMediaAccess()) {
            retryPerm.visibility = android.view.View.GONE
            loadBrowser()
        } else if (!permRequested) {
            browserStatus.text = "Loading on-device videos…"
            permRequested = true
            retryPerm.text = "Grant video access"
            try { permLauncher.launch(neededPermission()) } catch (_: Exception) {
                browserStatus.text = "Video access is needed to browse on-device videos. Grant it to load automatically."
                retryPerm.visibility = android.view.View.VISIBLE
            }
        } else {
            browserStatus.text = "Video access is needed to browse on-device videos. Grant it to load automatically."
            retryPerm.visibility = android.view.View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        try { ensureBrowser() } catch (_: Exception) { }
    }

    private fun loadBrowser() {
        browserStatus.text = "Loading on-device videos…"
        browserList.removeAllViews()
        openFolder = null
        Thread {
            val grouped = linkedMapOf<String, MutableList<Pair<String, Uri>>>()
            try {
                val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val proj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME
                )
                contentResolver.query(uri, proj, null, null, MediaStore.Video.Media.DISPLAY_NAME + " ASC")?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = c.getColumnIndex(MediaStore.Video.Media.DATA)
                    val bucketCol = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "Video"
                        val data = if (dataCol >= 0) c.getString(dataCol) else null
                        val bucket = if (bucketCol >= 0) c.getString(bucketCol) else null
                        val folder = data?.let { try { File(it).parentFile?.name } catch (_: Exception) { null } }
                            ?: bucket ?: "Device videos"
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
        backBtn.visibility = if (current == null) android.view.View.GONE else android.view.View.VISIBLE
        if (current == null) {
            browserStatus.text = if (folders.isEmpty()) "No on-device videos found."
                else "On-device videos — choose a folder"
            retryPerm.visibility = if (folders.isEmpty()) android.view.View.VISIBLE else retryPerm.visibility
            folders.forEach { (folder, files) ->
                browserList.addView(NativeButton(this, false).apply {
                    text = "$folder (${files.size})"
                    setOnClickListener { openFolder = folder; renderBrowser() }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(this@VideoPlayerActivity, 6f)
                })
            }
        } else {
            browserStatus.text = current
            browserList.addView(NativeButton(this, false).apply {
                text = "← All folders"
                setOnClickListener { openFolder = null; renderBrowser() }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@VideoPlayerActivity, 6f)
            })
            folders[current]?.forEach { (name, contentUri) ->
                browserList.addView(NativeButton(this, false).apply {
                    text = name
                    setOnClickListener { play(contentUri, name) }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(this@VideoPlayerActivity, 6f)
                })
            }
        }
    }

    override fun onPause() {
        if (::video.isInitialized) video.pause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::video.isInitialized) video.stopPlayback()
        super.onDestroy()
    }
}
