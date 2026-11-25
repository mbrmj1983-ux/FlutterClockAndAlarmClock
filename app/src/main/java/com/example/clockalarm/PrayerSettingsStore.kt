package com.example.clockalarm

import android.content.Context
import android.util.Log
import java.time.DateTimeException
import java.time.ZoneId

internal data class PrayerSettings(
    val location: PrayerLocation = PrayerCities.makkah,
    val method: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val asrFactor: Int = 1,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
)

internal object PrayerCities {
    val makkah = PrayerLocation("مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh")
    val all = listOf(
        makkah,
        PrayerLocation("المدينة المنورة", 24.5247, 39.5692, "Asia/Riyadh"),
        PrayerLocation("الرياض", 24.7136, 46.6753, "Asia/Riyadh"),
        PrayerLocation("جدة", 21.4858, 39.1925, "Asia/Riyadh"),
        PrayerLocation("القاهرة", 30.0444, 31.2357, "Africa/Cairo"),
        PrayerLocation("عمّان", 31.9539, 35.9106, "Asia/Amman"),
        PrayerLocation("إسطنبول", 41.0082, 28.9784, "Europe/Istanbul"),
        PrayerLocation("لندن", 51.5072, -0.1276, "Europe/London"),
        PrayerLocation("نيويورك", 40.7128, -74.0060, "America/New_York"),
        PrayerLocation("كراتشي", 24.8607, 67.0011, "Asia/Karachi"),
    )
}

internal object PrayerSettingsStore {
    private const val PREFERENCES = "prayer_settings"

    fun load(context: Context): PrayerSettings {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val location = readLocation(preferences) ?: PrayerCities.makkah
        val savedMethod = preferences.getString("calculation_method", null)
        val method = CalculationMethod.values().firstOrNull { it.name == savedMethod }
            ?: CalculationMethod.UMM_AL_QURA.also {
                if (savedMethod != null) {
                    Log.w("PrayerSettingsStore", "Saved prayer calculation method is invalid")
                }
            }
        val savedAsrFactor = preferences.getInt("asr_factor", 1)
        val asrFactor = savedAsrFactor.let { if (it == 2) 2 else 1 }.also {
            if (savedAsrFactor != 1 && savedAsrFactor != 2) {
                Log.w("PrayerSettingsStore", "Saved Asr factor is invalid")
            }
        }
        val adjustments = PrayerAdjustments(
            PrayerName.entries.associateWith { prayer ->
                preferences.getInt("adjustment_${prayer.name}", 0).coerceIn(-60, 60)
            },
        )
        return PrayerSettings(location, method, asrFactor, adjustments)
    }

    private fun readLocation(preferences: android.content.SharedPreferences): PrayerLocation? {
        val name = preferences.getString("location_name", null) ?: return null
        val latitude = preferences.getString("latitude", null) ?: return null
        val longitude = preferences.getString("longitude", null) ?: return null
        val zoneId = preferences.getString("zone_id", null) ?: return null
        return try {
            val location = PrayerLocation(name, latitude.toDouble(), longitude.toDouble(), zoneId)
            if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
                Log.w("PrayerSettingsStore", "Saved prayer location coordinates are out of range")
                return null
            }
            ZoneId.of(location.zoneId)
            location
        } catch (exception: NumberFormatException) {
            Log.w("PrayerSettingsStore", "Saved prayer location has invalid coordinates", exception)
            null
        } catch (exception: DateTimeException) {
            Log.w("PrayerSettingsStore", "Saved prayer location has an invalid time zone", exception)
            null
        }
    }

    fun save(context: Context, settings: PrayerSettings) {
        val editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("location_name", settings.location.name)
            .putString("latitude", settings.location.latitude.toString())
            .putString("longitude", settings.location.longitude.toString())
            .putString("zone_id", settings.location.zoneId)
            .putString("calculation_method", settings.method.name)
            .putInt("asr_factor", settings.asrFactor)
        PrayerName.entries.forEach { prayer ->
            editor.putInt("adjustment_${prayer.name}", settings.adjustments.forPrayer(prayer))
        }
        editor.apply()
    }
}
