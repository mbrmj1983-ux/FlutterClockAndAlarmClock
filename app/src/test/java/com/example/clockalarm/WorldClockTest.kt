package com.example.clockalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorldClockTest {
    @Test
    fun allConfiguredWorldClockZonesAreValidAndUnique() {
        val zoneIds = WorldClockZones.cities.map(WorldClockCity::zoneId)

        assertEquals(zoneIds.size, zoneIds.toSet().size)
        zoneIds.forEach { assertTrue(ZoneId.getAvailableZoneIds().contains(it)) }
    }

    @Test
    fun sameInstantIsDisplayedInEachCityWithoutChangingTheInstant() {
        val instant = Instant.parse("2026-01-15T12:00:00Z")
        val london = WorldClock.reading(
            instant,
            WorldClockZones.city("Europe/London")!!,
            "Asia/Riyadh",
        )
        val tokyo = WorldClock.reading(
            instant,
            WorldClockZones.city("Asia/Tokyo")!!,
            "Asia/Riyadh",
        )

        assertEquals(instant, london.localTime.toInstant())
        assertEquals(instant, tokyo.localTime.toInstant())
        assertEquals(12, london.localTime.hour)
        assertEquals(21, tokyo.localTime.hour)
        assertEquals(-10_800, london.differenceFromDeviceSeconds)
        assertEquals(21_600, tokyo.differenceFromDeviceSeconds)
    }

    @Test
    fun utcOffsetsAndDeviceDifferenceRespectQuarterAndHalfHourZones() {
        val instant = Instant.parse("2026-01-15T12:00:00Z")
        val kolkata = WorldClock.reading(
            instant,
            WorldClockZones.city("Asia/Kolkata")!!,
            "UTC",
        )
        val kathmandu = WorldClock.reading(
            instant,
            WorldClockCity("Kathmandu", "Asia/Kathmandu"),
            "UTC",
        )

        assertEquals(19_800, kolkata.utcOffsetSeconds)
        assertEquals(19_800, kolkata.differenceFromDeviceSeconds)
        assertEquals("UTC+05:30", WorldClock.formatOffset(kolkata.utcOffsetSeconds))
        assertEquals(20_700, kathmandu.utcOffsetSeconds)
        assertEquals("متقدم عن الجهاز بـ 5 ساعات و45 دقيقة", WorldClock.formatDeviceDifference(20_700))
        assertEquals("متأخر عن الجهاز بـ 3 ساعات", WorldClock.formatDeviceDifference(-10_800))
        assertEquals("مثل توقيت الجهاز", WorldClock.formatDeviceDifference(0))
    }

    @Test
    fun daylightSavingOffsetIsResolvedForTheInstantNotCached() {
        val london = WorldClockZones.city("Europe/London")!!
        val winter = WorldClock.reading(
            Instant.parse("2026-01-15T12:00:00Z"),
            london,
            "UTC",
        )
        val summer = WorldClock.reading(
            Instant.parse("2026-07-15T12:00:00Z"),
            london,
            "UTC",
        )

        assertEquals(0, winter.utcOffsetSeconds)
        assertEquals(3_600, summer.utcOffsetSeconds)
        assertEquals(12, winter.localTime.hour)
        assertEquals(13, summer.localTime.hour)
    }

    @Test
    fun prayerAndHijriEventCountdownInstantsDoNotDependOnDisplayedWorldZones() {
        val now = Instant.parse("2026-01-15T12:00:00Z")
        val makkah = PrayerCities.makkah
        val prayer = PrayerTimeCalculator.nextPrayer(
            now,
            makkah,
            CalculationMethod.UMM_AL_QURA,
            1,
            PrayerAdjustments(),
        )
        val ramadan = IslamicEvents.nextHijriDate(now, makkah, 9, 1, "بداية رمضان")
        val worldReadings = WorldClockZones.cities.map {
            WorldClock.reading(now, it, "Asia/Riyadh")
        }

        assertEquals(WorldClockZones.cities.size, worldReadings.size)
        assertTrue(worldReadings.all { it.localTime.toInstant() == now })
        assertTrue(prayer.at.isAfter(now))
        assertTrue(ramadan.at.isAfter(now))
        val prayerCountdownSeconds = remainingCountdownSeconds(now, prayer.at)
        val ramadanCountdownSeconds = remainingCountdownSeconds(now, ramadan.at)
        assertTrue(
            worldReadings.all {
                remainingCountdownSeconds(it.localTime.toInstant(), prayer.at) == prayerCountdownSeconds &&
                    remainingCountdownSeconds(it.localTime.toInstant(), ramadan.at) == ramadanCountdownSeconds
            },
        )
        assertEquals(
            prayer.at,
            PrayerTimeCalculator.nextPrayer(
                now,
                makkah.copy(zoneId = "UTC"),
                CalculationMethod.UMM_AL_QURA,
                1,
                PrayerAdjustments(),
            ).at,
        )
    }

    @Test
    fun worldClockDateCanDifferFromPrayerLocationDateAtTheSameInstant() {
        val instant = Instant.parse("2026-01-01T17:30:00Z")
        val tokyo = WorldClock.reading(
            instant,
            WorldClockZones.city("Asia/Tokyo")!!,
            "UTC",
        )
        val losAngeles = WorldClock.reading(
            instant,
            WorldClockZones.city("America/Los_Angeles")!!,
            "UTC",
        )
        val prayerDate = instant.atZone(ZoneId.of(PrayerCities.makkah.zoneId)).toLocalDate()

        assertEquals(LocalDate.of(2026, 1, 2), tokyo.localTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 1, 1), losAngeles.localTime.toLocalDate())
        assertTrue(prayerDate != tokyo.localTime.toLocalDate())
        assertEquals(prayerDate, losAngeles.localTime.toLocalDate())
    }
}
