package com.example.clockalarm

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

object AlarmStore {
    private const val PREFERENCES_NAME = "alarms"
    private const val ALARMS_KEY = "daily_alarms"

    fun load(context: Context): List<Alarm> =
        preferences(context).getStringSet(ALARMS_KEY, emptySet())
            .orEmpty()
            .mapNotNull(::decodeAlarm)
            .sortedWith(compareBy(Alarm::hour, Alarm::minute))

    fun save(context: Context, alarms: List<Alarm>) {
        val encoded = alarms.mapTo(mutableSetOf(), ::encodeAlarm)
        preferences(context).edit().putStringSet(ALARMS_KEY, encoded).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal fun decodeAlarm(encoded: String): Alarm? {
    val parts = encoded.split('|')
    if (parts.size != 3 && parts.size != 6 && parts.size != 8) return null
    val id = parts[0].toLongOrNull() ?: return null
    val hour = parts[1].toIntOrNull() ?: return null
    val minute = parts[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    if (parts.size == 3) return Alarm(id, hour, minute)

    val enabled = parts[3].toBooleanStrictOrNull() ?: return null
    val repeatDays = if (parts[4].isEmpty()) {
        emptySet()
    } else {
        val values = parts[4].split(',').map { it.toIntOrNull() ?: return null }
        if (values.any { it !in 1..7 } || values.distinct().size != values.size) return null
        values.map { DayOfWeek.of(it) }.toSet()
    }
    val date = if (parts[5].isEmpty() || parts[5] == "-") {
        null
    } else {
        try {
            LocalDate.parse(parts[5])
        } catch (_: DateTimeParseException) {
            return null
        }
    }
    val visualEnabled = if (parts.size == 8) {
        parts[6].toBooleanStrictOrNull() ?: return null
    } else {
        true
    }
    val soundEnabled = if (parts.size == 8) {
        parts[7].toBooleanStrictOrNull() ?: return null
    } else {
        true
    }
    return Alarm(id, hour, minute, enabled, repeatDays, date, visualEnabled, soundEnabled)
}

internal fun encodeAlarm(alarm: Alarm): String {
    val days = alarm.repeatDays.sortedBy { it.value }.joinToString(",") {
        it.value.toString()
    }
    return "${alarm.id}|${alarm.hour}|${alarm.minute}|${alarm.enabled}|$days|${alarm.date ?: "-"}|${alarm.visualEnabled}|${alarm.soundEnabled}"
}
