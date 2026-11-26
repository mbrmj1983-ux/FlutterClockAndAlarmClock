package com.example.clockalarm

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64

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
    if (parts.size !in setOf(3, 6, 8, 9, 10, 12, 13, 15)) return null
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
    val visualEnabled = if (parts.size in setOf(8, 9, 10, 12, 13, 15)) {
        parts[6].toBooleanStrictOrNull() ?: return null
    } else {
        true
    }
    val soundEnabled = if (parts.size in setOf(8, 9, 10, 12, 13, 15)) {
        parts[7].toBooleanStrictOrNull() ?: return null
    } else {
        true
    }
    val recurrence = if (parts.size in setOf(9, 10, 12, 13, 15) && parts[8].isNotEmpty()) {
        try {
            AlarmRecurrence.valueOf(parts[8])
        } catch (_: IllegalArgumentException) {
            return null
        }
    } else {
        null
    }
    val decodedLabel = decodeOptionalText(parts.getOrNull(9)) ?: return null
    val decodedLinkUrl = decodeOptionalText(parts.getOrNull(10)) ?: return null
    val decodedLinkLabel = decodeOptionalText(parts.getOrNull(11)) ?: return null
    val linkButtonColor = parts.getOrNull(12)?.takeIf(String::isNotEmpty)?.let {
        it.toLongOrNull() ?: return null
    }
    val linkTargetType = parts.getOrNull(13)?.takeIf(String::isNotEmpty)?.let {
        try {
            AlarmLinkTargetType.valueOf(it)
        } catch (_: IllegalArgumentException) {
            return null
        }
    }
    val linkTarget = decodeOptionalText(parts.getOrNull(14))?.value
    val label = decodedLabel.value
    val rawLinkUrl = decodedLinkUrl.value
    val linkUrl = when (linkTargetType) {
        AlarmLinkTargetType.OTHER -> rawLinkUrl?.let(::normalizeOtherAlarmTarget)
        else -> rawLinkUrl?.let(::normalizeWebUrl)
    }
    if (rawLinkUrl != null && linkUrl == null) return null
    if (linkTargetType in setOf(AlarmLinkTargetType.APP, AlarmLinkTargetType.FILE) && linkTarget == null) {
        return null
    }
    if (
        linkTargetType == AlarmLinkTargetType.APP &&
        linkTarget?.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) != true
    ) return null
    if (linkTargetType == AlarmLinkTargetType.FILE && !linkTarget.orEmpty().startsWith("content://")) {
        return null
    }
    val linkLabel = decodedLinkLabel.value
    return Alarm(
        id,
        hour,
        minute,
        enabled,
        repeatDays,
        date,
        visualEnabled,
        soundEnabled,
        recurrence,
        label,
        linkUrl,
        linkLabel,
        linkButtonColor,
        linkTargetType,
        linkTarget,
    )
}

internal fun encodeAlarm(alarm: Alarm): String {
    val days = alarm.repeatDays.sortedBy { it.value }.joinToString(",") {
        it.value.toString()
    }
    return listOf(
        alarm.id.toString(),
        alarm.hour.toString(),
        alarm.minute.toString(),
        alarm.enabled.toString(),
        days,
        alarm.date?.toString() ?: "-",
        alarm.visualEnabled.toString(),
        alarm.soundEnabled.toString(),
        alarm.recurrence?.name.orEmpty(),
        encodeOptionalText(alarm.label),
        encodeOptionalText(
            when (alarm.linkTargetType) {
                AlarmLinkTargetType.OTHER -> alarm.linkUrl?.let(::normalizeOtherAlarmTarget)
                else -> alarm.linkUrl?.let(::normalizeWebUrl)
            },
        ),
        encodeOptionalText(alarm.linkLabel),
        alarm.linkButtonColor?.toString().orEmpty(),
        alarm.linkTargetType?.name.orEmpty(),
        encodeOptionalText(alarm.linkTarget),
    ).joinToString("|")
}

private fun encodeOptionalText(value: String?): String =
    value?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) }
        .orEmpty()
        .ifEmpty { "!" }

private data class DecodedText(val value: String?)

private fun decodeOptionalText(value: String?): DecodedText? {
    if (value.isNullOrEmpty() || value == "!") return DecodedText(null)
    return try {
        DecodedText(
            Base64.getUrlDecoder().decode(value).toString(Charsets.UTF_8).takeIf(String::isNotBlank),
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
