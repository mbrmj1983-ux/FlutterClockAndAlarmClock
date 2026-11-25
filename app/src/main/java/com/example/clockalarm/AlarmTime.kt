package com.example.clockalarm

import java.time.LocalDate
import java.time.ZonedDateTime

internal const val SNOOZE_DELAY_MILLIS = 10 * 60 * 1000L

internal fun snoozeTriggerAtMillis(nowMillis: Long): Long = nowMillis + SNOOZE_DELAY_MILLIS

internal fun nextAlarmTimeMillis(alarm: Alarm, now: ZonedDateTime): Long? {
    val date = alarm.date
    if (date != null) {
        val target = now.with(date)
            .withHour(alarm.hour)
            .withMinute(alarm.minute)
            .withSecond(0)
            .withNano(0)
        return target.takeIf { it.isAfter(now) }?.toInstant()?.toEpochMilli()
    }

    if (alarm.repeatDays.isNotEmpty()) {
        return (0L..7L)
            .asSequence()
            .map { days -> now.plusDays(days).withAlarmTime(alarm.hour, alarm.minute) }
            .firstOrNull { it.isAfter(now) && it.dayOfWeek in alarm.repeatDays }
            ?.toInstant()
            ?.toEpochMilli()
    }

    return nextAlarmTimeMillis(alarm.hour, alarm.minute, now)
}

internal fun nextAlarmTimeMillis(hour: Int, minute: Int, now: ZonedDateTime): Long {
    require(hour in 0..23)
    require(minute in 0..59)

    var next = now.withAlarmTime(hour, minute)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return next.toInstant().toEpochMilli()
}

private fun ZonedDateTime.withAlarmTime(hour: Int, minute: Int): ZonedDateTime =
    withHour(hour).withMinute(minute).withSecond(0).withNano(0)
