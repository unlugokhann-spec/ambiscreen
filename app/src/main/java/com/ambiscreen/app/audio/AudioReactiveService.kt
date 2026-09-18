package com.ambiscreen.app.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.ambiscreen.app.AmbiSettings
import com.ambiscreen.app.MainActivity
import com.ambiscreen.app.R
import com.ambiscreen.app.SettingsRepository
import com.ambiscreen.app.capture.DdpSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Netflix gibi Widevine L1 DRM'li akışlarda ekran yakalama (`MediaProjection`
 * video modu) işletim sistemi tarafından donanım seviyesinde engellenir ve
 * bu yazılımla aşılamaz. Bu servis ekran yerine cihazın çaldığı SESİ
 * (`AudioPlaybackCaptureConfiguration`, Android 10+) okuyup bas/tiz enerjisine
 * göre bir renk/parlaklık üretir ve tüm LED'lere aynı rengi gönderir.
 * Gerçek ekran rengini yansıtmaz, müziğe/sese tepki veren bir ambiyans sağlar.
 *
 * Not: Kaynak uygulama (örn. Netflix) ses akışını da yakalamaya karşı
 * `setAllowedCapturePolicy(ALLOW_CAPTURE_BY_NONE)` ile kapatmışsa bu mod da
 * sessiz kalır — bu, uygulamanın kontrolü dışında bir DRM/gizlilik ayarıdır.
 */
class AudioReactiveService : Service() {

    companion object {
        const val ACTION_START = "com.ambiscreen.app.audio.action.START"
        const val ACTION_STOP = "com.ambiscreen.app.audio.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIFICATION_CHANNEL_ID = "ambiscreen_audio"
        private const val NOTIFICATION_ID = 2
        private const val SAMPLE_RATE = 44100

        // Bas/tiz ayrıştırması için tek kutuplu alçak geçiren filtre katsayısı.
        private const val LOW_PASS_ALPHA = 0.12f
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private val ddpSender = DdpSender()

    @Volatile private var currentSettings = AmbiSettings()
    private var smoothedLowState = 0f
    private var smoothedHue = 0f
    private var smoothedValue = 0f

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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    stopSelf()
                    return START_NOT_STICKY
                }
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

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData) ?: run {
            stopSelf()
            return
        }
        projection.registerCallback(projectionCallback, null)
        mediaProjection = projection

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            stopSelf()
            return
        }

        val record = try {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setBufferSizeInBytes(minBufferSize * 2)
                .build()
        } catch (e: UnsupportedOperationException) {
            stopSelf()
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            stopSelf()
            return
        }
        audioRecord = record
        record.startRecording()

        val chunkSamples = (SAMPLE_RATE * 0.1).toInt() // ~100ms'lik parçalar
        val buffer = ShortArray(chunkSamples)

        serviceScope.launch {
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                processChunk(buffer, read)
            }
        }
    }

    private fun processChunk(buffer: ShortArray, length: Int) {
        val settings = currentSettings
        if (settings.wledIp.isBlank()) return

        var lowState = smoothedLowState
        var lowSumSq = 0.0
        var highSumSq = 0.0
        var totalSumSq = 0.0

        for (i in 0 until length) {
            val s = buffer[i] / 32768f
            lowState += (s - lowState) * LOW_PASS_ALPHA
            val high = s - lowState
            lowSumSq += (lowState * lowState).toDouble()
            highSumSq += (high * high).toDouble()
            totalSumSq += (s * s).toDouble()
        }
        smoothedLowState = lowState

        val bassRms = sqrt(lowSumSq / length).toFloat()
        val trebleRms = sqrt(highSumSq / length).toFloat()
        val loudnessRms = sqrt(totalSumSq / length).toFloat()

        val sensitivity = settings.audioSensitivityPercent.coerceIn(10, 200) / 100f
        val targetValue = min(1f, loudnessRms * sensitivity * 6f)
        // Bas ağırlıklı ses -> sıcak (kırmızı/turuncu), tiz ağırlıklı -> soğuk (mavi/turkuaz).
        val energySum = bassRms + trebleRms + 0.0001f
        val targetHue = (trebleRms / energySum) * 200f

        val smoothingAlpha = 1f - (settings.smoothingPercent.coerceIn(0, 95) / 100f)
        smoothedValue += (targetValue - smoothedValue) * smoothingAlpha
        smoothedHue += (targetHue - smoothedHue) * smoothingAlpha

        val brightnessScale = settings.brightnessPercent.coerceIn(0, 100) / 100f
        val hsv = floatArrayOf(smoothedHue, 0.85f, smoothedValue.coerceIn(0f, 1f) * brightnessScale)
        val rgb = Color.HSVToColor(hsv) and 0x00FFFFFF

        val ledCount = settings.ledLayout.totalLedCount().coerceAtLeast(1)
        val colors = IntArray(ledCount) { rgb }
        ddpSender.send(settings.wledIp, settings.wledPort, colors)
    }

    private fun stopCapture() {
        audioRecord?.let { record ->
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // Zaten durmuş olabilir; yok sayılabilir.
            }
            record.release()
        }
        audioRecord = null
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
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.audio_notification_title))
            .setContentText(getString(R.string.audio_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }
}
