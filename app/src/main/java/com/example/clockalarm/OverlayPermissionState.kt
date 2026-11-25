package com.example.clockalarm

internal data class OverlayPermissionState(val granted: Boolean) {
    val statusLabel: String
        get() = if (granted) "مفعّل" else "غير مفعّل"

    val actionLabel: String
        get() = if (granted) "إدارة الإذن" else "تفعيل الظهور فوق التطبيقات"

    fun shouldOpenSettings(requestedEnabled: Boolean): Boolean = requestedEnabled != granted
}

internal enum class OverlayWindowAction {
    START,
    STOP,
    REQUEST_PERMISSION,
    NO_CHANGE,
}

internal object OverlayWindowPolicy {
    fun action(requestedEnabled: Boolean, permissionGranted: Boolean, isRunning: Boolean): OverlayWindowAction =
        when {
            requestedEnabled && !permissionGranted -> OverlayWindowAction.REQUEST_PERMISSION
            requestedEnabled && !isRunning -> OverlayWindowAction.START
            !requestedEnabled && isRunning -> OverlayWindowAction.STOP
            else -> OverlayWindowAction.NO_CHANGE
        }
}
