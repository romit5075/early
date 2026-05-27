package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class AlarmService : Service(), SensorEventListener {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var alarmId = -1
    private var soundUriStr: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isExtraLoud = false

    private val thirtySecondCheck = Runnable {
        enableExtraLoudMode()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "QRWake::AlarmServiceWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        soundUriStr = intent?.getStringExtra("SOUND_URI")

        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max lock

        startNotificationAndForeground()
        maximizeVolume()
        playAlarmSound()
        startIntenseVibration()

        // Register accelerometer for tilt/orientation anti-cheat
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Schedule EXTRA LOUD mode after 40 seconds
        handler.postDelayed(thirtySecondCheck, 40000L)

        return START_NOT_STICKY
    }

    private fun startNotificationAndForeground() {
        val channelId = "alarm_ringing_channel"
        val channelName = "QR Wake Alarm Rings"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Active Fullscreen Alarm notification stream"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val ringingIntent = Intent().apply {
            setClassName(packageName, "com.example.alarm.AlarmRingingActivity")
            putExtra("ALARM_ID", alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            re_code,
            ringingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("QR Wake Active Alarm")
            .setContentText("Your alarm is ringing! Scan QR code to turn off!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .build()

        startForeground(11022, notification)
    }

    private fun maximizeVolume() {
        audioManager?.let { am ->
            val maxAlarmVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
        }
    }

    private fun playAlarmSound() {
        try {
            val uri: Uri = when {
                soundUriStr == "default_sound_1" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                soundUriStr == "sound_2" -> {
                    // fallbacks or different types
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                }
                soundUriStr != null -> Uri.parse(soundUriStr)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to play custom sound, playing fallback alarm.", e)
            playFallbackSound()
        }
    }

    private fun playFallbackSound() {
        try {
            val fUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, fUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (ex: Exception) {
            Log.e("AlarmService", "Fallback ringtone execution also failed.", ex)
        }
    }

    private fun startIntenseVibration() {
        vibrator?.let { vib ->
            if (vib.hasVibrator()) {
                val timings = longArrayOf(0, 800, 300, 800)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(timings, 0)
                }
            }
        }
    }

    private fun enableExtraLoudMode() {
        isExtraLoud = true
        maximizeVolume()
        // Make vibe even more aggressive
        startExtraIntenseVibration()
        Log.w("AlarmService", "Extra loud mode activated! Volume locked to absolute max.")
    }

    private fun startExtraIntenseVibration() {
        vibrator?.let { vib ->
            if (vib.hasVibrator()) {
                val timings = longArrayOf(0, 400, 100, 400)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(timings, 0)
                }
            }
        }
    }

    // SensorEventListener (anti-cheat screen face down / tilt detection)
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val z = event.values[2]
            // Screen is facing roughly flat down when z axis acceleration is negative (-9.8 roughly)
            if (z < -8.0) {
                // Phone is flipped face-down! Force volume to absolute ceiling and trigger alert
                Log.w("AlarmService", "Face-down anti-cheat triggered! Maximizing alarm volume.")
                maximizeVolume()
                if (mediaPlayer?.isPlaying == true) {
                    // Make it play slightly faster if possible, or trigger intense vibe
                    startExtraIntenseVibration()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        handler.removeCallbacks(thirtySecondCheck)

        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.e("AlarmService", "Error unregistering sensor listener", e)
        }

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmService", "Error cleaning up MediaPlayer", e)
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AlarmService", "Error cleaning up vibrator", e)
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Error releasing WakeLock", e)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val re_code = 12051
    }
}
