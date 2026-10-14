package com.opentools.box.tools

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.UiStyle
import java.io.File

/** Audio file player (on-device folder browser + MediaPlayer). */
class AudioPlayerActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var playing = false
    private lateinit var nameView: TextView
    private lateinit var seek: SeekBar
    private lateinit var toggle: NativeButton
    private lateinit var browserStatus: TextView
    private lateinit var browserList: LinearLayout
    private lateinit var retryPerm: NativeButton
    private var permRequested = false
    private var folders: Map<String, List<Pair<String, Uri>>> = emptyMap()
    private var openFolder: String? = null

    private lateinit var backBtn: NativeButton
    private val progress = object : Runnable {
        override fun run() {
            player?.let {
                try { seek.progress = it.currentPosition } catch (_: Exception) { }
                if (::seek.isInitialized) seek.postDelayed(this, 500)
            }
        }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { retryPerm.visibility = android.view.View.GONE; loadBrowser() } else {
            browserStatus.text = "Audio access is needed to browse on-device audio. Grant it to load automatically."
            retryPerm.visibility = android.view.View.VISIBLE
            if (!shouldShowRequestPermissionRationale(neededPermission())) {
                retryPerm.text = "Open Settings"
            }
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
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasMediaAccess(): Boolean {
        return ContextCompat.checkSelfPermission(this, neededPermission()) == PackageManager.PERMISSION_GRANTED
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nameView = TextView(this).apply {
            text = "Choose an audio file to begin."
            UiStyle.styleText(this, 18f, true)
            setPadding(UiStyle.dp(this@AudioPlayerActivity, 18f), UiStyle.dp(this@AudioPlayerActivity, 18f),
                UiStyle.dp(this@AudioPlayerActivity, 18f), UiStyle.dp(this@AudioPlayerActivity, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        seek = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) try { player?.seekTo(p) } catch (_: Exception) { }
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
        toggle = NativeButton(this).apply { text = "Play"; isEnabled = false }
        toggle.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (playing) { p.pause(); toggle.text = "Play" }
            else { p.start(); toggle.text = "Pause" }
            playing = !playing
        }
        browserStatus = TextView(this).apply {
            text = "On-device audio"
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
            setPadding(0, 0, 0, UiStyle.dp(this@AudioPlayerActivity, 8f))
        }
        backBtn = NativeButton(this, false).apply {
            text = "← Back to folders"
            visibility = android.view.View.GONE
            setOnClickListener {
                openFolder = null
                renderBrowser()
            }
        }
        browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        retryPerm = NativeButton(this, false).apply {
            text = "Grant audio access"
            visibility = android.view.View.GONE
            setOnClickListener { if (retryPerm.text == "Open Settings") openAppSettings() else { permRequested = false; ensureBrowser() } }
        }
        val browserScroll = ScrollView(this).apply {
            addView(browserList, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Audio Player", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(this@AudioPlayerActivity, 20f)
            setPadding(pad, pad, pad, pad)
            addView(backBtn, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 8f)
            })
            addView(browserStatus, LinearLayout.LayoutParams(-1, -2))
            addView(browserScroll, LinearLayout.LayoutParams(-1, UiStyle.dp(this@AudioPlayerActivity, 180f)).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 16f)
            })
            addView(retryPerm, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 16f)
            })
            addView(TextView(context).apply {
                text = "Now playing"
                UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(this@AudioPlayerActivity, 8f))
            })
            addView(nameView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 16f)
            })
            addView(seek, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 12f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2))
        }))
        ensureBrowser()
    }

    private fun ensureBrowser() {
        if (hasMediaAccess()) {
            retryPerm.visibility = android.view.View.GONE
            loadBrowser()
        } else {
            browserStatus.text = "Loading on-device audio…"
            if (!permRequested) {
                permRequested = true
                try { permLauncher.launch(neededPermission()) } catch (_: Exception) {
                    browserStatus.text = "Audio access is needed to browse on-device audio. Grant it to load automatically."
                    retryPerm.visibility = android.view.View.VISIBLE
                }
            } else {
                browserStatus.text = "Audio access is needed to browse on-device audio. Grant it to load automatically."
                retryPerm.visibility = android.view.View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try { ensureBrowser() } catch (_: Exception) { }
    }

    private fun loadBrowser() {
        browserStatus.text = "Loading on-device audio…"
        browserList.removeAllViews()
        openFolder = null
        Thread {
            val grouped = linkedMapOf<String, MutableList<Pair<String, Uri>>>()
            try {
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val proj = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
                )
                contentResolver.query(uri, proj, null, null, MediaStore.Audio.Media.DISPLAY_NAME + " ASC")?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val bucketCol = c.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "Audio"
                        val data = if (dataCol >= 0) c.getString(dataCol) else null
                        val bucket = if (bucketCol >= 0) c.getString(bucketCol) else null
                        val folder = data?.let { try { File(it).parentFile?.name } catch (_: Exception) { null } }
                            ?: bucket ?: "Device audio"
                        val contentUri = ContentUris.withAppendedId(uri, id)
                        grouped.getOrPut(folder) { mutableListOf() }.add(name to contentUri)
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
            backBtn.visibility = android.view.View.GONE
            browserStatus.text = if (folders.isEmpty()) "No on-device audio found."
                else "On-device audio — choose a folder"
            retryPerm.visibility = if (folders.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            folders.forEach { (folder, files) ->
                browserList.addView(NativeButton(this, false).apply {
                    text = "$folder (${files.size})"
                    setOnClickListener {
                        openFolder = folder
                        renderBrowser()
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 6f)
                })
            }
        } else {
            backBtn.visibility = android.view.View.VISIBLE
            browserStatus.text = current
            browserList.addView(NativeButton(this, false).apply {
                text = "← All folders"
                setOnClickListener {
                    openFolder = null
                    renderBrowser()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 6f)
            })
            folders[current]?.forEach { (name, contentUri) ->
                browserList.addView(NativeButton(this, false).apply {
                    text = name
                    setOnClickListener { open(contentUri, name) }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 6f)
                })
            }
        }
    }

    private fun open(uri: Uri, displayName: String? = null) {
        if (::seek.isInitialized) seek.removeCallbacks(progress)
        try { player?.release() } catch (_: Exception) { }
        player = null
        playing = false
        toggle.isEnabled = false
        toggle.text = "Loading…"
        nameView.text = "Now playing: " + (displayName ?: uri.lastPathSegment ?: "Audio")
        try {
            player = MediaPlayer().apply {
                setDataSource(this@AudioPlayerActivity, uri)
                setOnPreparedListener { prepared ->
                    seek.max = prepared.duration.coerceAtLeast(0)
                    seek.progress = 0
                    toggle.isEnabled = true
                    prepared.start()
                    playing = true
                    toggle.text = "Pause"
                    if (::seek.isInitialized) seek.post(progress)
                }
                setOnCompletionListener {
                    playing = false
                    toggle.text = "Play"
                    seek.progress = seek.max
                }
                setOnErrorListener { _, _, _ ->
                    playing = false
                    toggle.isEnabled = false
                    toggle.text = "Play"
                    nameView.text = "Unable to play this audio file."
                    true
                }
                prepareAsync()
            }
        } catch (error: Exception) {
            player?.release()
            player = null
            toggle.isEnabled = false
            toggle.text = "Play"
            nameView.text = "Unable to open audio: " + (error.message ?: "unsupported format")
        }
    }

    override fun onPause() {
        player?.pause()
        playing = false
        try { toggle.text = "Play" } catch (_: Exception) { }
        super.onPause()
    }

    override fun onDestroy() {
        try { if (::seek.isInitialized) seek.removeCallbacks(progress) } catch (_: Exception) { }
        player?.release()
        super.onDestroy()
    }
}
