package com.example.clockalarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_RING -> ring(context, intent, isSnooze = false)
            AlarmScheduler.ACTION_RING_SNOOZED -> ring(context, intent, isSnooze = true)
            AlarmScheduler.ACTION_SNOOZE -> snooze(context, intent)
            AlarmScheduler.ACTION_DISMISS -> dismiss(context, intent)
            else -> return
        }
    }

    private fun ring(context: Context, intent: Intent, isSnooze: Boolean) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id < 0) return

        val alarms = AlarmStore.load(context).toMutableList()
        val index = alarms.indexOfFirst { it.id == id }
        if (index < 0) return
        val storedAlarm = alarms[index]
        if (!isSnooze && !storedAlarm.enabled) return

        if (!isSnooze) {
            if (storedAlarm.effectiveRecurrence == AlarmRecurrence.ONCE) {
                alarms[index] = storedAlarm.copy(enabled = false)
                AlarmStore.save(context, alarms)
            } else {
                AlarmScheduler.schedule(context, storedAlarm)
            }
        }
        showNotification(context, storedAlarm)
    }

    private fun snooze(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val alarm = AlarmStore.load(context).firstOrNull { it.id == id } ?: return
        AlarmScheduler.scheduleSnooze(context, alarm)
        NotificationManagerCompat.from(context).cancel(alarm.id.hashCode())
    }

    private fun dismiss(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id >= 0) NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    private fun showNotification(context: Context, alarm: Alarm) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = if (alarm.soundEnabled) SOUND_CHANNEL_ID else SILENT_CHANNEL_ID
        val channelName = if (alarm.soundEnabled) {
            R.string.alarm_channel_sound_name
        } else {
            R.string.alarm_channel_silent_name
        }
        val channel = NotificationChannel(
            channelId,
            context.getString(channelName),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            if (alarm.soundEnabled) {
                setSound(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.alarm_morse_dot}"),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            } else {
                setSound(null, null)
            }
        }
        manager.createNotificationChannel(
            channel,
        )

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val openAlarm = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            Intent(context, AlarmRingingActivity::class.java).apply {
                action = "com.example.clockalarm.OPEN_ALARM"
                data = Uri.parse("clockalarm://notification/${alarm.id}")
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                putExtra(AlarmScheduler.EXTRA_HOUR, alarm.hour)
                putExtra(AlarmScheduler.EXTRA_MINUTE, alarm.minute)
                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarm.label)
                putExtra(AlarmScheduler.EXTRA_LINK_URL, alarm.linkUrl)
                putExtra(AlarmScheduler.EXTRA_LINK_LABEL, alarm.linkLabel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissAction = actionPendingIntent(
            context,
            alarm,
            AlarmScheduler.ACTION_DISMISS,
            "dismiss",
            1,
        )
        val snoozeAction = actionPendingIntent(
            context,
            alarm,
            AlarmScheduler.ACTION_SNOOZE,
            "snooze",
            2,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.label?.takeIf(String::isNotBlank) ?: context.getString(R.string.app_name))
            .setContentText("%02d:%02d".format(alarm.hour, alarm.minute))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAlarm)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.dismiss_alarm),
                dismissAction,
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.snooze_alarm),
                snoozeAction,
            )

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            manager.canUseFullScreenIntent()
        ) {
            notification.setFullScreenIntent(openAlarm, true)
        }
        NotificationManagerCompat.from(context).notify(alarm.id.hashCode(), notification.build())
    }

    private fun actionPendingIntent(
        context: Context,
        alarm: Alarm,
        action: String,
        path: String,
        requestCodeOffset: Int,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            alarm.id.hashCode() + requestCodeOffset,
            Intent(context, AlarmRingingActivity::class.java).apply {
                this.action = action
                data = Uri.parse("clockalarm://$path/${alarm.id}")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                putExtra(AlarmScheduler.EXTRA_HOUR, alarm.hour)
                putExtra(AlarmScheduler.EXTRA_MINUTE, alarm.minute)
                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarm.label)
                putExtra(AlarmScheduler.EXTRA_LINK_URL, alarm.linkUrl)
                putExtra(AlarmScheduler.EXTRA_LINK_LABEL, alarm.linkLabel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val SOUND_CHANNEL_ID = "alarms_with_morse_dot"
        const val SILENT_CHANNEL_ID = "alarms_silent"
    }
}
