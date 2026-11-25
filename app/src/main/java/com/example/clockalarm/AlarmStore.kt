package com.example.clockalarm

import android.content.Context

object AlarmStore {
    private const val PREFERENCES_NAME = "alarms"
    private const val ALARMS_KEY = "daily_alarms"

    fun load(context: Context): List<Alarm> =
        preferences(context).getStringSet(ALARMS_KEY, emptySet())
            .orEmpty()
            .mapNotNull { encoded ->
                val parts = encoded.split('|')
                if (parts.size != 3) return@mapNotNull null
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                val hour = parts[1].toIntOrNull() ?: return@mapNotNull null
                val minute = parts[2].toIntOrNull() ?: return@mapNotNull null
                if (hour !in 0..23 || minute !in 0..59) return@mapNotNull null
                Alarm(id, hour, minute)
            }
            .sortedWith(compareBy(Alarm::hour, Alarm::minute))

    fun save(context: Context, alarms: List<Alarm>) {
        val encoded = alarms.mapTo(mutableSetOf()) { "${it.id}|${it.hour}|${it.minute}" }
        preferences(context).edit().putStringSet(ALARMS_KEY, encoded).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
