package com.example.clockalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmStore.load(context).filter(Alarm::enabled).forEach { alarm ->
            AlarmScheduler.schedule(context, alarm)
        }
        PrayerReminderScheduler.scheduleAll(context)
    }
}
