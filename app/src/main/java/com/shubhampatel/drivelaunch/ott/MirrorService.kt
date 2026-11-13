package com.shubhampatel.drivelaunch.ott

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import com.shubhampatel.drivelaunch.R

/**
 * Holds the MediaProjection of the phone screen. The VirtualDisplay lives as long as the
 * service does; the car-side MirrorView just swaps the output Surface (Android 14+ allows
 * only one createVirtualDisplay per projection token).
 */
object MirrorHub {
    @Volatile var active = false
    @Volatile var width = 720
    @Volatile var height = 1600
    internal var virtualDisplay: VirtualDisplay? = null
    internal var idleSurface: Surface? = null
    val listeners = mutableListOf<() -> Unit>()

    fun attach(surface: Surface?) {
        virtualDisplay?.surface = surface ?: idleSurface
    }
    fun notifyChanged() {
        Handler(Looper.getMainLooper()).post { listeners.toList().forEach { it() } }
    }
}

class MirrorService : Service() {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            stopSelf(); return START_NOT_STICKY
        }
        startAsForeground()
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)?.also { p ->
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(Looper.getMainLooper()))
            setupDisplay(p)
        }
        if (projection == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun computeSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val b = wm.maximumWindowMetrics.bounds
        var w = b.width(); var h = b.height()
        val scale = minOf(1f, 1920f / maxOf(w, h))
        w = ((w * scale).toInt() / 2) * 2
        h = ((h * scale).toInt() / 2) * 2
        return w to h
    }

    private fun setupDisplay(p: MediaProjection) {
        val (w, h) = computeSize()
        MirrorHub.width = w; MirrorHub.height = h
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(Looper.getMainLooper()))
        reader = r
        MirrorHub.idleSurface = r.surface
        MirrorHub.virtualDisplay = p.createVirtualDisplay(
            "ott-auto-mirror", w, h, 320,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )
        MirrorHub.active = true
        MirrorHub.notifyChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val vd = MirrorHub.virtualDisplay ?: return
        val (w, h) = computeSize()
        if (w != MirrorHub.width || h != MirrorHub.height) {
            MirrorHub.width = w; MirrorHub.height = h
            vd.resize(w, h, 320)
            MirrorHub.notifyChanged()
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Phone mirroring", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("OTT Auto is mirroring your phone")
            .setContentText("Tap Stop to end screen sharing")
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onDestroy() {
        MirrorHub.active = false
        MirrorHub.virtualDisplay?.release(); MirrorHub.virtualDisplay = null
        reader?.close(); reader = null; MirrorHub.idleSurface = null
        runCatching { projection?.stop() }
        projection = null
        MirrorHub.notifyChanged()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "ott.stop"
        private const val CHANNEL = "ott_mirror"
        private const val NOTIF_ID = 4711
    }
}
