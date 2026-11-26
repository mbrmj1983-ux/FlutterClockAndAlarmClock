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
    fun alarmStoreRoundTripsOptionalArabicLabelWithSeparators() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            label = "دواء | الصباح",
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreRoundTripsLinkAndButtonLabel() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            linkUrl = "https://example.com/reminder",
            linkLabel = "فتح صفحة الدواء",
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreRoundTripsOptionalLinkButtonColor() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            linkUrl = "https://example.com",
            linkLabel = "فتح",
            linkButtonColor = 0xFF0061A4L,
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreRoundTripsSelectedApplicationTarget() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            linkLabel = "فتح التطبيق",
            linkTargetType = AlarmLinkTargetType.APP,
            linkTarget = "com.example.calendar",
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreRoundTripsSelectedFileTarget() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            linkLabel = "فتح الملف",
            linkTargetType = AlarmLinkTargetType.FILE,
            linkTarget = "content://com.example.provider/document/123",
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreRoundTripsOtherSupportedIntentTarget() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 30,
            linkUrl = "mailto:person@example.com",
            linkLabel = "إرسال بريد",
            linkTargetType = AlarmLinkTargetType.OTHER,
        )

        assertEquals(alarm, decodeAlarm(encodeAlarm(alarm)))
    }

    @Test
    fun alarmStoreTreatsBlankOptionalLabelAsMissing() {
        val alarm = Alarm(id = 42, hour = 6, minute = 30, label = "  ")

        assertEquals(null, decodeAlarm(encodeAlarm(alarm))?.label)
    }

    @Test
    fun webLinksDefaultToHttpsAndRejectNonWebSchemes() {
        assertEquals("https://example.com/page", normalizeWebUrl("example.com/page"))
        assertEquals("http://example.com", normalizeWebUrl("http://example.com"))
        assertEquals(
            "https://www.youtube.com/watch?v=video123",
            normalizeWebUrl("https://www.youtube.com/watch?v=video123"),
        )
        assertEquals(
            "https://youtu.be/video123",
            normalizeWebUrl("youtu.be/video123"),
        )
        assertNull(normalizeWebUrl("javascript:alert(1)"))
        assertNull(normalizeWebUrl("https://"))
    }

    @Test
    fun otherAlarmTargetsAllowSupportedAndroidSchemesOnly() {
        assertEquals("mailto:person@example.com", normalizeOtherAlarmTarget("mailto:person@example.com"))
        assertEquals("tel:+123456789", normalizeOtherAlarmTarget("tel:+123456789"))
        assertEquals("market://details?id=com.example.app", normalizeOtherAlarmTarget("market://details?id=com.example.app"))
        assertNull(normalizeOtherAlarmTarget("intent://open#Intent;scheme=x;end"))
        assertNull(normalizeOtherAlarmTarget("file:///etc/passwd"))
    }

    @Test
    fun oldWeeklyAlarmDecodesToWeeklyRecurrence() {
        val alarm = decodeAlarm("42|6|30|true|1,5|-|true|false")

        assertEquals(AlarmRecurrence.WEEKLY, alarm?.effectiveRecurrence)
    }

    @Test
    fun prayerReminderSettingsToggleCategoriesIndependently() {
        val settings = PrayerReminderSettings()
            .withEnabled(PrayerReminderCategory.FAJR, true)
            .withEnabled(PrayerReminderCategory.FRIDAY, true)
            .withEnabled(PrayerReminderCategory.FAJR, false)

        assertEquals(setOf(PrayerReminderCategory.FRIDAY), settings.enabled)
    }

    @Test
    fun fridayReminderTargetsTheStartOfFridayInTheSelectedZone() {
        val now = ZonedDateTime.of(2026, 1, 1, 20, 0, 0, 0, zone).toInstant()

        val reminder = nextPrayerReminder(
            PrayerReminderCategory.FRIDAY,
            now,
            PrayerSettings(location = PrayerCities.makkah),
        )

        assertEquals("يوم الجمعة", reminder?.title)
        assertEquals(
            ZonedDateTime.of(2026, 1, 1, 21, 0, 0, 0, zone).toInstant(),
            reminder?.at,
        )
    }

    @Test
    fun prayerReminderSelectsTheNextPrayerTime() {
        val now = ZonedDateTime.of(2026, 1, 2, 10, 0, 0, 0, zone).toInstant()

        val reminder = nextPrayerReminder(
            PrayerReminderCategory.FAJR,
            now,
            PrayerSettings(location = PrayerCities.makkah),
        )

        assertEquals(true, reminder?.at?.isAfter(now))
        assertEquals("حان وقت صلاة الفجر", reminder?.title)
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
