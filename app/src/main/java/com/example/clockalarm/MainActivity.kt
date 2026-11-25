package com.example.clockalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ClockScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarms = remember { mutableStateListOf<Alarm>().apply { addAll(AlarmStore.load(context)) } }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var currentInstant by remember { mutableStateOf(Instant.now()) }
    var showAlarmEditor by remember { mutableStateOf(false) }
    var editingAlarmId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alarms.clear()
                alarms.addAll(AlarmStore.load(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        alarms.filter(Alarm::enabled).forEach { AlarmScheduler.schedule(context, it) }
        while (true) {
            currentTime = LocalDateTime.now()
            currentInstant = Instant.now()
            delay(1_000)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "الساعة الآن",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnalogClock(
                time = currentTime,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = currentTime.format(
                    DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar")),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.hijri_date,
                    HijriDateFormatter.format(currentTime.toLocalDate()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            WorldClockDashboard(now = currentInstant)
            Spacer(modifier = Modifier.height(12.dp))
            PrayerDashboard(now = currentInstant)
            Spacer(modifier = Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("المنبّهات", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        editingAlarmId = null
                        showAlarmEditor = true
                    },
                ) {
                    Text("إضافة منبّه")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (alarms.isEmpty()) {
                Text(
                    text = "لا توجد منبّهات بعد",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    alarms.forEach { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onEdit = {
                                editingAlarmId = alarm.id
                                showAlarmEditor = true
                            },
                            onEnabledChange = { enabled ->
                                val updated = alarm.copy(enabled = enabled)
                                val index = alarms.indexOfFirst { it.id == alarm.id }
                                if (index >= 0) alarms[index] = updated
                                AlarmStore.save(context, alarms)
                                if (enabled) {
                                    AlarmScheduler.schedule(context, updated)
                                } else {
                                    AlarmScheduler.cancel(context, alarm)
                                }
                            },
                            onDelete = {
                                AlarmScheduler.cancel(context, alarm)
                                alarms.remove(alarm)
                                AlarmStore.save(context, alarms)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAlarmEditor) {
        AlarmEditorDialog(
            existing = alarms.firstOrNull { it.id == editingAlarmId },
            onDismiss = { showAlarmEditor = false },
            onSave = { hour, minute, date, repeatDays, visualEnabled, soundEnabled ->
                val existing = alarms.firstOrNull { it.id == editingAlarmId }
                val updated = existing?.copy(
                    hour = hour,
                    minute = minute,
                    repeatDays = repeatDays,
                    date = date,
                    visualEnabled = visualEnabled,
                    soundEnabled = soundEnabled,
                ) ?: Alarm(
                    id = System.currentTimeMillis(),
                    hour = hour,
                    minute = minute,
                    repeatDays = repeatDays,
                    date = date,
                    visualEnabled = visualEnabled,
                    soundEnabled = soundEnabled,
                )

                if (date != null && nextAlarmTimeMillis(updated, ZonedDateTime.now()) == null) {
                    false
                } else {
                    if (existing != null) {
                        AlarmScheduler.cancel(context, existing)
                        val index = alarms.indexOfFirst { it.id == existing.id }
                        if (index >= 0) alarms[index] = updated
                    } else {
                        alarms.add(updated)
                    }
                    AlarmStore.save(context, alarms)
                    AlarmScheduler.schedule(context, updated)
                    if (existing == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    true
                }
            },
        )
    }
}

@Composable
private fun AnalogClock(
    time: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val faceColor = MaterialTheme.colorScheme.outline
    val hourHandColor = MaterialTheme.colorScheme.primary
    val minuteHandColor = MaterialTheme.colorScheme.onSurface
    val secondHandColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.size(176.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 4.dp.toPx()
        drawCircle(
            color = faceColor,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        for (hour in 0 until 12) {
            val angle = Math.toRadians(hour * 30.0 - 90.0)
            val outerRadius = radius * 0.9f
            val innerRadius = radius * if (hour % 3 == 0) 0.78f else 0.84f
            drawLine(
                color = faceColor,
                start = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * innerRadius,
                    center.y + kotlin.math.sin(angle).toFloat() * innerRadius,
                ),
                end = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * outerRadius,
                    center.y + kotlin.math.sin(angle).toFloat() * outerRadius,
                ),
                strokeWidth = if (hour % 3 == 0) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        fun drawHand(angle: Double, length: Float, color: Color, width: Float) {
            drawLine(
                color = color,
                start = center,
                end = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * length,
                    center.y + kotlin.math.sin(angle).toFloat() * length,
                ),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }

        val minute = time.minute + time.second / 60.0
        val hour = time.hour % 12 + minute / 60.0
        drawHand(Math.toRadians(hour * 30.0 - 90.0), radius * 0.52f, hourHandColor, 4.dp.toPx())
        drawHand(Math.toRadians(minute * 6.0 - 90.0), radius * 0.72f, minuteHandColor, 3.dp.toPx())
        drawHand(Math.toRadians(time.second * 6.0 - 90.0), radius * 0.78f, secondHandColor, 1.5.dp.toPx())
        drawCircle(color = hourHandColor, radius = 4.dp.toPx(), center = center)
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = alarmDescription(alarm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Switch(checked = alarm.enabled, onCheckedChange = onEnabledChange)
                Row {
                    TextButton(onClick = onEdit) { Text("تعديل") }
                    TextButton(onClick = onDelete) { Text("حذف") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorDialog(
    existing: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, LocalDate?, Set<DayOfWeek>, Boolean, Boolean) -> Boolean,
) {
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }
    val timePickerState = rememberTimePickerState(
        initialHour = existing?.hour ?: now.hour,
        initialMinute = existing?.minute ?: now.minute,
        is24Hour = true,
    )
    val initialDate = existing?.date ?: LocalDate.now()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    var calendarEnabled by remember { mutableStateOf(existing?.date != null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var repeatDays by remember {
        mutableStateOf(existing?.repeatDays ?: DayOfWeek.values().toSet())
    }
    var visualEnabled by remember { mutableStateOf(existing?.visualEnabled ?: true) }
    var soundEnabled by remember { mutableStateOf(existing?.soundEnabled ?: false) }
    var showInvalidDate by remember { mutableStateOf(false) }
    val fullScreenPermissionMissing =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة منبّه" else "تعديل المنبّه") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = timePickerState)
                AlarmOptionSwitch(
                    label = "موعد في التقويم",
                    checked = calendarEnabled,
                    onCheckedChange = {
                        calendarEnabled = it
                        showInvalidDate = false
                    },
                )
                if (calendarEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val selectedDate = datePickerState.selectedDateMillis
                            ?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        Text(
                            selectedDate?.format(
                                DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar")),
                            ) ?: "اختر التاريخ",
                        )
                        TextButton(onClick = { showDatePicker = true }) {
                            Text("اختيار")
                        }
                    }
                } else {
                    Text(
                        "أيام التكرار",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val days = DayOfWeek.values().toList()
                    val labels = listOf("ن", "ث", "ر", "خ", "ج", "س", "ح")
                    listOf(days.take(4), days.drop(4)).forEachIndexed { rowIndex, rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            rowDays.forEachIndexed { index, day ->
                                val dayLabel = labels[index + if (rowIndex == 0) 0 else 4]
                                FilterChip(
                                    selected = day in repeatDays,
                                    onClick = {
                                        repeatDays = if (day in repeatDays) {
                                            repeatDays - day
                                        } else {
                                            repeatDays + day
                                        }
                                        showInvalidDate = false
                                    },
                                    label = { Text(dayLabel) },
                                )
                            }
                        }
                    }
                }
                AlarmOptionSwitch(
                    label = "تنبيه بصري على شاشة القفل",
                    checked = visualEnabled,
                    onCheckedChange = { visualEnabled = it },
                )
                AlarmOptionSwitch(
                    label = "تشغيل الصوت",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it },
                )
                if (!soundEnabled) {
                    Text(
                        "سيظهر التنبيه بصمت.",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (visualEnabled && fullScreenPermissionMissing) {
                    Text(
                        "قد يقيّد Android العرض بملء الشاشة حتى تمنح الإذن من إعدادات النظام.",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                            )
                        },
                    ) {
                        Text("إعدادات التنبيه بملء الشاشة")
                    }
                }
                if (showInvalidDate) {
                    Text(
                        "اختر تاريخًا مستقبليًا أو يومًا واحدًا على الأقل.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = if (calendarEnabled) {
                        datePickerState.selectedDateMillis
                            ?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                            ?: LocalDate.now()
                    } else {
                        null
                    }
                    if (date == null && repeatDays.isEmpty()) {
                        showInvalidDate = true
                    } else if (onSave(
                            timePickerState.hour,
                            timePickerState.minute,
                            date,
                            if (calendarEnabled) emptySet() else repeatDays,
                            visualEnabled,
                            soundEnabled,
                        )
                    ) {
                        onDismiss()
                    } else {
                        showInvalidDate = true
                    }
                },
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("إلغاء") }
        },
        shape = RoundedCornerShape(28.dp),
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("تم") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AlarmOptionSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun alarmDescription(alarm: Alarm): String {
    val date = alarm.date
    if (date != null) {
        return date.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar")))
    }
    val allDays = DayOfWeek.values().toSet()
    if (alarm.repeatDays == allDays) return "يوميًا"
    val labels = mapOf(
        DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء",
        DayOfWeek.WEDNESDAY to "الأربعاء",
        DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
        DayOfWeek.SATURDAY to "السبت",
        DayOfWeek.SUNDAY to "الأحد",
    )
    return alarm.repeatDays.sortedBy { it.value }.joinToString("، ") { labels.getValue(it) }
}
