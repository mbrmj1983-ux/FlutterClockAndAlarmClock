package com.example.clockalarm

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object HijriDateFormatter {
    private val formatter = DateTimeFormatter
        .ofPattern("d MMMM yyyy 'هـ'", Locale.forLanguageTag("ar"))
        .withChronology(HijrahChronology.INSTANCE)

    fun format(date: LocalDate): String = date.format(formatter)
}
