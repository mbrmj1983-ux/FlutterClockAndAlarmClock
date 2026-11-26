package com.example.clockalarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URI
import java.net.URISyntaxException

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

internal fun openAlarmLink(context: Context, url: String) {
    val normalizedUrl = normalizeWebUrl(url)
    if (normalizedUrl == null) {
        Toast.makeText(context, R.string.invalid_alarm_link, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_browser_for_alarm_link, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.cannot_open_alarm_link, Toast.LENGTH_SHORT).show()
    }
}
