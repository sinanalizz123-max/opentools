package com.opentools.box.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.UiStyle

/** Audio file player (on-device folder browser + MediaPlayer). */
class AudioPlayerActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var playing = false
    private lateinit var nameView: TextView
    private lateinit var seek: SeekBar
    private lateinit var toggle: NativeButton
    private lateinit var browser: MediaBrowserHelper

    private val progress = object : Runnable {
        override fun run() {
            player?.let {
                try { seek.progress = it.currentPosition } catch (_: Exception) { }
                if (::seek.isInitialized) seek.postDelayed(this, 500)
            }
        }
    }

    private fun neededPermission(): String = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasMediaAccess(): Boolean =
        ContextCompat.checkSelfPermission(this, neededPermission()) == PackageManager.PERMISSION_GRANTED

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
        val browserStatus = TextView(this).apply {
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
            setPadding(0, 0, 0, UiStyle.dp(this@AudioPlayerActivity, 8f))
        }
        val browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val retryPerm = NativeButton(this, false)
        val backBtn = NativeButton(this, false)
        browser = MediaBrowserHelper(
            this, "audio", neededPermission(), { hasMediaAccess() },
            browserStatus, retryPerm, backBtn, browserList
        ) { uri, name -> open(uri, name) }
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
        browser.ensureBrowser()
    }

    override fun onResume() {
        super.onResume()
        try { browser.ensureBrowser() } catch (_: Exception) { }
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
