package com.czsk.app.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.czsk.app.R

class AlarmForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action.orEmpty()

        if (action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val planId = intent?.getLongExtra(EXTRA_PLAN_ID, -1L) ?: -1L
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        val ringtoneUriString = intent?.getStringExtra(EXTRA_RINGTONE_URI)

        val notification = buildForegroundNotification(
            planId = planId,
            title = if (title.isBlank()) "闹钟提醒" else title,
            body = body
        )

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)

        startLoopingAudio(ringtoneUriString)
        startLoopingVibration()

        return START_STICKY
    }

    private fun buildForegroundNotification(
        planId: Long,
        title: String,
        body: String
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            planId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            planId.toInt() + 100000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ReminderReceiver.CHANNEL_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "关闭", stopPendingIntent)
            .build()
    }

    private fun startLoopingAudio(ringtoneUriString: String?) {
        stopAudioOnly()

        val uri = runCatching {
            ringtoneUriString?.let { android.net.Uri.parse(it) }
        }.getOrNull() ?: ReminderReceiver.defaultAlarmUri(this)

        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AlarmForegroundService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun startLoopingVibration() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 500, 500, 1000, 500)
            val amplitudes = intArrayOf(
                0,
                180,
                0,
                255,
                0
            )
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 500, 500, 1000, 500), 0)
        }
    }

    private fun stopAudioOnly() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    private fun stopAlarm() {
        stopAudioOnly()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.cancel()
        } else {
            @Suppress("DEPRECATION")
            vibrator?.cancel()
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(FOREGROUND_NOTIFICATION_ID)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAudioOnly()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP_ALARM = "com.czsk.app.action.STOP_ALARM"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
        const val FOREGROUND_NOTIFICATION_ID = 990001
    }
}