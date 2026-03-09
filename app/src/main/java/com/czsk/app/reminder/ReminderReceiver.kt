package com.czsk.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.czsk.app.R
import com.czsk.app.model.PlanType
import com.czsk.app.model.ReminderMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getLongExtra("plan_id", -1L)
        if (planId == -1L) return

        val title = intent.getStringExtra("title").orEmpty()
        val type = runCatching {
            PlanType.valueOf(intent.getStringExtra("type") ?: PlanType.OTHER.name)
        }.getOrDefault(PlanType.OTHER)
        val placeName = intent.getStringExtra("place_name").orEmpty()

        val xRaw = intent.getIntExtra("x", Int.MIN_VALUE)
        val yRaw = intent.getIntExtra("y", Int.MIN_VALUE)
        val x = xRaw.takeIf { it != Int.MIN_VALUE }
        val y = yRaw.takeIf { it != Int.MIN_VALUE }

        val scheduledTimeMillis = intent.getLongExtra("scheduled_time", System.currentTimeMillis())
        val reminderMode = runCatching {
            ReminderMode.valueOf(intent.getStringExtra("reminder_mode") ?: ReminderMode.NOTIFICATION.name)
        }.getOrDefault(ReminderMode.NOTIFICATION)

        val messageTitle = buildNotificationTitle(type, reminderMode)
        val messageBody = buildNotificationBody(
            type = type,
            title = title,
            placeName = placeName,
            x = x,
            y = y,
            scheduledTimeMillis = scheduledTimeMillis
        )

        createChannels(context)

        if (reminderMode == ReminderMode.ALARM) {
            val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
                putExtra(AlarmForegroundService.EXTRA_PLAN_ID, planId)
                putExtra(AlarmForegroundService.EXTRA_TITLE, messageTitle)
                putExtra(AlarmForegroundService.EXTRA_BODY, messageBody)
                putExtra(AlarmForegroundService.EXTRA_RINGTONE_URI, ReminderPrefs.getRingtoneUri(context))
            }

            ContextCompat.startForegroundService(context, serviceIntent)
            return
        }

        if (reminderMode == ReminderMode.RING) {
            val uriString = ReminderPrefs.getRingtoneUri(context)
            val uri = runCatching { android.net.Uri.parse(uriString) }.getOrNull()
                ?: defaultAlarmUri(context)

            runCatching {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = androidx.core.app.PendingIntentCompat.getActivity(
            context,
            planId.toInt(),
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        val channelId = if (reminderMode == ReminderMode.RING) CHANNEL_RING else CHANNEL_NORMAL

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(messageTitle)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(planId.toInt(), notification)
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val normalChannel = NotificationChannel(
            CHANNEL_NORMAL,
            "计划通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "普通计划提醒通知"
        }

        val ringChannel = NotificationChannel(
            CHANNEL_RING,
            "振铃提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "振铃类提醒通知"
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "闹钟模式提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "持续响铃和持续震动的闹钟提醒"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(normalChannel)
        manager.createNotificationChannel(ringChannel)
        manager.createNotificationChannel(alarmChannel)
    }

    private fun buildNotificationTitle(type: PlanType, mode: ReminderMode): String {
        return when (type) {
            PlanType.SIEGE -> if (mode == ReminderMode.ALARM) "攻城闹钟" else "攻城提醒"
            PlanType.RECRUIT -> if (mode == ReminderMode.ALARM) "征兵闹钟" else "征兵提醒"
            PlanType.MARCH -> if (mode == ReminderMode.ALARM) "行军闹钟" else "行军提醒"
            PlanType.BUILD -> if (mode == ReminderMode.ALARM) "修建闹钟" else "修建提醒"
            PlanType.STAMINA -> if (mode == ReminderMode.ALARM) "体力闹钟" else "体力提醒"
            PlanType.TRUCE -> if (mode == ReminderMode.ALARM) "卡免闹钟" else "卡免提醒"
            PlanType.SNATCH -> if (mode == ReminderMode.ALARM) "抢夺闹钟" else "抢夺提醒"
            PlanType.OTHER -> if (mode == ReminderMode.ALARM) "计划闹钟" else "计划提醒"
        }
    }

    private fun buildNotificationBody(
        type: PlanType,
        title: String,
        placeName: String,
        x: Int?,
        y: Int?,
        scheduledTimeMillis: Long
    ): String {
        val hasLocation = placeName.isNotBlank() || (x != null && y != null)
        val locationText = when {
            placeName.isNotBlank() && x != null && y != null -> "$placeName（$x,$y）"
            placeName.isNotBlank() -> placeName
            x != null && y != null -> "（$x,$y）"
            else -> ""
        }

        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(scheduledTimeMillis))
        val remainingText = buildRemainingText(scheduledTimeMillis)

        return when (type) {
            PlanType.SIEGE -> {
                if (hasLocation) {
                    "主公，攻城计划【$title】将于 $timeText 在 $locationText 开始，还有 $remainingText，速调兵集结！"
                } else {
                    "主公，您计划的攻城行动【$title】还有 $remainingText 就要打响，立即整军备战！"
                }
            }

            PlanType.RECRUIT -> {
                if (hasLocation) {
                    "主公，$locationText 的征兵任务【$title】已完成！将士们已整装待发。"
                } else {
                    "主公，征兵计划【$title】已完成，请及时检阅新军。"
                }
            }

            PlanType.MARCH -> {
                if (hasLocation) {
                    "主公，部队正在赶往 $locationText，预计 $timeText 到达，剩余路程还有 $remainingText。"
                } else {
                    "主公，行军【$title】还有 $remainingText 抵达目的地，请留意战场动态。"
                }
            }

            PlanType.BUILD -> {
                if (hasLocation) {
                    "主公，$locationText 的修建工程【$title】将于 $timeText 竣工，剩余 $remainingText，快去查看！"
                } else {
                    "主公，修建任务【$title】即将完成（$remainingText 后），城防将更坚固。"
                }
            }

            PlanType.STAMINA -> {
                "主公，武将【$title】体力已恢复！可以再次征战沙场。"
            }

            PlanType.TRUCE -> {
                if (hasLocation) {
                    "主公，$locationText 的免战时间即将结束，【$title】还有 $remainingText 到点，请提前准备抢回！"
                } else {
                    "主公，免战窗口【$title】还有 $remainingText 结束，请提前待命。"
                }
            }

            PlanType.SNATCH -> {
                if (hasLocation) {
                    "主公，$locationText 的抢夺窗口已临近，【$title】还有 $remainingText，请尽快衔接占位建要塞。"
                } else {
                    "主公，抢夺计划【$title】还有 $remainingText，可以准备进场占位了。"
                }
            }

            PlanType.OTHER -> {
                if (hasLocation) {
                    "主公，您关注的事件【$title】将于 $timeText 在 $locationText 发生，还有 $remainingText。"
                } else {
                    "主公，计划【$title】还有 $remainingText 开始，请留意。"
                }
            }
        }
    }

    private fun buildRemainingText(targetTimeMillis: Long): String {
        val diff = targetTimeMillis - System.currentTimeMillis()
        if (diff <= 0L) return "即将"

        val totalMinutes = diff / 60_000L
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        return when {
            days > 0 -> "${days}天${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时${minutes}分钟"
            else -> "${minutes}分钟"
        }
    }

    companion object {
        const val CHANNEL_NORMAL = "cezheng_plan_normal"
        const val CHANNEL_RING = "cezheng_plan_ring"
        const val CHANNEL_ALARM = "cezheng_plan_alarm"

        fun defaultAlarmUri(context: Context): android.net.Uri {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
}