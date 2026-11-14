package com.voidex.s2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

/**
 * User-consented screen capture foundation.
 *
 * Captured frames are currently sampled only to verify that the MediaProjection
 * pipeline is alive. No player classifier is bundled yet, so this service does
 * not generate ESP markers or infer health/names/positions.
 */
class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.voidex.s2.capture.START"
        const val ACTION_STOP = "com.voidex.s2.capture.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "voidex_capture"
        private const val NOTIFICATION_ID = 2102
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var framesReceived = 0L
    private var statsRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startCapture(resultCode, resultData)
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        stopCapture()
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi
        worker = HandlerThread("VoidexFrameCapture").also { it.start() }
        val handler = Handler(worker!!.looper)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                framesReceived++
                image.close()
            }
        }, handler)

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // MediaProjection is already stopped by the system here.
                // Do not call projection.stop() again from its own stop callback.
                stopCapture(stopProjection = false)
                stopSelf()
            }
        }, handler)
        virtualDisplay = projection?.createVirtualDisplay(
            "VOIDEX-S2-ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
        if (projection == null || virtualDisplay == null) {
            stopCapture()
            stopForegroundCompat()
            stopSelf()
            return
        }

        // Periodically refresh capture diagnostics in the foreground notification.
        val stats = object : Runnable {
            override fun run() {
                if (projection == null || worker == null) return
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, buildNotification())
                handler.postDelayed(this, 5000L)
            }
        }
        statsRunnable = stats
        handler.postDelayed(stats, 5000L)
    }

    private fun stopCapture(stopProjection: Boolean = true) {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        val activeProjection = projection
        projection = null
        if (stopProjection) {
            try { activeProjection?.stop() } catch (_: Exception) {}
        }
        statsRunnable?.let { callback -> worker?.let { thread -> Handler(thread.looper).removeCallbacks(callback) } }
        statsRunnable = null
        worker?.quitSafely()
        worker = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "VOIDEX Screen Capture", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val stopPending = android.app.PendingIntent.getService(
            this, 2, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return builder.setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("VOIDEX: захват экрана активен")
            .setContentText("Захват активен · получено кадров: $framesReceived")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPending)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopCapture()
        stopForegroundCompat()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
