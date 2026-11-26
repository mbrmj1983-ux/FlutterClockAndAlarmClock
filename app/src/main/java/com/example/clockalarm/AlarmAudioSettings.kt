package com.example.clockalarm

import android.content.Context

internal object AlarmAudioSettings {
    private const val PREFERENCES = "alarm_audio"
    private const val BYPASS_DND_KEY = "bypass_dnd"

    fun bypassDndEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(BYPASS_DND_KEY, false)

    fun setBypassDndEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BYPASS_DND_KEY, enabled)
            .apply()
    }
}
