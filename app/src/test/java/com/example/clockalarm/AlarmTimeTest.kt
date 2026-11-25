package com.example.clockalarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun nextAlarmTimeUsesTodayWhenAlarmIsStillAhead() {
        val now = ZonedDateTime.of(2026, 1, 2, 8, 30, 45, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 2, 9, 15, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(9, 15, now))
    }

    @Test
    fun nextAlarmTimeMovesToTomorrowWhenTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 1, 2, 9, 15, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 3, 9, 15, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(9, 15, now))
    }
}
