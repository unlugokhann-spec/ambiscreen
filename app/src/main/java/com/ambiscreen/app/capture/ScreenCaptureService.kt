package com.ambiscreen.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ambiscreen.app.AmbiSettings
import com.ambiscreen.app.MainActivity
import com.ambiscreen.app.R
import com.ambiscreen.app.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.ambiscreen.app.action.START"
        const val ACTION_STOP = "com.ambiscreen.app.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIFICATION_CHANNEL_ID = "ambiscreen_capture"
        private const val NOTIFICATION_ID = 1

        // Gerçek ekran çözünürlüğüne gerek yok: ortalama renk çıkarımı için
        // küçük bir yakalama boyutu CPU/pil yükünü ciddi ölçüde azaltır.
        private const val CAPTURE_WIDTH = 160
        private const val CAPTURE_HEIGHT = 90
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var captureJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val ddpSender = DdpSender()

    private var smoothed: FloatArray? = null
    private var lastFrameTimeMs = 0L
    @Volatile private var currentSettings = AmbiSettings()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val settingsRepository = SettingsRepository(applicationContext)
        serviceScope.launch {
            settingsRepository.settingsFlow.collect { currentSettings = it }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultData != null) {
                    startCapture(resultCode, resultData)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData) ?: run {
            stopSelf()
            return
        }
        projection.registerCallback(projectionCallback, null)
        mediaProjection = projection

        val reader = ImageReader.newInstance(
            CAPTURE_WIDTH,
            CAPTURE_HEIGHT,
            PixelFormat.RGBA_8888,
            2,
        )
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "AmbiScreenCapture",
            CAPTURE_WIDTH,
            CAPTURE_HEIGHT,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        )

        reader.setOnImageAvailableListener({ imgReader ->
            val now = System.currentTimeMillis()
            val minInterval = currentSettings.intervalMs
            val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (now - lastFrameTimeMs < minInterval) return@setOnImageAvailableListener
                lastFrameTimeMs = now
                processFrame(image)
            } finally {
                image.close()
            }
        }, null)
    }

    private fun processFrame(image: android.media.Image) {
        val settings = currentSettings
        if (settings.wledIp.isBlank() || settings.ledCount <= 0) return

        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(buffer)

        val contentRect = ColorExtractor.detectContentRect(bitmap)
        val zones = ColorExtractor.buildPerimeterZones(
            contentRect = contentRect,
            ledCount = settings.ledCount,
            marginPercent = settings.marginPercent,
        )
        val rawColors = ColorExtractor.averageColors(bitmap, zones)
        bitmap.recycle()

        val smoothedColors = applySmoothing(rawColors, settings.smoothingPercent)
        val finalColors = applyBrightnessAndOrder(smoothedColors, settings)

        ddpSender.send(settings.wledIp, settings.wledPort, finalColors)
    }

    private fun applySmoothing(raw: IntArray, smoothingPercent: Int): IntArray {
        val alpha = 1f - (smoothingPercent.coerceIn(0, 95) / 100f)
        var state = smoothed
        if (state == null || state.size != raw.size * 3) {
            state = FloatArray(raw.size * 3)
            for (i in raw.indices) {
                state[i * 3] = ((raw[i] shr 16) and 0xFF).toFloat()
                state[i * 3 + 1] = ((raw[i] shr 8) and 0xFF).toFloat()
                state[i * 3 + 2] = (raw[i] and 0xFF).toFloat()
            }
            smoothed = state
        }
        val result = IntArray(raw.size)
        for (i in raw.indices) {
            val r = (raw[i] shr 16) and 0xFF
            val g = (raw[i] shr 8) and 0xFF
            val b = raw[i] and 0xFF
            state[i * 3] += (r - state[i * 3]) * alpha
            state[i * 3 + 1] += (g - state[i * 3 + 1]) * alpha
            state[i * 3 + 2] += (b - state[i * 3 + 2]) * alpha
            result[i] = (state[i * 3].toInt().coerceIn(0, 255) shl 16) or
                (state[i * 3 + 1].toInt().coerceIn(0, 255) shl 8) or
                state[i * 3 + 2].toInt().coerceIn(0, 255)
        }
        return result
    }

    private fun applyBrightnessAndOrder(colors: IntArray, settings: AmbiSettings): IntArray {
        val scale = settings.brightnessPercent.coerceIn(0, 100) / 100f
        val scaled = IntArray(colors.size)
        for (i in colors.indices) {
            val r = (((colors[i] shr 16) and 0xFF) * scale).toInt().coerceIn(0, 255)
            val g = (((colors[i] shr 8) and 0xFF) * scale).toInt().coerceIn(0, 255)
            val b = ((colors[i] and 0xFF) * scale).toInt().coerceIn(0, 255)
            scaled[i] = (r shl 16) or (g shl 8) or b
        }
        return if (settings.reverseDirection) scaled.reversedArray() else scaled
    }

    private fun stopCapture() {
        captureJob?.cancel()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        ddpSender.close()
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }
}
