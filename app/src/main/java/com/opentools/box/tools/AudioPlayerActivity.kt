package com.opentools.box.tools

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Audio file player (system picker + MediaPlayer). Original implementation. */
class AudioPlayerActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var playing = false
    private lateinit var nameView: TextView
    private lateinit var seek: SeekBar
    private lateinit var toggle: MaterialButton
    private val progress = object : Runnable {
        override fun run() {
            player?.let {
                try { seek.progress = it.currentPosition } catch (_: Exception) { }
                seek.postDelayed(this, 500)
            }
        }
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { open(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nameView = TextView(this).apply { textSize = 18f; text = "No file" }
        seek = SeekBar(this)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) try { player?.seekTo(p) } catch (_: Exception) { }
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
        val pick = MaterialButton(this).apply { text = "Open audio" }
        toggle = MaterialButton(this).apply { text = "Play"; isEnabled = false }
        pick.setOnClickListener { picker.launch(arrayOf("audio/*")) }
        toggle.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (playing) { p.pause(); toggle.text = "Play" }
            else { p.start(); toggle.text = "Pause" }
            playing = !playing
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(nameView); addView(seek); addView(pick); addView(toggle)
        })
    }

    private fun open(uri: Uri) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(this@AudioPlayerActivity, uri)
            prepare()
            setOnCompletionListener { playing = false; toggle.text = "Play" }
        }
        nameView.text = uri.lastPathSegment ?: "Audio"
        seek.max = player!!.duration
        toggle.isEnabled = true
        toggle.text = "Play"
        playing = false
        seek.post(progress)
    }

    override fun onPause() {
        player?.pause()
        playing = false
        super.onPause()
    }

    override fun onDestroy() {
        seek.removeCallbacks(progress)
        player?.release()
        super.onDestroy()
    }
}
