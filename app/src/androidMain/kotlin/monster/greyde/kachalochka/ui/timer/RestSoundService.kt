package monster.greyde.kachalochka.ui.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Instant

private const val LOG_TAG = "Kachalochka"
private const val CHANNEL = "rest_timer"
private const val NOTIFICATION_ID = 1
private const val EXTRA_END = "end"
private const val EXTRA_SOUND = "sound"

// Covers the sound itself after the rest's end.
private const val PLAY_LIMIT_MILLIS = 30_000L

/** Hands each rest to [RestSoundService]. */
class ServiceRestAlarm(
    private val context: Context,
) : RestAlarm {
    override fun schedule(
        end: Instant,
        sound: String?,
    ) {
        val intent =
            Intent(context, RestSoundService::class.java)
                .putExtra(EXTRA_END, end.toEpochMilliseconds())
                .putExtra(EXTRA_SOUND, sound)
        // The system refuses a foreground service to an app in the background.
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (refused: IllegalStateException) {
            Log.w(LOG_TAG, "Could not start the rest timer service", refused)
        }
    }

    override fun cancel() {
        context.stopService(Intent(context, RestSoundService::class.java))
    }
}

/**
 * Shows the rest as a countdown notification and plays its sound at the end, so the sound comes
 * with the screen locked. The channel itself is silent: the service plays the chosen sound as
 * media, which goes to the headphones the user listens to, at their volume, in silent mode too.
 */
class RestSoundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    // Without it the CPU may sleep through the end of the rest once the screen is locked.
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kachalochka:rest")
            .apply { setReferenceCounted(false) }
    }

    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val end = intent?.getLongExtra(EXTRA_END, 0L) ?: 0L
        val sound = intent?.getStringExtra(EXTRA_SOUND)
        showCountdown(end)
        handler.removeCallbacksAndMessages(null)
        release()
        val delay = (end - System.currentTimeMillis()).coerceAtLeast(0L)
        wakeLock.acquire(delay + PLAY_LIMIT_MILLIS)
        handler.postDelayed({ play(sound) }, delay)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) = finish()

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) = finish()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        release()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun showCountdown(end: Long) {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, countdown(end), type)
    }

    private fun countdown(end: Long): Notification {
        val strings = AppStrings.current
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                strings.restNotificationChannel,
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val open =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        return NotificationCompat
            .Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(strings.restNotificationTitle)
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    /**
     * A chosen sound that is gone, or unreadable, gives way to the default one. Without the audio
     * focus, as in a phone call, the rest ends in silence.
     */
    private fun play(sound: String?) {
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
        val granted = getSystemService(AudioManager::class.java).requestAudioFocus(request)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return finish()
        focus = request
        val default = Settings.System.DEFAULT_NOTIFICATION_URI
        val started = start(sound?.toUri() ?: default) || (sound != null && start(default))
        if (!started) finish()
    }

    private fun start(uri: Uri): Boolean {
        val media = MediaPlayer()
        return try {
            media.setAudioAttributes(attributes)
            media.setDataSource(this, uri)
            media.setOnCompletionListener { finish() }
            media.setOnErrorListener { _, _, _ ->
                finish()
                true
            }
            media.prepare()
            media.start()
            player = media
            true
        } catch (unplayable: Exception) {
            Log.w(LOG_TAG, "Could not play the rest sound", unplayable)
            media.release()
            false
        }
    }

    private fun finish() {
        release()
        stopSelf()
    }

    private fun release() {
        player?.release()
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }
}
