package com.opentools.box.tools

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.UiStyle

/** Audio file player (system picker + MediaPlayer). Original implementation. */
class AudioPlayerActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var playing = false
    private lateinit var nameView: TextView
    private lateinit var seek: SeekBar
    private lateinit var toggle: NativeButton
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
        nameView = TextView(this).apply {
            text = "Choose an audio file to begin."
            UiStyle.styleText(this, 18f, true)
            setPadding(UiStyle.dp(context, 18f), UiStyle.dp(context, 18f),
                UiStyle.dp(context, 18f), UiStyle.dp(context, 18f))
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
        val pick = NativeButton(this, false).apply { text = "Open audio" }
        toggle = NativeButton(this).apply { text = "Play"; isEnabled = false }
        pick.setOnClickListener { picker.launch(arrayOf("audio/*")) }
        toggle.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (playing) { p.pause(); toggle.text = "Play" }
            else { p.start(); toggle.text = "Pause" }
            playing = !playing
        }
        setContentView(com.opentools.box.ToolScaffold.wrap(this, "Audio Player", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Now playing"
                UiStyle.styleText(this, 13f, true, UiStyle.muted(context))
                setPadding(0, 0, 0, UiStyle.dp(context, 8f))
            })
            addView(nameView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 16f)
            })
            addView(seek, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 12f)
            })
            addView(pick, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = UiStyle.dp(context, 8f)
            })
            addView(toggle, LinearLayout.LayoutParams(-1, -2))
        }))
    }

    private fun open(uri: Uri) {
        seek.removeCallbacks(progress)
        try { player?.release() } catch (_: Exception) { }
        player = null
        playing = false
        toggle.isEnabled = false
        toggle.text = "Loading…"
        nameView.text = uri.lastPathSegment ?: "Audio"
        try {
            player = MediaPlayer().apply {
                setDataSource(this@AudioPlayerActivity, uri)
                setOnPreparedListener { prepared ->
                    seek.max = prepared.duration.coerceAtLeast(0)
                    seek.progress = 0
                    toggle.isEnabled = true
                    toggle.text = "Play"
                    seek.post(progress)
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
        toggle.text = "Play"
        super.onPause()
    }

    override fun onDestroy() {
        seek.removeCallbacks(progress)
        player?.release()
        super.onDestroy()
    }
}
