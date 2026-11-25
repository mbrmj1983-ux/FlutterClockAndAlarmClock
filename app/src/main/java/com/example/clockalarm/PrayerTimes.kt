package com.example.clockalarm

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.floor

internal data class PrayerLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
)

internal enum class PrayerName(val arabic: String) {
    FAJR("الفجر"),
    DHUHR("الظهر"),
    ASR("العصر"),
    MAGHRIB("المغرب"),
    ISHA("العشاء"),
}

internal enum class CalculationMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ummAlQuraIshaMinutes: Int? = null,
) {
    MUSLIM_WORLD_LEAGUE("رابطة العالم الإسلامي", 18.0, 17.0),
    EGYPTIAN("الهيئة المصرية", 19.5, 17.5),
    KARACHI("كراتشي", 18.0, 18.0),
    UMM_AL_QURA("أم القرى", 18.5, 0.0, 90),
    NORTH_AMERICA("أمريكا الشمالية (ISNA)", 15.0, 15.0),
}

internal data class PrayerAdjustments(
    val minutes: Map<PrayerName, Int> = emptyMap(),
) {
    fun forPrayer(prayer: PrayerName): Int = minutes[prayer] ?: 0

    fun change(prayer: PrayerName, delta: Int): PrayerAdjustments {
        val updated = (forPrayer(prayer) + delta).coerceIn(-60, 60)
        return copy(minutes = minutes + (prayer to updated))
    }
}

internal data class PrayerSchedule(
    val times: Map<PrayerName, Instant>,
    val sunset: Instant,
    val midnight: Instant,
    val lastThird: Instant,
)

internal data class CountdownEntry(
    val title: String,
    val at: Instant,
    val subtitle: String? = null,
)

internal fun remainingCountdownSeconds(now: Instant, at: Instant): Long =
    Duration.between(now, at).seconds.coerceAtLeast(0)

internal enum class Season(val arabic: String) {
    SPRING("الربيع"),
    SUMMER("الصيف"),
    AUTUMN("الخريف"),
    WINTER("الشتاء"),
}

internal object PrayerTimeCalculator {
    fun calculate(
        date: LocalDate,
        location: PrayerLocation,
        method: CalculationMethod,
        asrFactor: Int,
        adjustments: PrayerAdjustments,
    ): PrayerSchedule {
        require(location.latitude in -90.0..90.0)
        require(location.longitude in -180.0..180.0)
        require(asrFactor == 1 || asrFactor == 2)
        val todaySun = solarDay(date, location)
        val tomorrowSun = solarDay(date.plusDays(1), location)
        val hijriMonth = HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR)
        val ishaMinutes = method.ummAlQuraIshaMinutes?.let {
            if (hijriMonth == 9) 120 else it
        }

        val asrAltitude = Math.toDegrees(
            atan(
                1.0 / (
                    asrFactor + tan(
                        Math.toRadians(abs(location.latitude - todaySun.declinationDegrees)),
                    )
                ),
            ),
        )
        val raw = linkedMapOf(
            PrayerName.FAJR to todaySun.atAltitude(-method.fajrAngle, beforeNoon = true),
            PrayerName.DHUHR to todaySun.noon,
            PrayerName.ASR to todaySun.atAltitude(asrAltitude, beforeNoon = false),
            PrayerName.MAGHRIB to todaySun.sunset,
            PrayerName.ISHA to if (ishaMinutes != null) {
                todaySun.sunset.plusSeconds(ishaMinutes * 60L)
            } else {
                todaySun.atAltitude(-method.ishaAngle, beforeNoon = false)
            },
        )
        val adjusted = raw.mapValues { (prayer, instant) ->
            instant.plusSeconds(adjustments.forPrayer(prayer) * 60L)
        }
        val sunset = adjusted.getValue(PrayerName.MAGHRIB)
        val nextFajr = tomorrowSun.atAltitude(-method.fajrAngle, beforeNoon = true)
            .plusSeconds(adjustments.forPrayer(PrayerName.FAJR) * 60L)
        require(nextFajr.isAfter(sunset)) { "Sunset and next Fajr could not be calculated" }
        val nightLength = Duration.between(sunset, nextFajr)
        return PrayerSchedule(
            times = adjusted,
            sunset = sunset,
            midnight = sunset.plusNanos(nightLength.toNanos() / 2),
            lastThird = sunset.plusNanos(nightLength.toNanos() * 2 / 3),
        )
    }

    fun nextPrayer(
        now: Instant,
        location: PrayerLocation,
        method: CalculationMethod,
        asrFactor: Int,
        adjustments: PrayerAdjustments,
    ): CountdownEntry {
        val localDate = now.atZone(ZoneId.of(location.zoneId)).toLocalDate()
        val today = calculate(localDate, location, method, asrFactor, adjustments)
        val next = today.times.entries
            .filter { it.value.isAfter(now) }
            .minByOrNull { it.value }
        if (next != null) return CountdownEntry("الصلاة القادمة: ${next.key.arabic}", next.value)

        val tomorrow = calculate(localDate.plusDays(1), location, method, asrFactor, adjustments)
        return CountdownEntry(
            "الصلاة القادمة: ${PrayerName.FAJR.arabic}",
            tomorrow.times.getValue(PrayerName.FAJR),
        )
    }

    private data class SolarDay(
        val noon: Instant,
        val declinationDegrees: Double,
        val latitudeDegrees: Double,
        val sunset: Instant,
    ) {
        fun atAltitude(altitudeDegrees: Double, beforeNoon: Boolean): Instant {
            val latitude = Math.toRadians(latitudeDegrees)
            val declination = Math.toRadians(declinationDegrees)
            val altitude = Math.toRadians(altitudeDegrees)
            val cosineHourAngle =
                (sin(altitude) - sin(latitude) * sin(declination)) /
                    (cos(latitude) * cos(declination))
            require(cosineHourAngle in -1.0..1.0) {
                "Solar event is not available at this latitude and date"
            }
            val hourAngleDegrees = Math.toDegrees(acos(cosineHourAngle))
            val utcOffsetHours = (hourAngleDegrees * 4.0) / 60.0
            val nanos = (utcOffsetHours * 3_600_000_000_000.0).toLong()
            return if (beforeNoon) noon.minusNanos(nanos) else noon.plusNanos(nanos)
        }
    }

    private fun solarDay(date: LocalDate, location: PrayerLocation): SolarDay {
        val julianDay = date.toEpochDay() + 2_440_587.5 + 0.5
        val century = (julianDay - 2_451_545.0) / 36_525.0
        val geomMeanLong = normalizeDegrees(
            280.46646 + century * (36_000.76983 + century * 0.0003032),
        )
        val geomMeanAnomaly = Math.toRadians(
            357.52911 + century * (35_999.05029 - 0.0001537 * century),
        )
        val eccentricity = 0.016708634 - century * (0.000042037 + 0.0000001267 * century)
        val equationOfCenter = sin(geomMeanAnomaly) *
            (1.914602 - century * (0.004817 + 0.000014 * century)) +
            sin(2 * geomMeanAnomaly) * (0.019993 - 0.000101 * century) +
            sin(3 * geomMeanAnomaly) * 0.000289
        val trueLong = geomMeanLong + equationOfCenter
        val omega = Math.toRadians(125.04 - 1934.136 * century)
        val apparentLong = Math.toRadians(trueLong - 0.00569 - 0.00478 * sin(omega))
        val meanObliquity = 23.0 + (
            26.0 + (
                21.448 - century * (
                    46.815 + century * (0.00059 - century * 0.001813)
                )
            ) / 60.0
        ) / 60.0
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        val declination = Math.toDegrees(asin(sin(obliquity) * sin(apparentLong)))
        val y = tan(obliquity / 2).let { it * it }
        val equationOfTime = 4.0 * Math.toDegrees(
            y * sin(2 * Math.toRadians(geomMeanLong)) -
                2 * eccentricity * sin(geomMeanAnomaly) +
                4 * eccentricity * y * sin(geomMeanAnomaly) *
                cos(2 * Math.toRadians(geomMeanLong)) -
                0.5 * y * y * sin(4 * Math.toRadians(geomMeanLong)) -
                1.25 * eccentricity * eccentricity * sin(2 * geomMeanAnomaly),
        )
        val noonMinutes = 720.0 - 4.0 * location.longitude - equationOfTime
        val noon = date.atStartOfDay(ZoneOffset.UTC).toInstant()
            .plusNanos((noonMinutes * 60_000_000_000.0).toLong())
        val latitude = Math.toRadians(location.latitude)
        val declinationRadians = Math.toRadians(declination)
        val sunsetCosine = (
            sin(Math.toRadians(-0.833)) - sin(latitude) * sin(declinationRadians)
        ) / (cos(latitude) * cos(declinationRadians))
        require(sunsetCosine in -1.0..1.0) { "Sunset is not available at this latitude and date" }
        val sunset = noon.plusNanos(
            (Math.toDegrees(acos(sunsetCosine)) * 240.0 * 1_000_000_000.0).toLong(),
        )
        return SolarDay(noon, declination, location.latitude, sunset)
    }

    private fun normalizeDegrees(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    private fun asin(value: Double): Double = kotlin.math.asin(value)

}

internal object IslamicEvents {
    fun observances(date: LocalDate): List<String> {
        val hijri = HijrahDate.from(date)
        val month = hijri.get(ChronoField.MONTH_OF_YEAR)
        val day = hijri.get(ChronoField.DAY_OF_MONTH)
        return buildList {
            if (date.dayOfWeek == java.time.DayOfWeek.FRIDAY) add("يوم الجمعة")
            if (month == 9) add("رمضان")
            if (month == 12 && day == 9) add("يوم عرفة")
            if (month == 10 && day == 1) add("عيد الفطر")
            if (month == 12 && day == 10) add("عيد الأضحى")
        }
    }

    fun nextHijriDate(
        now: Instant,
        location: PrayerLocation,
        month: Int,
        day: Int,
        title: String,
    ): CountdownEntry {
        val zone = ZoneId.of(location.zoneId)
        val firstDate = now.atZone(zone).toLocalDate()
        for (offset in 0..400) {
            val date = firstDate.plusDays(offset.toLong())
            val hijri = HijrahDate.from(date)
            if (
                hijri.get(ChronoField.MONTH_OF_YEAR) == month &&
                hijri.get(ChronoField.DAY_OF_MONTH) == day
            ) {
                val at = date.atStartOfDay(zone).toInstant()
                if (at.isAfter(now)) return CountdownEntry(title, at)
            }
        }
        error("Unable to find the next Hijri date")
    }

    fun nextFriday(now: Instant, location: PrayerLocation): CountdownEntry {
        val zone = ZoneId.of(location.zoneId)
        var date = now.atZone(zone).toLocalDate()
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY))
        var at = date.atStartOfDay(zone).toInstant()
        if (!at.isAfter(now)) {
            date = date.plusWeeks(1)
            at = date.atStartOfDay(zone).toInstant()
        }
        return CountdownEntry("يوم الجمعة", at)
    }
}

internal object SeasonCalculator {
    fun starts(year: Int): Map<Season, Instant> = mapOf(
        Season.SPRING to julianEphemerisToInstant(seasonJde(year, 0)),
        Season.SUMMER to julianEphemerisToInstant(seasonJde(year, 1)),
        Season.AUTUMN to julianEphemerisToInstant(seasonJde(year, 2)),
        Season.WINTER to julianEphemerisToInstant(seasonJde(year, 3)),
    )

    fun current(now: Instant, southernHemisphere: Boolean): Season {
        val year = now.atZone(ZoneOffset.UTC).year
        val boundaries = (year - 1..year + 1)
            .flatMap { season -> starts(season).map { it.value to it.key } }
            .filter { it.first <= now }
            .maxBy { it.first }
            .second
        return if (southernHemisphere) boundaries.opposite() else boundaries
    }

    fun nextStarts(
        now: Instant,
        southernHemisphere: Boolean,
    ): List<CountdownEntry> {
        val utcYear = now.atZone(ZoneOffset.UTC).year
        val seasons = Season.values().associateWith { mutableListOf<Instant>() }
        for (year in utcYear..utcYear + 1) {
            starts(year).forEach { (northernSeason, instant) ->
                val season = if (southernHemisphere) northernSeason.opposite() else northernSeason
                if (instant.isAfter(now)) seasons.getValue(season).add(instant)
            }
        }
        return seasons.map { (season, dates) ->
            CountdownEntry("بداية ${season.arabic}", dates.minOrNull() ?: error("No future season"))
        }.sortedBy(CountdownEntry::at)
    }

    private fun Season.opposite(): Season = when (this) {
        Season.SPRING -> Season.AUTUMN
        Season.SUMMER -> Season.WINTER
        Season.AUTUMN -> Season.SPRING
        Season.WINTER -> Season.SUMMER
    }

    private fun seasonJde(year: Int, season: Int): Double {
        val y = (year - 2000) / 1000.0
        val coefficients = when (season) {
            0 -> doubleArrayOf(2_451_623.80984, 365242.37404, 0.05169, -0.00411, -0.00057)
            1 -> doubleArrayOf(2_451_716.56767, 365241.62603, 0.00325, 0.00888, -0.00030)
            2 -> doubleArrayOf(2_451_810.21715, 365242.01767, -0.11575, 0.00337, 0.00078)
            else -> doubleArrayOf(2_451_900.05952, 365242.74049, -0.06223, -0.00823, 0.00032)
        }
        return coefficients[0] + y * (
            coefficients[1] + y * (
                coefficients[2] + y * (coefficients[3] + y * coefficients[4])
            )
        )
    }

    private fun julianEphemerisToInstant(jde: Double): Instant {
        val unixDays = jde - 2_440_587.5
        val seconds = ((unixDays - floor(unixDays)) * 86_400.0).toLong()
        return Instant.ofEpochSecond(
            (floor(unixDays) * 86_400.0).toLong() + seconds - 69,
        )
    }
}
