package com.opentools.box.tools

import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Video file player (system picker + VideoView). Original implementation. */
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var video: VideoView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            video.setVideoURI(it)
            video.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        video = VideoView(this).apply {
            setMediaController(MediaController(this@VideoPlayerActivity).also { setMediaController(it) })
        }
        val pick = MaterialButton(this).apply { text = "Open video" }
        pick.setOnClickListener { picker.launch(arrayOf("video/*")) }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Video Player",LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(video, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(pick)
        }))
    }

    override fun onPause() {
        video.pause()
        super.onPause()
    }
}
