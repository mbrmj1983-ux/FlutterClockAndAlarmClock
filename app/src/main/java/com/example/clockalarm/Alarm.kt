package com.example.clockalarm

import java.time.DayOfWeek
import java.time.LocalDate

enum class AlarmRecurrence {
    MINUTELY,
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    ONCE,
}

enum class AlarmLinkTargetType {
    WEB,
    APP,
    FILE,
    OTHER,
}

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val date: LocalDate? = null,
    val visualEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val recurrence: AlarmRecurrence? = null,
    val label: String? = null,
    val linkUrl: String? = null,
    val linkLabel: String? = null,
    val linkButtonColor: Long? = null,
    val linkTargetType: AlarmLinkTargetType? = null,
    val linkTarget: String? = null,
)

internal val Alarm.effectiveRecurrence: AlarmRecurrence
    get() = recurrence ?: when {
        date != null -> AlarmRecurrence.ONCE
        repeatDays.isEmpty() -> AlarmRecurrence.ONCE
        repeatDays.size == DayOfWeek.values().size -> AlarmRecurrence.DAILY
        else -> AlarmRecurrence.WEEKLY
    }
