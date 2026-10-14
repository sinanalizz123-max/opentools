package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** Local video player using Android's VideoView and the system document picker. */
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var video: VideoView
    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                status.text = "Loading video…"
                video.setVideoURI(uri)
            } catch (_: Exception) {
                status.text = "Couldn't open this video."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Choose a video stored on your device."
            gravity = Gravity.CENTER
            UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            setPadding(UiStyle.dp(context, 12f), UiStyle.dp(context, 10f),
                UiStyle.dp(context, 12f), UiStyle.dp(context, 10f))
        }
        video = VideoView(this).apply {
            setMediaController(MediaController(this@VideoPlayerActivity))
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
        val pick = NativeButton(this).apply { text = "Open video" }
        pick.setOnClickListener { picker.launch(arrayOf("video/*")) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(video, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = UiStyle.dp(context, 8f)
                bottomMargin = UiStyle.dp(context, 10f)
            })
            addView(pick, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ToolScaffold.wrap(this, "Video Player", content))
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
