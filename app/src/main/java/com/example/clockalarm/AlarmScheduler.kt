package com.example.clockalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.ZonedDateTime

object AlarmScheduler {
    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.enabled) return
        val triggerAtMillis = nextAlarmTimeMillis(alarm, ZonedDateTime.now()) ?: return
        scheduleAt(context, alarm, ACTION_RING, triggerAtMillis)
    }

    fun scheduleSnooze(context: Context, alarm: Alarm) {
        scheduleAt(
            context,
            alarm,
            ACTION_RING_SNOOZED,
            snoozeTriggerAtMillis(System.currentTimeMillis()),
        )
    }

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context, alarm, ACTION_RING))
        alarmManager.cancel(pendingIntent(context, alarm, ACTION_RING_SNOOZED))
    }

    private fun scheduleAt(context: Context, alarm: Alarm, action: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context, alarm, action)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun pendingIntent(context: Context, alarm: Alarm, action: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            data = Uri.parse(
                if (action == ACTION_RING) {
                    "clockalarm://alarm/${alarm.id}"
                } else {
                    "clockalarm://snooze/${alarm.id}"
                },
            )
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_HOUR, alarm.hour)
            putExtra(EXTRA_MINUTE, alarm.minute)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_RING = "com.example.clockalarm.RING"
    const val ACTION_RING_SNOOZED = "com.example.clockalarm.RING_SNOOZED"
    const val ACTION_SNOOZE = "com.example.clockalarm.SNOOZE"
    const val ACTION_DISMISS = "com.example.clockalarm.DISMISS"
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_HOUR = "alarm_hour"
    const val EXTRA_MINUTE = "alarm_minute"
    const val EXTRA_ALARM_LABEL = "alarm_label"
    const val EXTRA_LINK_URL = "alarm_link_url"
    const val EXTRA_LINK_LABEL = "alarm_link_label"
}
