package com.example.clockalarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import java.net.URI
import java.net.URISyntaxException

internal data class LaunchableAlarmApp(val label: String, val packageName: String)

internal val Alarm.actionTargetType: AlarmLinkTargetType?
    get() = linkTargetType ?: if (linkUrl != null) AlarmLinkTargetType.WEB else null

internal fun Alarm.hasActionTarget(): Boolean =
    !linkLabel.isNullOrBlank() && hasAlarmActionTarget(actionTargetType, linkUrl, linkTarget)

internal fun hasAlarmActionTarget(
    type: AlarmLinkTargetType?,
    url: String?,
    target: String?,
): Boolean = when (type ?: if (url != null) AlarmLinkTargetType.WEB else null) {
    AlarmLinkTargetType.WEB -> normalizeWebUrl(url) != null
    AlarmLinkTargetType.OTHER -> normalizeOtherAlarmTarget(url) != null
    AlarmLinkTargetType.APP, AlarmLinkTargetType.FILE -> !target.isNullOrBlank()
    null -> false
}

internal fun normalizeWebUrl(value: String?): String? {
    val input = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val url = if ("://" in input) input else "https://$input"
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return null
    }
    return url.takeIf {
        (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank() &&
            uri.port in -1..65535 &&
            uri.rawUserInfo == null
    }
}

internal fun normalizeOtherAlarmTarget(value: String?): String? {
    val input = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = try {
        URI(input)
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme !in setOf("mailto", "tel", "sms", "smsto", "geo", "market")) return null
    if (uri.rawSchemeSpecificPart.isNullOrBlank() || uri.rawUserInfo != null) return null
    return input
}

internal fun launchableAlarmApps(context: Context): List<LaunchableAlarmApp> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            LaunchableAlarmApp(resolveInfo.loadLabel(context.packageManager).toString(), packageName)
        }
        .distinctBy(LaunchableAlarmApp::packageName)
        .sortedBy { it.label.lowercase() }
}

internal fun alarmDocumentName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf(String::isNotBlank) else null
        }
        ?: uri.lastPathSegment
        ?: context.getString(R.string.selected_alarm_file)

internal fun alarmActionIntent(
    context: Context,
    type: AlarmLinkTargetType?,
    url: String?,
    target: String?,
): Intent? = when (type ?: if (url != null) AlarmLinkTargetType.WEB else null) {
    AlarmLinkTargetType.WEB -> normalizeWebUrl(url)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
    AlarmLinkTargetType.OTHER ->
        normalizeOtherAlarmTarget(url)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
    AlarmLinkTargetType.APP -> target?.let(context.packageManager::getLaunchIntentForPackage)
    AlarmLinkTargetType.FILE -> target?.let { value ->
        val uri = Uri.parse(value)
        if (uri.scheme != "content") {
            null
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    null -> null
}

internal fun openAlarmAction(
    context: Context,
    type: AlarmLinkTargetType?,
    url: String?,
    target: String?,
) {
    try {
        val intent = alarmActionIntent(context, type, url, target)
        if (intent == null) {
            val message = if (type == AlarmLinkTargetType.WEB || type == null) {
                R.string.invalid_alarm_link
            } else {
                R.string.alarm_target_unavailable
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
        val launchIntent = if (type == AlarmLinkTargetType.FILE || type == AlarmLinkTargetType.OTHER) {
            Intent.createChooser(intent, context.getString(R.string.open_alarm_target))
        } else {
            intent
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_for_alarm_target, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.cannot_open_alarm_target, Toast.LENGTH_SHORT).show()
    }
}
