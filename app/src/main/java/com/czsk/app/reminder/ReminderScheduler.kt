package com.czsk.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.czsk.app.model.PlanItem
import com.czsk.app.model.ReminderMode

object ReminderScheduler {

    private const val MAX_REMINDER_SLOTS = 10

    private fun buildIntent(context: Context, plan: PlanItem, leadMinutes: Int): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            putExtra("plan_id", plan.id)
            putExtra("title", plan.title)
            putExtra("type", plan.type.name)
            putExtra("place_name", plan.placeName)
            putExtra("x", plan.x ?: Int.MIN_VALUE)
            putExtra("y", plan.y ?: Int.MIN_VALUE)
            putExtra("scheduled_time", plan.scheduledTimeMillis)
            putExtra("note", plan.note)
            putExtra("reminder_mode", plan.reminderMode.name)
            putExtra("lead_minutes", leadMinutes)
        }
    }

    private fun requestCode(planId: Long, slotIndex: Int): Int {
        return "${planId}_$slotIndex".hashCode()
    }

    private fun buildPendingIntent(
        context: Context,
        plan: PlanItem,
        slotIndex: Int,
        leadMinutes: Int
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode(plan.id, slotIndex),
            buildIntent(context, plan, leadMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleReminder(context: Context, plan: PlanItem) {
        cancelReminder(context, plan.id)

        if (plan.reminderMode == ReminderMode.NONE) {
            return
        }

        val leadMinutesList = plan.allReminderLeadMinutes
        if (leadMinutesList.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        leadMinutesList
            .take(MAX_REMINDER_SLOTS)
            .forEachIndexed { index, leadMinutes ->
                val triggerAtMillis = plan.scheduledTimeMillis - leadMinutes * 60_000L

                if (triggerAtMillis <= now) {
                    return@forEachIndexed
                }

                val pendingIntent = buildPendingIntent(context, plan, index, leadMinutes)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
    }

    fun cancelReminder(context: Context, planId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        repeat(MAX_REMINDER_SLOTS) { slotIndex ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(planId, slotIndex),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}