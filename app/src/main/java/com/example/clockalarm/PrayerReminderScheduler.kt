package com.example.clockalarm

import android.app.AlarmManager
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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.ZoneId

internal enum class PrayerReminderCategory(val title: String, val isPrayer: Boolean = false) {
    FAJR("صلاة الفجر", true),
    DHUHR("صلاة الظهر", true),
    ASR("صلاة العصر", true),
    MAGHRIB("صلاة المغرب", true),
    ISHA("صلاة العشاء", true),
    FRIDAY("تذكير يوم الجمعة"),
    RAMADAN("بداية شهر رمضان"),
    ARAFAH("يوم عرفة"),
    EID_FITR("عيد الفطر"),
    EID_ADHA("عيد الأضحى"),
    SPRING("بداية الربيع"),
    SUMMER("بداية الصيف"),
    AUTUMN("بداية الخريف"),
    WINTER("بداية الشتاء"),
}

internal data class PrayerReminderSettings(
    val enabled: Set<PrayerReminderCategory> = emptySet(),
) {
    fun isEnabled(category: PrayerReminderCategory): Boolean = category in enabled

    fun withEnabled(category: PrayerReminderCategory, value: Boolean): PrayerReminderSettings =
        copy(enabled = if (value) enabled + category else enabled - category)
}

internal object PrayerReminderSettingsStore {
    private const val PREFERENCES = "prayer_reminders"

    fun load(context: Context): PrayerReminderSettings {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return PrayerReminderSettings(
            PrayerReminderCategory.values()
                .filterTo(mutableSetOf()) { preferences.getBoolean(it.name, false) },
        )
    }

    fun save(context: Context, settings: PrayerReminderSettings) {
        val editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
        PrayerReminderCategory.values().forEach { category ->
            editor.putBoolean(category.name, settings.isEnabled(category))
        }
        editor.apply()
    }
}

internal fun nextPrayerReminder(
    category: PrayerReminderCategory,
    now: Instant,
    settings: PrayerSettings,
): CountdownEntry? {
    val location = settings.location
    val zone = ZoneId.of(location.zoneId)
    return when (category) {
        PrayerReminderCategory.FAJR,
        PrayerReminderCategory.DHUHR,
        PrayerReminderCategory.ASR,
        PrayerReminderCategory.MAGHRIB,
        PrayerReminderCategory.ISHA,
        -> {
            val prayer = when (category) {
                PrayerReminderCategory.FAJR -> PrayerName.FAJR
                PrayerReminderCategory.DHUHR -> PrayerName.DHUHR
                PrayerReminderCategory.ASR -> PrayerName.ASR
                PrayerReminderCategory.MAGHRIB -> PrayerName.MAGHRIB
                PrayerReminderCategory.ISHA -> PrayerName.ISHA
                else -> error("Not a prayer reminder category")
            }
            val date = now.atZone(zone).toLocalDate()
            val today = PrayerTimeCalculator.calculate(
                date,
                location,
                settings.method,
                settings.asrFactor,
                settings.adjustments,
            )
            val tomorrow = PrayerTimeCalculator.calculate(
                date.plusDays(1),
                location,
                settings.method,
                settings.asrFactor,
                settings.adjustments,
            )
            listOf(today.times.getValue(prayer), tomorrow.times.getValue(prayer))
                .firstOrNull { it.isAfter(now) }
                ?.let { at -> CountdownEntry("حان وقت صلاة ${prayer.arabic}", at) }
        }
        PrayerReminderCategory.FRIDAY -> IslamicEvents.nextFriday(now, location)
        PrayerReminderCategory.RAMADAN ->
            IslamicEvents.nextHijriDate(now, location, 9, 1, "بداية شهر رمضان")
        PrayerReminderCategory.ARAFAH ->
            IslamicEvents.nextHijriDate(now, location, 12, 9, "يوم عرفة")
        PrayerReminderCategory.EID_FITR ->
            IslamicEvents.nextHijriDate(now, location, 10, 1, "عيد الفطر")
        PrayerReminderCategory.EID_ADHA ->
            IslamicEvents.nextHijriDate(now, location, 12, 10, "عيد الأضحى")
        PrayerReminderCategory.SPRING,
        PrayerReminderCategory.SUMMER,
        PrayerReminderCategory.AUTUMN,
        PrayerReminderCategory.WINTER,
        -> SeasonCalculator.nextStarts(now, location.latitude < 0)
            .firstOrNull { it.title == category.title }
    }
}

internal object PrayerReminderScheduler {
    private const val ACTION_REMINDER = "com.example.clockalarm.PRAYER_REMINDER"
    private const val EXTRA_CATEGORY = "reminder_category"
    private const val CHANNEL_ID = "prayer_and_event_reminders_morse_dot"
    private const val NOTIFICATION_ID_BASE = 8200

    fun scheduleAll(context: Context, now: Instant = Instant.now()) {
        val reminderSettings = PrayerReminderSettingsStore.load(context)
        val prayerSettings = PrayerSettingsStore.load(context)
        PrayerReminderCategory.values().forEach { category ->
            schedule(context, category, reminderSettings, prayerSettings, now)
        }
    }

    fun scheduleNext(context: Context, category: PrayerReminderCategory, now: Instant = Instant.now()) {
        schedule(
            context,
            category,
            PrayerReminderSettingsStore.load(context),
            PrayerSettingsStore.load(context),
            now,
        )
    }

    private fun schedule(
        context: Context,
        category: PrayerReminderCategory,
        reminderSettings: PrayerReminderSettings,
        prayerSettings: PrayerSettings,
        now: Instant,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context, category)
        if (!reminderSettings.isEnabled(category)) {
            alarmManager.cancel(operation)
            return
        }
        val next = try {
            nextPrayerReminder(category, now, prayerSettings)
        } catch (exception: IllegalArgumentException) {
            Log.w("PrayerReminderScheduler", "Could not calculate next ${category.name} reminder", exception)
            null
        }
        if (next == null) {
            alarmManager.cancel(operation)
            return
        }
        val scheduledOperation = pendingIntent(context, category, next.title)
        val atMillis = next.at.toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, scheduledOperation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, scheduledOperation)
        }
    }

    private fun pendingIntent(
        context: Context,
        category: PrayerReminderCategory,
        title: String? = null,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_BASE + category.ordinal,
            Intent(context, PrayerReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                data = Uri.parse("clockalarm://reminder/${category.name}")
                putExtra(EXTRA_CATEGORY, category.name)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun showNotification(context: Context, category: PrayerReminderCategory, title: String?) {
        ensureNotificationChannel(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val notificationTitle = title ?: category.title
        val openApp = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BASE + category.ordinal,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_BASE + category.ordinal,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(notificationTitle)
                .setContentText(context.getString(R.string.prayer_reminder_notification_text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build(),
        )
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.prayer_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.prayer_reminder_channel_description)
                setSound(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.alarm_morse_dot}"),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

    internal fun notificationId(category: PrayerReminderCategory): Int =
        NOTIFICATION_ID_BASE + category.ordinal

    internal fun categoryFromName(name: String?): PrayerReminderCategory? =
        PrayerReminderCategory.values().firstOrNull { it.name == name }

    internal const val ACTION_REMINDER_VALUE = ACTION_REMINDER
    internal const val EXTRA_CATEGORY_VALUE = EXTRA_CATEGORY
    internal const val EXTRA_TITLE_VALUE = "reminder_title"
    private const val EXTRA_TITLE = EXTRA_TITLE_VALUE
}

class PrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerReminderScheduler.ACTION_REMINDER_VALUE) return
        val category = PrayerReminderScheduler.categoryFromName(
            intent.getStringExtra(PrayerReminderScheduler.EXTRA_CATEGORY_VALUE),
        ) ?: return
        val settings = PrayerReminderSettingsStore.load(context)
        if (!settings.isEnabled(category)) return
        PrayerReminderScheduler.scheduleNext(context, category)
        PrayerReminderScheduler.showNotification(
            context,
            category,
            intent.getStringExtra(PrayerReminderScheduler.EXTRA_TITLE_VALUE),
        )
    }
}
