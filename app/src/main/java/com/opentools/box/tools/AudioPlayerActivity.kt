package com.opentools.box.tools

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.UiStyle

/** Audio file player (on-device folder browser + background MusicPlayService). */
class AudioPlayerActivity : AppCompatActivity() {

    private var speed = 1f
    private var playing = false
    private var currentUri: Uri? = null
    private var currentName = "Audio"
    private lateinit var nameView: TextView
    private lateinit var subView: TextView
    private lateinit var elapsedView: TextView
    private lateinit var totalView: TextView
    private lateinit var sleepLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var toggle: NativeButton
    private lateinit var browser: MediaBrowserHelper
    private lateinit var browserList: LinearLayout
    private lateinit var browserScroll: ScrollView
    private var tracks: List<Track> = emptyList()
    private var trackIndex = -1
    private var sleepMinutes = 0
    private var service: MusicPlayService? = null
    private var bound = false
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        stopAll()
        sleepMinutes = 0
        try { sleepLabel.text = "Sleep: Off" } catch (_: Exception) { }
    }

    private data class Track(val uri: Uri, val name: String, val artist: String, val album: String)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            service = (b as? MusicPlayService.LocalBinder)?.service()
            bound = true
            syncFromService()
        }
        override fun onServiceDisconnected(n: ComponentName?) { service = null; bound = false }
    }

    private val progress = object : Runnable {
        override fun run() {
            val s = service
            if (s != null) {
                try {
                    val d = s.duration()
                    val p = s.position()
                    if (d > 0) {
                        seek.max = d
                        seek.progress = p
                        elapsedView.text = fmtTime(p.toLong())
                        totalView.text = fmtTime(d.toLong())
                    }
                    playing = s.isPlaying()
                    toggle.text = if (playing) "Pause" else "Play"
                } catch (_: Exception) { }
                if (::seek.isInitialized) seek.postDelayed(this, 500)
            } else if (::seek.isInitialized) seek.postDelayed(this, 500)
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
            UiStyle.styleText(this, 20f, true)
            setPadding(UiStyle.dp(this@AudioPlayerActivity, 18f), UiStyle.dp(this@AudioPlayerActivity, 18f),
                UiStyle.dp(this@AudioPlayerActivity, 18f), UiStyle.dp(this@AudioPlayerActivity, 6f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        subView = TextView(this).apply {
            text = "Unknown artist"
            UiStyle.styleText(this, 13f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(this@AudioPlayerActivity, 18f), 0,
                UiStyle.dp(this@AudioPlayerActivity, 18f), UiStyle.dp(this@AudioPlayerActivity, 18f))
            background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
        }
        elapsedView = TextView(this).apply { text = "00:00"; UiStyle.styleText(this, 12f, false, UiStyle.muted(context)) }
        totalView = TextView(this).apply { text = "00:00"; UiStyle.styleText(this, 12f, false, UiStyle.muted(context)) }
        sleepLabel = TextView(this).apply { text = "Sleep: Off"; UiStyle.styleText(this, 12f, false, UiStyle.muted(context)) }
        seek = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) service?.seekTo(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
        toggle = NativeButton(this).apply { text = "Play"; isEnabled = false }
        toggle.setOnClickListener {
            val s = service
            if (s != null && currentUri != null) {
                s.toggle()
                playing = s.isPlaying()
                toggle.text = if (playing) "Pause" else "Play"
            }
        }
        val prev = NativeButton(this, false).apply { text = "Prev" }
        prev.setOnClickListener { step(-1) }
        val next = NativeButton(this, false).apply { text = "Next" }
        next.setOnClickListener { step(1) }
        val speedBtn = NativeButton(this, false).apply { text = "1.0x" }
        speedBtn.setOnClickListener {
            speed = PlaybackSpeed.next(speed)
            speedBtn.text = "${speed}x"
            service?.setSpeed(speed)
        }
        val sleepBtn = NativeButton(this, false).apply { text = "Sleep" }
        sleepBtn.setOnClickListener {
            sleepMinutes = when (sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
            handler.removeCallbacks(sleepRunnable)
            sleepLabel.text = if (sleepMinutes == 0) "Sleep: Off" else "Sleep: ${sleepMinutes} min"
            if (sleepMinutes > 0) handler.postDelayed(sleepRunnable, sleepMinutes * 60_000L)
        }
        val eqBtn = NativeButton(this, false).apply { text = "EQ" }
        eqBtn.setOnClickListener {
            try {
                val id = service?.sessionId() ?: 0
                val i = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                }
                startActivity(i)
            } catch (_: Exception) {
                Toast.makeText(this, "No system equalizer on this device", Toast.LENGTH_SHORT).show()
            }
        }
        val stop = NativeButton(this, false).apply { text = "Stop" }
        stop.setOnClickListener { stopAll() }
        val browserStatus = TextView(this).apply {
            UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
            setPadding(0, 0, 0, UiStyle.dp(this@AudioPlayerActivity, 8f))
        }
        browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val retryPerm = NativeButton(this, false)
        val backBtn = NativeButton(this, false)
        browser = MediaBrowserHelper(
            this, "audio", neededPermission(), { hasMediaAccess() },
            browserStatus, retryPerm, backBtn, browserList
        ) { uri, name -> open(uri, name) }
        browserScroll = ScrollView(this).apply {
            addView(browserList, LinearLayout.LayoutParams(-1, -2))
        }
        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navRow.addView(prev, LinearLayout.LayoutParams(0, -2, 1f))
        navRow.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        val optRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        optRow.addView(speedBtn, LinearLayout.LayoutParams(0, -2, 1f))
        optRow.addView(sleepBtn, LinearLayout.LayoutParams(0, -2, 1f))
        optRow.addView(eqBtn, LinearLayout.LayoutParams(0, -2, 1f))
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        timeRow.addView(elapsedView, LinearLayout.LayoutParams(0, -2, 1f))
        timeRow.addView(totalView, LinearLayout.LayoutParams(-2, -2))
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
            addView(nameView, LinearLayout.LayoutParams(-1, -2))
            addView(subView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 12f)
            })
            addView(timeRow, LinearLayout.LayoutParams(-1, -2))
            addView(seek, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(this@AudioPlayerActivity, 12f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2))
            addView(navRow, LinearLayout.LayoutParams(-1, -2))
            addView(optRow, LinearLayout.LayoutParams(-1, -2))
            addView(sleepLabel, LinearLayout.LayoutParams(-1, -2))
            addView(stop, LinearLayout.LayoutParams(-1, -2))
        }))
        browser.ensureBrowser()
        loadTracks()
        seek.post(progress)
        try {
            bindService(Intent(this, MusicPlayService::class.java), conn, Context.BIND_AUTO_CREATE)
            bound = true
        } catch (_: Exception) { }
    }

    private fun loadTracks() {
        Thread {
            val out = mutableListOf<Track>()
            try {
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM
                    ), null, null, MediaStore.Audio.Media.DISPLAY_NAME + " ASC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val aCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val alCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val uri = android.content.ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val artist = if (aCol >= 0) c.getString(aCol) else null
                        val album = if (alCol >= 0) c.getString(alCol) else null
                        out.add(Track(uri, c.getString(nCol) ?: "Audio", artist ?: "Unknown artist", album ?: ""))
                    }
                }
            } catch (_: Exception) { }
            runOnUiThread { tracks = out }
        }.start()
    }

    private fun open(uri: Uri, displayName: String? = null) {
        currentUri = uri
        currentName = displayName ?: uri.lastPathSegment ?: "Audio"
        trackIndex = tracks.indexOfFirst { it.uri.toString() == uri.toString() }
        nameView.text = currentName
        val meta = tracks.firstOrNull { it.uri.toString() == uri.toString() }
        subView.text = if (meta != null && meta.album.isNotEmpty()) "${meta.artist} — ${meta.album}"
        else meta?.artist ?: "Unknown artist"
        toggle.isEnabled = true
        toggle.text = "Pause"
        playing = true
        MusicPlayService.startPlay(this, uri, currentName)
        try {
            if (!bound) {
                bindService(Intent(this, MusicPlayService::class.java), conn, Context.BIND_AUTO_CREATE)
                bound = true
            }
        } catch (_: Exception) { }
        service?.setSpeed(speed)
        highlightRow()
    }

    private fun step(dir: Int) {
        if (tracks.isEmpty()) return
        var idx = trackIndex
        if (idx < 0) {
            idx = tracks.indexOfFirst { it.uri.toString() == currentUri?.toString() }
            if (idx < 0) idx = if (dir > 0) -1 else 0
        }
        val nextIdx = idx + dir
        if (nextIdx !in tracks.indices) return
        val t = tracks[nextIdx]
        open(t.uri, t.name)
    }

    private fun highlightRow() {
        try {
            for (i in 0 until browserList.childCount) {
                val v = browserList.getChildAt(i) as? NativeButton ?: continue
                val active = currentName.isNotEmpty() && v.text.toString().contains(currentName)
                v.alpha = if (active) 1f else 0.75f
                if (active) browserScroll.requestChildFocus(v, v)
            }
        } catch (_: Exception) { }
    }

    private fun syncFromService() {
        val s = service ?: return
        try {
            if (s.currentName().isNotEmpty() && currentUri != null) {
                nameView.text = s.currentName()
                playing = s.isPlaying()
                toggle.isEnabled = true
                toggle.text = if (playing) "Pause" else "Play"
                s.setSpeed(speed)
            }
        } catch (_: Exception) { }
    }

    private fun stopAll() {
        handler.removeCallbacks(sleepRunnable)
        sleepMinutes = 0
        try { sleepLabel.text = "Sleep: Off" } catch (_: Exception) { }
        playing = false
        try { toggle.text = "Play" } catch (_: Exception) { }
        try { service?.stopPlayback() } catch (_: Exception) { }
        try { stopService(Intent(this, MusicPlayService::class.java)) } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        try { browser.ensureBrowser() } catch (_: Exception) { }
        syncFromService()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        try { if (::seek.isInitialized) seek.removeCallbacks(progress) } catch (_: Exception) { }
        try { handler.removeCallbacks(sleepRunnable) } catch (_: Exception) { }
        try { if (bound) unbindService(conn) } catch (_: Exception) { }
        super.onDestroy()
    }
}
