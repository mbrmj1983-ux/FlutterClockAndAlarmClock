package com.example.clockalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun bottomNavigationSelectsEachMainSectionAndKeepsTheSelection() {
        val selected = BottomNavigationState()
            .select(AppSection.PRAYER_TIMES)
            .select(AppSection.WORLD_CLOCK)

        assertEquals(AppSection.WORLD_CLOCK, selected.selected)
        assertEquals(AppSection.WORLD_CLOCK, selected.select(AppSection.WORLD_CLOCK).selected)
        assertEquals(
            listOf(
                AppSection.CLOCK,
                AppSection.PRAYER_TIMES,
                AppSection.WORLD_CLOCK,
                AppSection.ALARMS_SETTINGS,
            ),
            AppSection.values().toList(),
        )
    }

    @Test
    fun navigationRestoresKnownRouteAndFallsBackForUnknownRoute() {
        assertEquals(AppSection.ALARMS_SETTINGS, AppSection.fromRoute("alarms_settings"))
        assertEquals(AppSection.CLOCK, AppSection.fromRoute(null))
        assertEquals(AppSection.CLOCK, AppSection.fromRoute("unknown"))
    }

    @Test
    fun overlayPermissionStateExplainsStatusAndOnlyRequestsSettingsWhenNeeded() {
        val disabled = OverlayPermissionState(granted = false)
        assertEquals("غير مفعّل", disabled.statusLabel)
        assertEquals("تفعيل الظهور فوق التطبيقات", disabled.actionLabel)
        assertTrue(disabled.shouldOpenSettings(requestedEnabled = true))
        assertFalse(disabled.shouldOpenSettings(requestedEnabled = false))

        val enabled = OverlayPermissionState(granted = true)
        assertEquals("مفعّل", enabled.statusLabel)
        assertEquals("إدارة الإذن", enabled.actionLabel)
        assertTrue(enabled.shouldOpenSettings(requestedEnabled = false))
        assertFalse(enabled.shouldOpenSettings(requestedEnabled = true))
    }

    @Test
    fun overlayWindowToggleRequestsPermissionStartsAndStopsOnlyWhenNeeded() {
        assertEquals(
            OverlayWindowAction.REQUEST_PERMISSION,
            OverlayWindowPolicy.action(requestedEnabled = true, permissionGranted = false, isRunning = false),
        )
        assertEquals(
            OverlayWindowAction.START,
            OverlayWindowPolicy.action(requestedEnabled = true, permissionGranted = true, isRunning = false),
        )
        assertEquals(
            OverlayWindowAction.STOP,
            OverlayWindowPolicy.action(requestedEnabled = false, permissionGranted = true, isRunning = true),
        )
        assertEquals(
            OverlayWindowAction.NO_CHANGE,
            OverlayWindowPolicy.action(requestedEnabled = true, permissionGranted = true, isRunning = true),
        )
    }
}
