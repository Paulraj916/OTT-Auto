package com.shubhampatel.drivelaunch.webaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.media.MediaBrowserService
import com.shubhampatel.drivelaunch.MainActivity
import com.shubhampatel.drivelaunch.R

/**
 * Foreground mediaPlayback service + MediaSession + MediaBrowserService.
 * Android Auto lists "OTT Auto" as a media app and shows its native audio-only Now Playing
 * screen (title, artwork, play/pause/prev/next/seek) driven by what the phone's WebView plays.
 */
class WebAudioService : MediaBrowserService() {

    private lateinit var session: MediaSession
    private val listener: () -> Unit = { update() }
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "OTTAutoWeb").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = WebAudioHub.command("play")
                override fun onPause() = WebAudioHub.command("pause")
                override fun onStop() = WebAudioHub.command("pause")
                override fun onSkipToNext() = WebAudioHub.command("next")
                override fun onSkipToPrevious() = WebAudioHub.command("prev")
                override fun onFastForward() = WebAudioHub.command("fwd")
                override fun onRewind() = WebAudioHub.command("rew")
                override fun onSeekTo(pos: Long) = WebAudioHub.command("seek", pos)
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = WebAudioHub.command("play")
            })
        }
        sessionToken = session.sessionToken
        createChannel()
        WebAudioHub.listeners += listener
        update()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> WebAudioHub.command(if (WebAudioHub.state.playing) "pause" else "play")
            ACTION_NEXT -> WebAudioHub.command("next")
            ACTION_PREV -> WebAudioHub.command("prev")
        }
        // Must enter the foreground quickly when started via startForegroundService.
        startForegroundNow()
        update()
        return START_NOT_STICKY
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot("root", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        val d = MediaDescription.Builder()
            .setMediaId("current")
            .setTitle("Web playback from phone")
            .setSubtitle("Start a video on your phone, audio plays here")
            .build()
        result.sendResult(mutableListOf(MediaBrowser.MediaItem(d, MediaBrowser.MediaItem.FLAG_PLAYABLE)))
    }

    private fun update() {
        val s = WebAudioHub.state
        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, s.title.ifBlank { "OTT Auto" })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist.ifBlank { s.site })
            .putString(MediaMetadata.METADATA_KEY_ALBUM, s.site)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, if (s.durationMs > 0) s.durationMs else -1L)
        WebAudioHub.art?.let { md.putBitmap(MediaMetadata.METADATA_KEY_ART, it); md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(md.build())

        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        val st = when {
            !s.active -> PlaybackState.STATE_NONE
            s.playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(st, s.positionMs, if (s.playing) 1f else 0f)
                .build()
        )
        session.isActive = s.active

        if (!s.active) {
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE); foregroundStarted = false
                stopSelf()
            }
            return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun startForegroundNow() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
        foregroundStarted = true
    }

    private fun pi(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, WebAudioService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(): Notification {
        val s = WebAudioHub.state
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(s.title.ifBlank { "OTT Auto" })
            .setContentText(s.artist.ifBlank { s.site.ifBlank { "Audio from web player" } })
            .setContentIntent(open)
            .setOngoing(s.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "Prev", pi(ACTION_PREV, 1)).build())
            .addAction(Notification.Action.Builder(null, if (s.playing) "Pause" else "Play", pi(ACTION_PLAY_PAUSE, 2)).build())
            .addAction(Notification.Action.Builder(null, "Next", pi(ACTION_NEXT, 3)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        WebAudioHub.art?.let { b.setLargeIcon(it) }
        return b.build()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Web audio playback", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        WebAudioHub.listeners -= listener
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ott_web_audio"
        private const val NOTIF_ID = 4712
        const val ACTION_PLAY_PAUSE = "ott.webaudio.playpause"
        const val ACTION_NEXT = "ott.webaudio.next"
        const val ACTION_PREV = "ott.webaudio.prev"
    }
}
