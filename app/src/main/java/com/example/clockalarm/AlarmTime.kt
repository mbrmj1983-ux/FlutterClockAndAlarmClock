package com.example.clockalarm

import java.time.ZonedDateTime

internal fun nextAlarmTimeMillis(hour: Int, minute: Int, now: ZonedDateTime): Long {
    require(hour in 0..23)
    require(minute in 0..59)

    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return next.toInstant().toEpochMilli()
}
