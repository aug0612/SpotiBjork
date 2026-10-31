package com.spotibjork.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Service de premier plan : garde l'app en vie pendant l'écoute, affiche la notification
 * média (écran de verrouillage compris) et relaie les boutons vers le lecteur web.
 */
class PlaybackService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var session: MediaSession
    private var wakeLock: PowerManager.WakeLock? = null

    private var title = ""
    private var artist = ""
    private var playing = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        session = MediaSession(this, "Spotibjork")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = WebHolder.click(Config.TEST_PLAY_PAUSE)
            override fun onPause() = WebHolder.click(Config.TEST_PLAY_PAUSE)
            override fun onSkipToNext() = WebHolder.click(Config.TEST_NEXT)
            override fun onSkipToPrevious() = WebHolder.click(Config.TEST_PREV)
        })
        session.isActive = true
        updateSession()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> WebHolder.click(Config.TEST_PREV)
            ACTION_TOGGLE -> WebHolder.click(Config.TEST_PLAY_PAUSE)
            ACTION_NEXT -> WebHolder.click(Config.TEST_NEXT)
        }
        return START_NOT_STICKY
    }

    /** Appelé depuis le pont JavaScript (n'importe quel thread). */
    fun update(newTitle: String, newArtist: String, nowPlaying: Boolean) {
        handler.post {
            if (newTitle == title && newArtist == artist && nowPlaying == playing) return@post
            title = newTitle
            artist = newArtist
            playing = nowPlaying
            updateSession()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
            if (playing) acquireLock() else releaseLock()
        }
    }

    private fun updateSession() {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .build()
        )
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(if (title.isEmpty()) getString(R.string.app_name) else title)
            .setContentText(artist)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_prev, R.string.prev, ACTION_PREV, 1))
            .addAction(
                if (playing) action(R.drawable.ic_pause, R.string.pause, ACTION_TOGGLE, 2)
                else action(R.drawable.ic_play, R.string.play, ACTION_TOGGLE, 2)
            )
            .addAction(action(R.drawable.ic_next, R.string.next, ACTION_NEXT, 3))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun action(icon: Int, label: Int, act: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, code,
            Intent(this, PlaybackService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), pi).build()
    }

    private fun acquireLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spotibjork:playback").apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(6 * 60 * 60 * 1000L)
    }

    private fun releaseLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        instance = null
        releaseLock()
        session.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var instance: PlaybackService? = null

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PREV = "com.spotibjork.app.PREV"
        private const val ACTION_TOGGLE = "com.spotibjork.app.TOGGLE"
        private const val ACTION_NEXT = "com.spotibjork.app.NEXT"
    }
}
