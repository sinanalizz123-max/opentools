package com.opentools.box.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle
import android.content.pm.ActivityInfo

/** Local video player with on-device folder browser, VideoView and custom control bar. */
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var video: VideoView
    private lateinit var status: TextView
    private lateinit var browser: MediaBrowserHelper
    private lateinit var browserStatus: TextView
    private lateinit var browserList: LinearLayout
    private lateinit var browserScroll: ScrollView
    private lateinit var controlBar: LinearLayout
    private lateinit var playBtn: NativeButton
    private lateinit var speedBtn: NativeButton
    private lateinit var fullBtn: NativeButton
    private lateinit var seek: SeekBar
    private lateinit var curView: TextView
    private lateinit var totalView: TextView
    private lateinit var hintView: TextView
    private lateinit var libStrip: LinearLayout
    private lateinit var sortBtn: NativeButton

    private var speed = 1f
    private var fullscreen = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { controlBar.visibility = View.GONE }
    private val ticker = object : Runnable {
        override fun run() {
            try {
                if (video.isPlaying || video.duration > 0) {
                    seek.max = video.duration.coerceAtLeast(0)
                    seek.progress = video.currentPosition.coerceAtLeast(0)
                    curView.text = fmtTime(video.currentPosition.toLong())
                    totalView.text = fmtTime(video.duration.toLong())
                }
            } catch (_: Exception) { }
            video.postDelayed(this, 500)
        }
    }
    private val thumbCache = linkedMapOf<String, Bitmap?>()
    private val metaCache = linkedMapOf<String, String>()
    private var sortMode = "Name"

    private fun neededPermission(): String = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

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
            showControls()
        } catch (_: Exception) {
            status.text = "Couldn't open this video."
        }
    }

    private fun showControls() {
        controlBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 3000)
    }

    private fun applySpeed() {
        speedBtn.text = "${speed}x"
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                video.setOnPreparedListener { mp ->
                    try { mp.playbackParams = mp.playbackParams.setSpeed(speed) } catch (_: Exception) { }
                    status.text = "Video ready"
                    totalView.text = fmtTime(mp.duration.toLong())
                    mp.start()
                    playBtn.text = "Pause"
                    showControls()
                }
            }
        } catch (_: Exception) { }
    }

    private fun flashHint(text: String) {
        hintView.text = text
        hintView.visibility = View.VISIBLE
        handler.removeCallbacks(hideHint)
        handler.postDelayed(hideHint, 900)
    }

    private val hideHint = Runnable { try { hintView.visibility = View.GONE } catch (_: Exception) { } }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        if (on) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            browserScroll.visibility = View.GONE
            browserStatus.visibility = View.GONE
            libStrip.visibility = View.GONE
            sortBtn.visibility = View.GONE
            fullBtn.text = "Exit FS"
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            browserScroll.visibility = View.VISIBLE
            browserStatus.visibility = View.VISIBLE
            libStrip.visibility = View.VISIBLE
            sortBtn.visibility = View.VISIBLE
            fullBtn.text = "Full"
        }
        showControls()
    }

    private fun loadLibraryStrip() {
        sortMode = SortPref.get(this, "video_sort", "Name")
        sortBtn.text = "Sort: $sortMode"
        Thread {
            val items = queryVideos(sortMode)
            runOnUiThread { renderStrip(items) }
        }.start()
    }

    private data class Vid(val uri: Uri, val name: String, val date: Long, val size: Long, val dur: Long)

    private fun queryVideos(sort: String): List<Vid> {
        val out = mutableListOf<Vid>()
        try {
            val order = when (sort) {
                "Date" -> MediaStore.Video.Media.DATE_MODIFIED + " DESC"
                "Size" -> MediaStore.Video.Media.SIZE + " DESC"
                else -> MediaStore.MediaColumns.DISPLAY_NAME + " ASC"
            }
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_MODIFIED,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION
                ), null, null, order
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dCol = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val sCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    out.add(
                        Vid(
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                            c.getString(nCol) ?: "Video",
                            if (dCol >= 0) c.getLong(dCol) else 0L,
                            if (sCol >= 0) c.getLong(sCol) else 0L,
                            if (durCol >= 0) c.getLong(durCol) else 0L
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return out.take(30)
    }

    private fun renderStrip(items: List<Vid>) {
        try {
            libStrip.removeAllViews()
            items.forEach { v ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    UiStyle.clickable(this)
                    setOnClickListener { play(v.uri, v.name) }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    val iv = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(UiStyle.dp(context, 56f), UiStyle.dp(context, 40f))
                    }
                    row.addView(iv)
                    val key = v.uri.toString()
                    val cached = thumbCache[key]
                    if (cached != null) iv.setImageBitmap(cached)
                    else Thread {
                        try {
                            val b = contentResolver.loadThumbnail(v.uri, Size(160, 120), null)
                            thumbCache[key] = b
                            runOnUiThread { try { iv.setImageBitmap(b) } catch (_: Exception) { } }
                        } catch (_: Exception) { thumbCache[key] = null }
                    }.start()
                }
                val label = TextView(this).apply {
                    text = v.name + if (v.dur > 0) "\n" + fmtTime(v.dur) else ""
                    UiStyle.styleText(this, 12f, false)
                    setPadding(UiStyle.dp(context, 8f), 0, 0, 0)
                }
                row.addView(label)
                libStrip.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = UiStyle.dp(this@VideoPlayerActivity, 6f)
                })
            }
        } catch (_: Exception) { }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        status = TextView(this).apply {
            text = "Choose a video stored on your device."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
        }
        hintView = TextView(this).apply {
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 13f, true)
            background = UiStyle.rounded(context, UiStyle.raised(context), 10f, UiStyle.border(context))
            visibility = View.GONE
        }
        video = VideoView(this).apply {
            setOnPreparedListener { mp ->
                try {
                    if (Build.VERSION.SDK_INT >= 23) mp.playbackParams = mp.playbackParams.setSpeed(speed)
                } catch (_: Exception) { }
                status.text = "Video ready"
                totalView.text = fmtTime(mp.duration.toLong())
                start()
                playBtn.text = "Pause"
            }
            setOnCompletionListener {
                status.text = "Playback finished"
                playBtn.text = "Play"
                try { seek.progress = seek.max } catch (_: Exception) { }
            }
            setOnErrorListener { _, what, extra ->
                status.text = "Playback failed (error $what/$extra). Try another file."
                true
            }
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        playBtn = NativeButton(this, false).apply { text = "Play" }
        playBtn.setOnClickListener {
            try {
                if (video.isPlaying) { video.pause(); playBtn.text = "Play" }
                else { video.start(); playBtn.text = "Pause" }
            } catch (_: Exception) { }
            showControls()
        }
        speedBtn = NativeButton(this, false).apply { text = "1.0x" }
        speedBtn.setOnClickListener {
            speed = PlaybackSpeed.next(speed)
            applySpeed()
            try {
                if (Build.VERSION.SDK_INT >= 23 && video.isPlaying) {
                    video.post { showControls() }
                }
            } catch (_: Exception) { }
            flashHint("Speed ${speed}x")
            showControls()
        }
        fullBtn = NativeButton(this, false).apply { text = "Full" }
        fullBtn.setOnClickListener { setFullscreen(!fullscreen) }
        seek = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.accent(context))
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) try { video.seekTo(p) } catch (_: Exception) { }
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = showControls()
        })
        curView = TextView(this).apply { text = "00:00"; UiStyle.styleText(this, 12f, false, UiStyle.muted(context)) }
        totalView = TextView(this).apply { text = "00:00"; UiStyle.styleText(this, 12f, false, UiStyle.muted(context)) }
        controlBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.rounded(context, UiStyle.raised(context), 12f, UiStyle.border(context))
            setPadding(UiStyle.dp(context, 8f), UiStyle.dp(context, 8f), UiStyle.dp(context, 8f), UiStyle.dp(context, 8f))
        }
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        timeRow.addView(curView, LinearLayout.LayoutParams(0, -2, 1f))
        timeRow.addView(totalView, LinearLayout.LayoutParams(-2, -2))
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bp = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = UiStyle.dp(this@VideoPlayerActivity, 6f) }
        btnRow.addView(playBtn, bp)
        btnRow.addView(speedBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = UiStyle.dp(this@VideoPlayerActivity, 6f) })
        btnRow.addView(fullBtn, LinearLayout.LayoutParams(0, -2, 1f))
        controlBar.addView(timeRow, LinearLayout.LayoutParams(-1, -2))
        controlBar.addView(seek, LinearLayout.LayoutParams(-1, -2))
        controlBar.addView(btnRow, LinearLayout.LayoutParams(-1, -2))
        browserStatus = TextView(this).apply { UiStyle.styleText(this, 13f, true, UiStyle.muted(context)) }
        browserList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val retryPerm = NativeButton(this, false)
        val backBtn = NativeButton(this, false)
        browser = MediaBrowserHelper(
            this, "video", neededPermission(), { hasMediaAccess() },
            browserStatus, retryPerm, backBtn, browserList
        ) { uri, name -> play(uri, name) }
        browserList.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            try {
                if (backBtn.visibility == View.GONE) {
                    thumbCache.clear()
                    metaCache.clear()
                }
            } catch (_: Exception) { }
        }
        browserScroll = ScrollView(this).apply {
            addView(browserList, LinearLayout.LayoutParams(-1, -2))
        }
        libStrip = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val libScroll = ScrollView(this).apply {
            addView(libStrip, LinearLayout.LayoutParams(-1, -2))
        }
        sortBtn = NativeButton(this, false).apply { text = "Sort: Name" }
        sortBtn.setOnClickListener {
            sortMode = when (sortMode) { "Name" -> "Date"; "Date" -> "Size"; else -> "Name" }
            SortPref.set(this, "video_sort", sortMode)
            sortBtn.text = "Sort: $sortMode"
            loadLibraryStrip()
        }
        val frame = FrameLayout(this).apply {
            addView(video, FrameLayout.LayoutParams(-1, UiStyle.dp(context, 220f)))
            addView(hintView, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                controlBar.visibility = if (controlBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (controlBar.visibility == View.VISIBLE) showControls()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                try {
                    val w = video.width.coerceAtLeast(1)
                    if (e.x < w / 2) video.seekTo((video.currentPosition - 10_000).coerceAtLeast(0))
                    else video.seekTo((video.currentPosition + 10_000).coerceAtMost(video.duration))
                    flashHint(if (e.x < w / 2) "-10s" else "+10s")
                } catch (_: Exception) { }
                showControls()
                return true
            }
        })
        var downY = 0f
        var downX = 0f
        video.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { downY = ev.y; downX = ev.x }
                MotionEvent.ACTION_MOVE -> {
                    val dy = downY - ev.y
                    if (kotlin.math.abs(dy) > 40 && kotlin.math.abs(ev.x - downX) < 200) {
                        val w = video.width.coerceAtLeast(1)
                        if (ev.x < w / 2) {
                            try {
                                val lp = window.attributes
                                lp.screenBrightness = ((lp.screenBrightness.let { if (it < 0) 0.5f else it }) + dy / 1000f).coerceIn(0.01f, 1f)
                                window.attributes = lp
                                flashHint("Brightness ${(lp.screenBrightness * 100).toInt()}%")
                            } catch (_: Exception) { }
                        } else {
                            try {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                var cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                cur = (cur + if (dy > 0) 1 else -1).coerceIn(0, max)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, cur, 0)
                                flashHint("Volume $cur/$max")
                            } catch (_: Exception) { }
                        }
                        downY = ev.y
                    }
                }
            }
            true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(this@VideoPlayerActivity, 12f)
            setPadding(pad, pad, pad, pad)
            addView(backBtn, LinearLayout.LayoutParams(-1, -2))
            addView(browserStatus, LinearLayout.LayoutParams(-1, -2))
            addView(retryPerm, LinearLayout.LayoutParams(-1, -2))
            addView(browserScroll, LinearLayout.LayoutParams(-1, UiStyle.dp(this@VideoPlayerActivity, 150f)))
            addView(sortBtn, LinearLayout.LayoutParams(-1, -2))
            addView(libScroll, LinearLayout.LayoutParams(-1, UiStyle.dp(this@VideoPlayerActivity, 110f)))
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(frame, LinearLayout.LayoutParams(-1, -2))
            addView(controlBar, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Video Player", content))
        browser.ensureBrowser()
        loadLibraryStrip()
        video.post(ticker)
    }

    override fun onBackPressed() {
        if (fullscreen) { setFullscreen(false); return }
        try {
            if (browserScroll.visibility != View.VISIBLE) { setFullscreen(false); return }
        } catch (_: Exception) { }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        try { browser.ensureBrowser() } catch (_: Exception) { }
    }

    override fun onPause() {
        try { handler.removeCallbacks(hideRunnable) } catch (_: Exception) { }
        if (::video.isInitialized) video.pause()
        super.onPause()
    }

    override fun onDestroy() {
        try { video.removeCallbacks(ticker) } catch (_: Exception) { }
        thumbCache.clear()
        metaCache.clear()
        if (::video.isInitialized) video.stopPlayback()
        super.onDestroy()
    }
}
