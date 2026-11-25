package com.example.clockalarm

import android.content.Context
import android.util.Log
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WorldClockCity(
    val name: String,
    val zoneId: String,
)

internal data class WorldClockReading(
    val city: WorldClockCity,
    val localTime: ZonedDateTime,
    val utcOffsetSeconds: Int,
    val differenceFromDeviceSeconds: Int,
)

internal object WorldClockZones {
    val cities = listOf(
        WorldClockCity("مكة المكرمة", "Asia/Riyadh"),
        WorldClockCity("القاهرة", "Africa/Cairo"),
        WorldClockCity("عمّان", "Asia/Amman"),
        WorldClockCity("إسطنبول", "Europe/Istanbul"),
        WorldClockCity("لندن", "Europe/London"),
        WorldClockCity("باريس", "Europe/Paris"),
        WorldClockCity("نيويورك", "America/New_York"),
        WorldClockCity("لوس أنجلوس", "America/Los_Angeles"),
        WorldClockCity("كراتشي", "Asia/Karachi"),
        WorldClockCity("دلهي", "Asia/Kolkata"),
        WorldClockCity("طوكيو", "Asia/Tokyo"),
        WorldClockCity("سيدني", "Australia/Sydney"),
        WorldClockCity("أوكلاند", "Pacific/Auckland"),
        WorldClockCity("UTC", "UTC"),
    )

    val defaults = listOf("Asia/Riyadh", "Europe/London", "America/New_York")

    fun city(zoneId: String): WorldClockCity? = cities.firstOrNull { it.zoneId == zoneId }
}

internal object WorldClock {
    fun reading(
        now: Instant,
        city: WorldClockCity,
        deviceZoneId: String,
    ): WorldClockReading {
        val cityZone = ZoneId.of(city.zoneId)
        val deviceZone = ZoneId.of(deviceZoneId)
        val cityTime = now.atZone(cityZone)
        return WorldClockReading(
            city = city,
            localTime = cityTime,
            utcOffsetSeconds = cityTime.offset.totalSeconds,
            differenceFromDeviceSeconds =
                cityTime.offset.totalSeconds - now.atZone(deviceZone).offset.totalSeconds,
        )
    }

    fun formatOffset(seconds: Int): String {
        val sign = if (seconds < 0) "−" else "+"
        val absoluteMinutes = kotlin.math.abs(seconds) / 60
        return "UTC$sign%02d:%02d".format(
            absoluteMinutes / 60,
            absoluteMinutes % 60,
        )
    }

    fun formatDeviceDifference(seconds: Int): String {
        if (seconds == 0) return "مثل توقيت الجهاز"
        val absoluteMinutes = kotlin.math.abs(seconds) / 60
        val hours = absoluteMinutes / 60
        val minutes = absoluteMinutes % 60
        val difference = buildString {
            if (hours > 0) append(formatAmount(hours, "ساعة", "ساعتان", "ساعات"))
            if (minutes > 0) {
                if (isNotEmpty()) append(" و")
                append(formatAmount(minutes, "دقيقة", "دقيقتان", "دقائق"))
            }
        }
        return if (seconds > 0) "متقدم عن الجهاز بـ $difference" else "متأخر عن الجهاز بـ $difference"
    }

    private fun formatAmount(value: Int, singular: String, dual: String, plural: String): String =
        when (value) {
            1 -> "$singular واحدة"
            2 -> dual
            in 3..10 -> "$value $plural"
            else -> "$value $singular"
        }

    fun formatTime(now: Instant, city: WorldClockCity): String =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.forLanguageTag("ar"))
            .format(now.atZone(ZoneId.of(city.zoneId)))

    fun formatDate(now: Instant, city: WorldClockCity): String =
        DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar"))
            .format(now.atZone(ZoneId.of(city.zoneId)))
}

internal object WorldClockStore {
    private const val PREFERENCES = "world_clock_settings"
    private const val CITY_ZONES = "city_zones"

    fun load(context: Context): List<WorldClockCity> {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val saved = preferences.getStringSet(CITY_ZONES, null)
        val zoneIds = saved ?: WorldClockZones.defaults.toSet()
        return zoneIds.mapNotNull { zoneId ->
            WorldClockZones.city(zoneId) ?: run {
                try {
                    ZoneId.of(zoneId)
                    Log.w("WorldClockStore", "Ignoring unrecognized saved world-clock zone: $zoneId")
                } catch (exception: DateTimeException) {
                    Log.w("WorldClockStore", "Ignoring invalid saved world-clock zone", exception)
                }
                null
            }
        }.distinctBy(WorldClockCity::zoneId).sortedBy { city ->
            WorldClockZones.cities.indexOfFirst { it.zoneId == city.zoneId }
        }
    }

    fun save(context: Context, cities: List<WorldClockCity>) {
        val allowedZones = WorldClockZones.cities.mapTo(mutableSetOf(), WorldClockCity::zoneId)
        require(cities.all { it.zoneId in allowedZones }) {
            "World-clock cities must use a supported time zone"
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(CITY_ZONES, cities.mapTo(mutableSetOf(), WorldClockCity::zoneId))
            .apply()
    }
}
