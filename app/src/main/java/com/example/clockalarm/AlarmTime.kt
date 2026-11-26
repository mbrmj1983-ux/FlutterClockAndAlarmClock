package com.example.clockalarm

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal const val SNOOZE_DELAY_MILLIS = 10 * 60 * 1000L

internal fun snoozeTriggerAtMillis(nowMillis: Long): Long = nowMillis + SNOOZE_DELAY_MILLIS

internal fun nextAlarmTimeMillis(alarm: Alarm, now: ZonedDateTime): Long? {
    val recurrence = alarm.effectiveRecurrence
    val target = when (recurrence) {
        AlarmRecurrence.MINUTELY -> nextIntervalTime(alarm, now, 1, ChronoUnit.MINUTES)
        AlarmRecurrence.HOURLY -> nextIntervalTime(alarm, now, 1, ChronoUnit.HOURS)
        AlarmRecurrence.DAILY -> nextAlarmDateTime(alarm.hour, alarm.minute, now)
        AlarmRecurrence.WEEKLY -> (0L..7L)
            .asSequence()
            .map { days -> now.plusDays(days).withAlarmTime(alarm.hour, alarm.minute) }
            .firstOrNull { it.isAfter(now) && it.dayOfWeek in alarm.repeatDays }
        AlarmRecurrence.MONTHLY -> nextCalendarDateTime(alarm, now) { anchor, offset ->
            YearMonth.from(now).plusMonths(offset).atDay(
                anchor.dayOfMonth.coerceAtMost(
                    YearMonth.from(now).plusMonths(offset).lengthOfMonth(),
                ),
            )
        }
        AlarmRecurrence.YEARLY -> nextCalendarDateTime(alarm, now) { anchor, offset ->
            val yearMonth = YearMonth.of(now.year + offset.toInt(), anchor.monthValue)
            yearMonth.atDay(anchor.dayOfMonth.coerceAtMost(yearMonth.lengthOfMonth()))
        }
        AlarmRecurrence.ONCE -> alarm.date
            ?.atTime(alarm.hour, alarm.minute)
            ?.atZone(now.zone)
            ?.takeIf { it.isAfter(now) }
    }

    return target?.toInstant()?.toEpochMilli()
}

internal fun nextAlarmTimeMillis(hour: Int, minute: Int, now: ZonedDateTime): Long {
    return nextAlarmDateTime(hour, minute, now).toInstant().toEpochMilli()
}

private fun nextAlarmDateTime(hour: Int, minute: Int, now: ZonedDateTime): ZonedDateTime {
    require(hour in 0..23)
    require(minute in 0..59)

    var next = now.withAlarmTime(hour, minute)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return next
}

private fun ZonedDateTime.withAlarmTime(hour: Int, minute: Int): ZonedDateTime =
    withHour(hour).withMinute(minute).withSecond(0).withNano(0)

private fun nextIntervalTime(
    alarm: Alarm,
    now: ZonedDateTime,
    interval: Long,
    unit: ChronoUnit,
): ZonedDateTime {
    val anchorDate = alarm.date ?: now.toLocalDate()
    val anchor = anchorDate.atTime(alarm.hour, alarm.minute).atZone(now.zone)
    if (anchor.isAfter(now)) return anchor
    val elapsed = unit.between(anchor, now)
    return anchor.plus(elapsed / interval + 1, unit)
}

private fun nextCalendarDateTime(
    alarm: Alarm,
    now: ZonedDateTime,
    dateForOffset: (LocalDate, Long) -> LocalDate,
): ZonedDateTime? {
    val anchor = alarm.date ?: return null
    return (0L..120L)
        .asSequence()
        .map { offset ->
            dateForOffset(anchor, offset)
                .atTime(alarm.hour, alarm.minute)
                .atZone(now.zone)
        }
        .firstOrNull { it.isAfter(now) }
}
