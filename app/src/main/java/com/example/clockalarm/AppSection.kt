package com.example.clockalarm

internal enum class AppSection(
    val route: String,
    val label: String,
    val glyph: String,
) {
    CLOCK("clock", "الساعة", "◷"),
    PRAYER_TIMES("prayers", "المواقيت", "☾"),
    WORLD_CLOCK("world_clock", "العالمية", "◎"),
    ALARMS_SETTINGS("alarms_settings", "المنبّهات", "⚙");

    companion object {
        fun fromRoute(route: String?): AppSection =
            values().firstOrNull { it.route == route } ?: CLOCK
    }
}

internal data class BottomNavigationState(val selected: AppSection = AppSection.CLOCK) {
    fun select(section: AppSection): BottomNavigationState = copy(selected = section)
}
