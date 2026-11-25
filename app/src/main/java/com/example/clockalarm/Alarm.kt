package com.example.clockalarm

import java.time.DayOfWeek
import java.time.LocalDate

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val date: LocalDate? = null,
    val visualEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
)
