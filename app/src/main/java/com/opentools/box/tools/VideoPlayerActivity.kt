package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Local video player with on-device folder browser, VideoView and system picker fallback. */
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var video: VideoView
    private lateinit var status: TextView
    private lateinit var mediaControls: MediaController
    private lateinit var browser: MediaBrowserHelper

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
        val browserStatus = TextView(this).apply {
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
        }
        val browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val retryPerm = NativeButton(this, false)
        val backBtn = NativeButton(this, false)
        browser = MediaBrowserHelper(
            this, "video", neededPermission(), { hasMediaAccess() },
            browserStatus, retryPerm, backBtn, browserList
        ) { uri, name -> play(uri, name) }
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
        browser.ensureBrowser()
    }

    override fun onResume() {
        super.onResume()
        try { browser.ensureBrowser() } catch (_: Exception) { }
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
