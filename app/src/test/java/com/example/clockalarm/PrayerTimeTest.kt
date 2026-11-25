package com.example.clockalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PrayerTimeTest {
    private val makkah = PrayerCities.makkah
    private val zone = ZoneId.of(makkah.zoneId)

    @Test
    fun prayerTimesFollowExpectedSolarOrderAndMeccaReferenceWindow() {
        val schedule = PrayerTimeCalculator.calculate(
            LocalDate.of(2024, 3, 11),
            makkah,
            CalculationMethod.UMM_AL_QURA,
            asrFactor = 1,
            PrayerAdjustments(),
        )
        val times = PrayerName.values().map { schedule.times.getValue(it) }

        assertTrue(times.zipWithNext().all { (first, second) -> first.isBefore(second) })
        val fajrHour = schedule.times.getValue(PrayerName.FAJR).atZone(zone).hour
        val noon = schedule.times.getValue(PrayerName.DHUHR).atZone(zone)
        val sunset = schedule.times.getValue(PrayerName.MAGHRIB).atZone(zone)
        assertTrue("Fajr outside expected Mecca window: $fajrHour", fajrHour in 4..5)
        assertTrue("Dhuhr outside expected local solar-noon window: $noon", noon.hour == 12)
        assertTrue("Sunset outside expected Mecca window: $sunset", sunset.hour in 18..19)
        assertNearMinute(316, schedule.times.getValue(PrayerName.FAJR))
        assertNearMinute(750, schedule.times.getValue(PrayerName.DHUHR))
        assertNearMinute(954, schedule.times.getValue(PrayerName.ASR))
        assertNearMinute(1_108, schedule.times.getValue(PrayerName.MAGHRIB))
        assertNearMinute(1_228, schedule.times.getValue(PrayerName.ISHA))
    }

    @Test
    fun asrShadowFactorTwoIsLaterThanFactorOne() {
        val date = LocalDate.of(2026, 1, 15)
        val standard = PrayerTimeCalculator.calculate(
            date, makkah, CalculationMethod.UMM_AL_QURA, 1, PrayerAdjustments(),
        )
        val hanafi = PrayerTimeCalculator.calculate(
            date, makkah, CalculationMethod.UMM_AL_QURA, 2, PrayerAdjustments(),
        )

        assertTrue(
            hanafi.times.getValue(PrayerName.ASR).isAfter(
                standard.times.getValue(PrayerName.ASR),
            ),
        )
    }

    @Test
    fun manualPrayerAdjustmentMovesOnlySelectedPrayerByOneMinute() {
        val date = LocalDate.of(2026, 1, 15)
        val original = PrayerTimeCalculator.calculate(
            date, makkah, CalculationMethod.MUSLIM_WORLD_LEAGUE, 1, PrayerAdjustments(),
        )
        val adjusted = PrayerTimeCalculator.calculate(
            date,
            makkah,
            CalculationMethod.MUSLIM_WORLD_LEAGUE,
            1,
            PrayerAdjustments().change(PrayerName.FAJR, 1),
        )

        assertEquals(
            60L,
            Duration.between(
                original.times.getValue(PrayerName.FAJR),
                adjusted.times.getValue(PrayerName.FAJR),
            ).seconds,
        )
        assertEquals(
            original.times.getValue(PrayerName.DHUHR),
            adjusted.times.getValue(PrayerName.DHUHR),
        )
    }

    @Test
    fun ummAlQuraIshaUsesTwoHoursDuringRamadan() {
        val schedule = PrayerTimeCalculator.calculate(
            LocalDate.of(2024, 3, 11),
            makkah,
            CalculationMethod.UMM_AL_QURA,
            1,
            PrayerAdjustments(),
        )

        assertEquals(
            120L,
            Duration.between(
                schedule.times.getValue(PrayerName.MAGHRIB),
                schedule.times.getValue(PrayerName.ISHA),
            ).toMinutes(),
        )
    }

    @Test
    fun legalNightMidnightAndLastThirdUseSunsetToFollowingFajr() {
        val schedule = PrayerTimeCalculator.calculate(
            LocalDate.of(2026, 1, 15),
            makkah,
            CalculationMethod.MUSLIM_WORLD_LEAGUE,
            1,
            PrayerAdjustments(),
        )
        val nextFajr = PrayerTimeCalculator.calculate(
            LocalDate.of(2026, 1, 16),
            makkah,
            CalculationMethod.MUSLIM_WORLD_LEAGUE,
            1,
            PrayerAdjustments(),
        ).times.getValue(PrayerName.FAJR)
        val nightLength = Duration.between(schedule.sunset, nextFajr)

        assertEquals(
            nightLength.toNanos() / 2,
            Duration.between(schedule.sunset, schedule.midnight).toNanos(),
        )
        assertEquals(
            nightLength.toNanos() * 2 / 3,
            Duration.between(schedule.sunset, schedule.lastThird).toNanos(),
        )
        assertTrue(schedule.midnight.isBefore(schedule.lastThird))
        assertTrue(schedule.lastThird.isBefore(nextFajr))
    }

    @Test
    fun nextPrayerFindsNextDayFajrAfterIsha() {
        val now = Instant.parse("2026-01-15T20:30:00Z")
        val next = PrayerTimeCalculator.nextPrayer(
            now, makkah, CalculationMethod.UMM_AL_QURA, 1, PrayerAdjustments(),
        )

        assertEquals("الصلاة القادمة: الفجر", next.title)
        assertTrue(next.at.isAfter(now))
    }

    @Test
    fun hijriOccasionsUseUmmAlQuraDatesAndIdentifyObservances() {
        val beforeRamadan = Instant.parse("2024-03-10T12:00:00Z")
        val ramadan = IslamicEvents.nextHijriDate(
            beforeRamadan, makkah, 9, 1, "بداية رمضان",
        )

        assertEquals(Instant.parse("2024-03-10T21:00:00Z"), ramadan.at)
        assertTrue("رمضان" in IslamicEvents.observances(LocalDate.of(2024, 3, 11)))
        assertTrue("يوم عرفة" in IslamicEvents.observances(LocalDate.of(2024, 6, 15)))
        assertTrue("عيد الفطر" in IslamicEvents.observances(LocalDate.of(2024, 4, 10)))
        assertTrue("عيد الأضحى" in IslamicEvents.observances(LocalDate.of(2024, 6, 16)))
        assertTrue("يوم الجمعة" in IslamicEvents.observances(LocalDate.of(2024, 3, 15)))
    }

    @Test
    fun countdownReportsSecondsAndNeverGoesNegative() {
        val now = Instant.parse("2026-01-01T00:00:00Z")

        assertEquals(90L, remainingCountdownSeconds(now, now.plusSeconds(90)))
        assertEquals(0L, remainingCountdownSeconds(now, now.minusSeconds(1)))
    }

    @Test
    fun seasonDatesMatchMarchEquinoxAndReverseInSouthernHemisphere() {
        val marchEquinox = SeasonCalculator.starts(2024).getValue(Season.SPRING)
        val knownEquinox = Instant.parse("2024-03-20T03:06:00Z")

        assertTrue(Duration.between(marchEquinox, knownEquinox).abs().toMinutes() < 5)
        assertEquals(
            Season.SPRING,
            SeasonCalculator.current(Instant.parse("2024-03-21T12:00:00Z"), southernHemisphere = false),
        )
        assertEquals(
            Season.AUTUMN,
            SeasonCalculator.current(Instant.parse("2024-03-21T12:00:00Z"), southernHemisphere = true),
        )
        assertEquals(
            4,
            SeasonCalculator.nextStarts(Instant.parse("2024-01-01T00:00:00Z"), false).size,
        )
    }

    @Test
    fun prayerCalculatorRejectsInvalidCoordinatesAndPolarSunlessDay() {
        val invalidCoordinates = PrayerLocation("bad", 91.0, 0.0, "UTC")
        val polarCity = PrayerLocation("polar", 89.0, 0.0, "UTC")

        assertThrows(IllegalArgumentException::class.java) {
            PrayerTimeCalculator.calculate(
                LocalDate.of(2026, 6, 21),
                invalidCoordinates,
                CalculationMethod.UMM_AL_QURA,
                1,
                PrayerAdjustments(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTimeCalculator.calculate(
                LocalDate.of(2026, 6, 21),
                polarCity,
                CalculationMethod.UMM_AL_QURA,
                1,
                PrayerAdjustments(),
            )
        }
    }

    private fun assertNearMinute(expected: Int, actual: Instant) {
        val local = actual.atZone(zone)
        val actualMinute = local.hour * 60 + local.minute
        assertTrue(
            "Expected minute $expected, got $actualMinute",
            kotlin.math.abs(expected - actualMinute) <= 2,
        )
    }
}
