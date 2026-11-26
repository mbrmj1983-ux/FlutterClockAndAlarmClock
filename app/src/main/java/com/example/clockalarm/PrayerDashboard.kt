package com.example.clockalarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun PrayerDashboard(now: Instant) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(PrayerSettingsStore.load(context)) }
    var reminderSettings by remember { mutableStateOf(PrayerReminderSettingsStore.load(context)) }
    var notificationsAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }
    var showSettings by remember { mutableStateOf(false) }
    var showCities by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    fun updateSettings(updated: PrayerSettings) {
        settings = updated
        PrayerSettingsStore.save(context, updated)
        PrayerReminderScheduler.scheduleAll(context)
    }

    val reminderNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun changeReminder(category: PrayerReminderCategory, enabled: Boolean) {
        reminderSettings = reminderSettings.withEnabled(category, enabled)
        PrayerReminderSettingsStore.save(context, reminderSettings)
        PrayerReminderScheduler.scheduleAll(context)
        if (
            enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            reminderNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            requestCurrentLocation(context) { result ->
                result.fold(
                    onSuccess = { location ->
                        updateSettings(settings.copy(location = location))
                        locationMessage = null
                    },
                    onFailure = { error ->
                        Log.w("PrayerDashboard", "Unable to get the current location", error)
                        locationMessage = error.message ?: "تعذر الحصول على الموقع"
                    },
                )
            }
        } else {
            locationMessage = "لم يُمنح إذن الموقع؛ يمكنك اختيار مدينة يدويًا."
        }
    }

    val zone = ZoneId.of(settings.location.zoneId)
    val localDate = now.atZone(zone).toLocalDate()
    val schedule = remember(localDate, settings) {
        try {
            PrayerTimeCalculator.calculate(
                localDate,
                settings.location,
                settings.method,
                settings.asrFactor,
                settings.adjustments,
            ) to PrayerTimeCalculator.calculate(
                localDate.plusDays(1),
                settings.location,
                settings.method,
                settings.asrFactor,
                settings.adjustments,
            )
        } catch (exception: IllegalArgumentException) {
            null
        }
    }
    val entries = remember(now.epochSecond / 60, settings, localDate) {
        buildList {
            add(IslamicEvents.nextFriday(now, settings.location))
            add(IslamicEvents.nextHijriDate(now, settings.location, 9, 1, "بداية رمضان"))
            add(IslamicEvents.nextHijriDate(now, settings.location, 12, 9, "يوم عرفة"))
            add(IslamicEvents.nextHijriDate(now, settings.location, 10, 1, "عيد الفطر"))
            add(IslamicEvents.nextHijriDate(now, settings.location, 12, 10, "عيد الأضحى"))
            addAll(SeasonCalculator.nextStarts(now, settings.location.latitude < 0))
            schedule?.let { (today, tomorrow) ->
                listOf(
                    CountdownEntry("منتصف الليل الشرعي", today.midnight),
                    CountdownEntry("الثلث الأخير من الليل", today.lastThird),
                    CountdownEntry("منتصف الليل الشرعي", tomorrow.midnight),
                    CountdownEntry("الثلث الأخير من الليل", tomorrow.lastThird),
                ).filterTo(this) { it.at.isAfter(now) }
            }
        }.sortedBy(CountdownEntry::at)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("المواقيت والمناسبات", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${settings.location.name} · ${settings.location.zoneId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showSettings = true }) { Text("الإعدادات") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        locationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) { Text("استخدام GPS") }
                OutlinedButton(onClick = { showCities = true }) { Text("اختيار مدينة") }
            }
            locationMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            val observances = IslamicEvents.observances(localDate)
            Text(
                text = "الفصل الحالي: ${SeasonCalculator.current(now, settings.location.latitude < 0).arabic}" +
                    if (observances.isEmpty()) "" else " · اليوم: ${observances.joinToString("، ")}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (schedule == null) {
                Text(
                    "تعذر حساب الشروق/الغروب لهذا الموقع والتاريخ (قد يكون الموقع ضمن نطاق قطبي). " +
                        "اختر مدينة أو موقعًا آخر.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val (today, tomorrow) = schedule
                val nextPrayer = today.times.entries
                    .filter { it.value.isAfter(now) }
                    .minByOrNull { it.value }
                    ?.let { CountdownEntry("الصلاة القادمة: ${it.key.arabic}", it.value) }
                    ?: CountdownEntry(
                        "الصلاة القادمة: ${PrayerName.FAJR.arabic}",
                        tomorrow.times.getValue(PrayerName.FAJR),
                    )
                CountdownRow(nextPrayer, now, emphasized = true)
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    PrayerName.values().forEach { prayer ->
                        val at = today.times.getValue(prayer)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(prayer.arabic, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                DateTimeFormatter.ofPattern("HH:mm")
                                    .withLocale(Locale.forLanguageTag("ar"))
                                    .format(at.atZone(zone)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text("العدّادات القادمة", style = MaterialTheme.typography.titleSmall)
                    entries.forEach { CountdownRow(it, now) }
                }
            }
        }
    }
    PrayerReminderSettingsCard(
        settings = reminderSettings,
        notificationsAllowed = notificationsAllowed,
        onReminderChange = ::changeReminder,
    )

    if (showSettings) {
        PrayerSettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = ::updateSettings,
            onChooseCity = {
                showSettings = false
                showCities = true
            },
        )
    }
    if (showCities) {
        AlertDialog(
            onDismissRequest = { showCities = false },
            title = { Text("اختيار المدينة") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PrayerCities.all.forEach { city ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                updateSettings(settings.copy(location = city))
                                showCities = false
                                locationMessage = null
                            },
                        ) { Text(city.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCities = false }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun PrayerReminderSettingsCard(
    settings: PrayerReminderSettings,
    notificationsAllowed: Boolean,
    onReminderChange: (PrayerReminderCategory, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("تنبيهات المواقيت والمناسبات", style = MaterialTheme.typography.titleMedium)
            if (!notificationsAllowed) {
                Text(
                    "اسمح بإشعارات التطبيق من Android لتصلك التنبيهات.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("تنبيهات الصلوات", style = MaterialTheme.typography.titleSmall)
            PrayerReminderCategory.values().filter { it.isPrayer }.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(category.title, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.isEnabled(category),
                        onCheckedChange = { onReminderChange(category, it) },
                    )
                }
            }
            Text("تنبيهات المناسبات والفصول", style = MaterialTheme.typography.titleSmall)
            PrayerReminderCategory.values().filterNot { it.isPrayer }.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(category.title, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.isEnabled(category),
                        onCheckedChange = { onReminderChange(category, it) },
                    )
                }
            }
            Text(
                "تصل تنبيهات الصلاة عند دخول وقتها، والجمعة عند بداية يومها، والمناسبات والفصول عند بدايتها. " +
                    "قد يؤثر توفير الطاقة أو إذن التنبيه الدقيق في توقيت التسليم.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CountdownRow(
    entry: CountdownEntry,
    now: Instant,
    emphasized: Boolean = false,
) {
    val seconds = remainingCountdownSeconds(now, entry.at)
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    val remainingSeconds = seconds % 60
    val countdown = if (days > 0) {
        "%d يوم %02d:%02d:%02d".format(days, hours, minutes, remainingSeconds)
    } else {
        "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (emphasized) 6.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.title,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            countdown,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PrayerSettingsDialog(
    settings: PrayerSettings,
    onDismiss: () -> Unit,
    onSave: (PrayerSettings) -> Unit,
    onChooseCity: () -> Unit,
) {
    var updated by remember(settings) { mutableStateOf(settings) }
    var methodsExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات مواقيت الصلاة") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onChooseCity) { Text("الموقع: ${updated.location.name}") }
                Text(
                    "عند استخدام GPS تُحسب المواقيت من الإحداثيات، ويُستخدم توقيت الجهاز الحالي.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("طريقة الحساب", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { methodsExpanded = true }) { Text(updated.method.label) }
                DropdownMenu(
                    expanded = methodsExpanded,
                    onDismissRequest = { methodsExpanded = false },
                ) {
                    CalculationMethod.values().forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method.label) },
                            onClick = {
                                updated = updated.copy(method = method)
                                methodsExpanded = false
                            },
                        )
                    }
                }
                Text("طريقة العصر", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "شافعي/مالكي", 2 to "حنفي").forEach { (factor, label) ->
                        OutlinedButton(
                            onClick = { updated = updated.copy(asrFactor = factor) },
                        ) {
                            RadioButton(selected = updated.asrFactor == factor, onClick = null)
                            Text(label)
                        }
                    }
                }
                Text("تعديل الدقائق (±60)", style = MaterialTheme.typography.titleSmall)
                PrayerName.values().forEach { prayer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(prayer.arabic)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    updated = updated.copy(
                                        adjustments = updated.adjustments.change(prayer, -1),
                                    )
                                },
                            ) { Text("−") }
                            Text("%+d".format(updated.adjustments.forPrayer(prayer)))
                            TextButton(
                                onClick = {
                                    updated = updated.copy(
                                        adjustments = updated.adjustments.change(prayer, 1),
                                    )
                                },
                            ) { Text("+") }
                        }
                    }
                }
                TextButton(
                    onClick = { updated = updated.copy(adjustments = PrayerAdjustments()) },
                ) { Text("إعادة ضبط تعديلات الدقائق") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(updated)
                    onDismiss()
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun requestCurrentLocation(
    context: Context,
    onResult: (Result<PrayerLocation>) -> Unit,
) {
    val locationManager = context.getSystemService(LocationManager::class.java)
    val hasFinePermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val provider = when {
        hasFinePermission && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
            LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
            LocationManager.NETWORK_PROVIDER
        else -> {
            onResult(Result.failure(IllegalStateException("فعّل خدمات الموقع أو اختر مدينة.")))
            return
        }
    }

    fun toPrayerLocation(location: Location): PrayerLocation = PrayerLocation(
        name = "موقعي الحالي",
        latitude = location.latitude,
        longitude = location.longitude,
        zoneId = ZoneId.systemDefault().id,
    )

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                if (location == null) {
                    onResult(Result.failure(IllegalStateException("لم يُعثر على موقع؛ حاول مجددًا أو اختر مدينة.")))
                } else {
                    onResult(Result.success(toPrayerLocation(location)))
                }
            }
        } else {
            locationManager.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        onResult(Result.success(toPrayerLocation(location)))
                    }

                    override fun onProviderDisabled(provider: String) {
                        onResult(Result.failure(IllegalStateException("أُوقف مزود الموقع؛ اختر مدينة.")))
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                },
                Looper.getMainLooper(),
            )
        }
    } catch (exception: SecurityException) {
        onResult(Result.failure(exception))
    } catch (exception: IllegalArgumentException) {
        onResult(Result.failure(exception))
    }
}
