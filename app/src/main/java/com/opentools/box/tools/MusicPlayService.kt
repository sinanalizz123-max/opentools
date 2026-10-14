package com.opentools.box.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicPlayService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val ACTION_TOGGLE = "com.opentools.box.tools.MUSIC_TOGGLE"
        const val ACTION_STOP = "com.opentools.box.tools.MUSIC_STOP"
        const val EXTRA_URI = "uri"
        const val EXTRA_NAME = "name"
        const val NOTIF_ID = 2001
        const val CHANNEL_ID = "music_playback"

        var instance: MusicPlayService? = null
            private set

        fun startPlay(ctx: Context, uri: Uri, name: String) {
            val i = Intent(ctx, MusicPlayService::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_NAME, name)
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) { }
        }
    }

    private var player: MediaPlayer? = null
    private var trackName = "Audio"
    private var trackUri: String? = null
    private var focusRequest: AudioFocusRequest? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun service(): MusicPlayService = this@MusicPlayService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
    }

    override fun onDestroy() {
        abandonFocus()
        try { player?.release() } catch (_: Exception) { }
        player = null
        instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_STOP -> stopPlayback()
            else -> {
                val uriStr = intent?.getStringExtra(EXTRA_URI)
                val name = intent?.getStringExtra(EXTRA_NAME) ?: "Audio"
                if (uriStr != null) {
                    try { open(Uri.parse(uriStr), name) } catch (_: Exception) { }
                }
            }
        }
        return START_NOT_STICKY
    }

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (_: Exception) { false }

    fun currentName(): String = trackName

    fun sessionId(): Int = try { player?.audioSessionId ?: 0 } catch (_: Exception) { 0 }

    fun duration(): Int = try { player?.duration ?: 0 } catch (_: Exception) { 0 }

    fun position(): Int = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    fun seekTo(ms: Int) { try { player?.seekTo(ms) } catch (_: Exception) { } }

    fun setSpeed(speed: Float) {
        try {
            player?.let {
                if (Build.VERSION.SDK_INT >= 23) {
                    it.playbackParams = it.playbackParams.setSpeed(speed)
                }
            }
        } catch (_: Exception) { }
    }

    private fun open(uri: Uri, name: String) {
        trackName = name
        trackUri = uri.toString()
        if (!requestFocus()) return
        try { player?.release() } catch (_: Exception) { }
        player = null
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(this@MusicPlayService, uri)
                setOnPreparedListener { it.start(); refreshNotification() }
                setOnCompletionListener {
                    // stop-at-end: halt foreground playback, keep service idle
                    try { it.pause(); it.seekTo(it.duration) } catch (_: Exception) { }
                    refreshNotification()
                }
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
                prepareAsync()
            }
            startForeground(NOTIF_ID, buildNotification())
        } catch (_: Exception) { stopPlayback() }
    }

    fun toggle() {
        val p = player ?: return
        try {
            if (p.isPlaying) { p.pause(); abandonFocus() }
            else { if (requestFocus()) p.start() }
        } catch (_: Exception) { }
        refreshNotification()
    }

    fun pauseOnly() {
        try { player?.pause() } catch (_: Exception) { }
        refreshNotification()
    }

    fun stopPlayback() {
        try { player?.stop() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
        abandonFocus()
        try { stopForeground(true) } catch (_: Exception) { }
        try { stopSelf() } catch (_: Exception) { }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) pauseOnly()
    }

    private fun requestFocus(): Boolean {
        return try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(this)
                    .build()
                focusRequest = req
                am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) { true }
    }

    private fun abandonFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(this)
            }
        } catch (_: Exception) { }
    }

    private fun ensureChannel() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
                    nm?.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW)
                    )
                }
            }
        } catch (_: Exception) { }
    }

    private fun pendingFor(action: String): PendingIntent {
        val i = Intent(this, MusicPlayService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getService(this, action.hashCode(), i, flags)
    }

    private fun contentIntent(): PendingIntent {
        val i = Intent(this, AudioPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(this, 0, i, flags)
    }

    private fun buildNotification(): Notification {
        val playing = isPlaying()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(trackName)
            .setContentText(if (playing) "Playing" else "Paused")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent())
            .setOngoing(playing)
            .addAction(
                android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                pendingFor(ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingFor(ACTION_STOP))
            .build()
    }

    private fun refreshNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) { }
    }
}
