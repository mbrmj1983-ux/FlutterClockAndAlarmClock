package com.example.clockalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun hijriDateFormatterUsesUmmAlQuraCalendar() {
        assertEquals(
            "1 رمضان 1445 هـ",
            HijriDateFormatter.format(LocalDate.of(2024, 3, 11)),
        )
    }

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

    @Test
    fun nextAlarmTimeSkipsToNextSelectedWeekday() {
        val now = ZonedDateTime.of(2026, 1, 2, 9, 15, 0, 0, zone)
        val alarm = Alarm(1, 8, 0, repeatDays = setOf(DayOfWeek.MONDAY))
        val expected = ZonedDateTime.of(2026, 1, 5, 8, 0, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(alarm, now))
    }

    @Test
    fun minuteRecurrenceUsesTheNextMinuteAfterItsAnchor() {
        val now = ZonedDateTime.of(2026, 1, 2, 10, 17, 45, 0, zone)
        val alarm = Alarm(
            id = 1,
            hour = 9,
            minute = 15,
            date = LocalDate.of(2026, 1, 2),
            recurrence = AlarmRecurrence.MINUTELY,
        )
        val expected = ZonedDateTime.of(2026, 1, 2, 10, 18, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(alarm, now))
    }

    @Test
    fun hourlyRecurrencePreservesItsOriginalTimeAnchor() {
        val now = ZonedDateTime.of(2026, 1, 2, 10, 17, 45, 0, zone)
        val alarm = Alarm(
            id = 1,
            hour = 8,
            minute = 30,
            date = LocalDate.of(2026, 1, 2),
            recurrence = AlarmRecurrence.HOURLY,
        )
        val expected = ZonedDateTime.of(2026, 1, 2, 10, 30, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(alarm, now))
    }

    @Test
    fun monthlyRecurrenceClampsTheAnchorDayToTheMonthLength() {
        val now = ZonedDateTime.of(2026, 2, 28, 9, 0, 0, 0, zone)
        val alarm = Alarm(
            id = 1,
            hour = 8,
            minute = 0,
            date = LocalDate.of(2026, 1, 31),
            recurrence = AlarmRecurrence.MONTHLY,
        )
        val expected = ZonedDateTime.of(2026, 3, 31, 8, 0, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(alarm, now))
    }

    @Test
    fun yearlyRecurrenceClampsLeapDayInNonLeapYears() {
        val now = ZonedDateTime.of(2025, 3, 1, 9, 0, 0, 0, zone)
        val alarm = Alarm(
            id = 1,
            hour = 8,
            minute = 0,
            date = LocalDate.of(2024, 2, 29),
            recurrence = AlarmRecurrence.YEARLY,
        )
        val expected = ZonedDateTime.of(2026, 2, 28, 8, 0, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(alarm, now))
    }

    @Test
    fun calendarAlarmDoesNotRollOverWhenItsDateHasPassed() {
        val now = ZonedDateTime.of(2026, 1, 2, 9, 15, 0, 0, zone)
        val alarm = Alarm(1, 8, 0, date = LocalDate.of(2026, 1, 2))

        assertNull(nextAlarmTimeMillis(alarm, now))
    }

    @Test
    fun calendarAlarmUsesTheSelectedDate() {
        val now = ZonedDateTime.of(2026, 1, 2, 9, 15, 0, 0, zone)
        val alarm = Alarm(1, 8, 0, date = LocalDate.of(2026, 1, 3))
        val expected = ZonedDateTime.of(2026, 1, 3, 8, 0, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), nextAlarmTimeMillis(alarm, now))
    }

    @Test
    fun alarmStoreReadsLegacyAlarmsAsDaily() {
        assertEquals(
            Alarm(42, 6, 30),
            decodeAlarm("42|6|30"),
        )
    }

    @Test
    fun alarmStoreReadsPreSoundSettingsAsEnabled() {
        assertEquals(
            Alarm(
                id = 42,
                hour = 6,
                minute = 30,
                repeatDays = setOf(DayOfWeek.MONDAY),
                date = LocalDate.of(2026, 2, 1),
            ),
            decodeAlarm("42|6|30|true|1|2026-02-01"),
        )
    }

    @Test
    fun alarmStoreRoundTripsAlarmOptions() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            enabled = false,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            date = null,
            visualEnabled = false,
            soundEnabled = true,
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreRoundTripsCalendarDate() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            repeatDays = emptySet(),
            date = LocalDate.of(2026, 2, 1),
            visualEnabled = true,
            soundEnabled = false,
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreRoundTripsRecurrenceFrequency() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            repeatDays = emptySet(),
            date = LocalDate.of(2026, 2, 1),
            recurrence = AlarmRecurrence.YEARLY,
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun oldWeeklyAlarmDecodesToWeeklyRecurrence() {
        val alarm = decodeAlarm("42|6|30|true|1,5|-|true|false")

        assertEquals(AlarmRecurrence.WEEKLY, alarm?.effectiveRecurrence)
    }

    @Test
    fun alarmStoreRejectsInvalidRepeatDayValues() {
        assertNull(decodeAlarm("42|6|30|true|1,8|"))
    }

    @Test
    fun alarmStoreRejectsInvalidCalendarDate() {
        assertNull(decodeAlarm("42|6|30|true||not-a-date"))
    }

    @Test
    fun alarmStoreRejectsInvalidAudioSettings() {
        assertNull(decodeAlarm("42|6|30|true|1|-|invalid|true"))
    }

    @Test
    fun snoozeUsesTenMinutesFromTheCurrentTime() {
        assertEquals(1_000_000L, snoozeTriggerAtMillis(400_000L))
    }
}
