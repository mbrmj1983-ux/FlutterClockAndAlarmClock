package com.example.clockalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.ActivityNotFoundException
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    val saveableStateHolder = rememberSaveableStateHolder()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    var selectedRoute by rememberSaveable { mutableStateOf(AppSection.CLOCK.route) }
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var currentInstant by remember { mutableStateOf(Instant.now()) }
    var showAlarmEditor by remember { mutableStateOf(false) }
    var editingAlarmId by remember { mutableStateOf<Long?>(null) }
    var overlayPermission by remember {
        mutableStateOf(OverlayPermissionState(Settings.canDrawOverlays(context)))
    }
    var overlaySettingsError by remember { mutableStateOf<String?>(null) }
    var floatingClockRunning by remember { mutableStateOf(FloatingClockState.isRunning(context)) }
    var floatingClockError by remember { mutableStateOf(FloatingClockState.lastError(context)) }
    var enableOverlayAfterPermission by remember { mutableStateOf(false) }
    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayPermission = OverlayPermissionState(Settings.canDrawOverlays(context))
        overlaySettingsError = null
        if (overlayPermission.granted && enableOverlayAfterPermission) {
            floatingClockError = startFloatingClock(context)
        } else if (!overlayPermission.granted && enableOverlayAfterPermission) {
            floatingClockError = "لم يُمنح الإذن؛ لم تبدأ الساعة العائمة."
        } else if (!overlayPermission.granted && FloatingClockState.isRunning(context)) {
            stopFloatingClock(context)
            floatingClockRunning = false
        }
        enableOverlayAfterPermission = false
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alarms.clear()
                alarms.addAll(AlarmStore.load(context))
                overlayPermission = OverlayPermissionState(Settings.canDrawOverlays(context))
                floatingClockRunning = FloatingClockState.isRunning(context)
                if (!overlayPermission.granted && floatingClockRunning) {
                    stopFloatingClock(context)
                    floatingClockRunning = false
                }
                floatingClockError = FloatingClockState.lastError(context)
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

    fun openOverlayPermissionSettings(enableAfterGrant: Boolean) {
        enableOverlayAfterPermission = enableAfterGrant
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        try {
            overlaySettingsLauncher.launch(intent)
            overlaySettingsError = null
        } catch (exception: ActivityNotFoundException) {
            enableOverlayAfterPermission = false
            overlaySettingsError = "تعذر فتح إعدادات الظهور فوق التطبيقات على هذا الجهاز."
        }
    }

    fun changeFloatingClock(enabled: Boolean) {
        when (
            OverlayWindowPolicy.action(
                requestedEnabled = enabled,
                permissionGranted = overlayPermission.granted,
                isRunning = floatingClockRunning,
            )
        ) {
            OverlayWindowAction.REQUEST_PERMISSION -> openOverlayPermissionSettings(enableAfterGrant = true)
            OverlayWindowAction.START -> {
                floatingClockError = startFloatingClock(context)
                overlaySettingsError = null
            }
            OverlayWindowAction.STOP -> {
                stopFloatingClock(context)
                floatingClockRunning = false
                floatingClockError = null
            }
            OverlayWindowAction.NO_CHANGE -> Unit
        }
    }

    LaunchedEffect(selectedRoute) {
        if (AppSection.fromRoute(selectedRoute) == AppSection.ALARMS_SETTINGS) {
            while (true) {
                floatingClockRunning = FloatingClockState.isRunning(context)
                floatingClockError = FloatingClockState.lastError(context)
                overlayPermission = OverlayPermissionState(Settings.canDrawOverlays(context))
                delay(500)
            }
        }
    }

    val selectedSection = AppSection.fromRoute(selectedRoute)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppSection.values().forEach { section ->
                    NavigationBarItem(
                        selected = section == selectedSection,
                        onClick = { selectedRoute = section.route },
                        icon = { Text(section.glyph) },
                        label = { Text(section.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { contentPadding ->
        saveableStateHolder.SaveableStateProvider(selectedSection.route) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (selectedSection) {
                    AppSection.CLOCK -> ClockHomeScreen(currentTime)
                    AppSection.PRAYER_TIMES -> PrayerDashboard(now = currentInstant)
                    AppSection.WORLD_CLOCK -> WorldClockDashboard(now = currentInstant)
                    AppSection.ALARMS_SETTINGS -> AlarmsSettingsScreen(
                        alarms = alarms,
                        overlayPermission = overlayPermission,
                        overlaySettingsError = overlaySettingsError,
                        floatingClockRunning = floatingClockRunning,
                        floatingClockError = floatingClockError,
                        onManageOverlayPermission = {
                            openOverlayPermissionSettings(enableAfterGrant = false)
                        },
                        onFloatingClockChange = ::changeFloatingClock,
                        onAddAlarm = {
                            editingAlarmId = null
                            showAlarmEditor = true
                        },
                        onEditAlarm = { alarm ->
                            editingAlarmId = alarm.id
                            showAlarmEditor = true
                        },
                        onAlarmEnabledChange = { alarm, enabled ->
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
                        onDeleteAlarm = { alarm ->
                            AlarmScheduler.cancel(context, alarm)
                            alarms.remove(alarm)
                            AlarmStore.save(context, alarms)
                        },
                    )
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
private fun ClockHomeScreen(currentTime: LocalDateTime) {
    Spacer(modifier = Modifier.height(16.dp))
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
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun AlarmsSettingsScreen(
    alarms: List<Alarm>,
    overlayPermission: OverlayPermissionState,
    overlaySettingsError: String?,
    floatingClockRunning: Boolean,
    floatingClockError: String?,
    onManageOverlayPermission: () -> Unit,
    onFloatingClockChange: (Boolean) -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onAlarmEnabledChange: (Alarm, Boolean) -> Unit,
    onDeleteAlarm: (Alarm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("المنبّهات والإعدادات", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAddAlarm) { Text("إضافة منبّه") }
        }
        OverlayPermissionCard(
            state = overlayPermission,
            overlayWindowRunning = floatingClockRunning,
            settingsError = overlaySettingsError,
            serviceError = floatingClockError,
            onManagePermission = onManageOverlayPermission,
            onWindowChange = onFloatingClockChange,
        )
        if (alarms.isEmpty()) {
            Text(
                text = "لا توجد منبّهات بعد",
                modifier = Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            alarms.forEach { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onEdit = { onEditAlarm(alarm) },
                    onEnabledChange = { enabled -> onAlarmEnabledChange(alarm, enabled) },
                    onDelete = { onDeleteAlarm(alarm) },
                )
            }
        }
    }
}

@Composable
private fun OverlayPermissionCard(
    state: OverlayPermissionState,
    overlayWindowRunning: Boolean,
    settingsError: String?,
    serviceError: String?,
    onManagePermission: () -> Unit,
    onWindowChange: (Boolean) -> Unit,
) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("الساعة العائمة", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "النافذة: ${if (overlayWindowRunning) "نشطة" else "متوقفة"}",
                        color = if (state.granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "إذن النظام: ${state.statusLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = overlayWindowRunning,
                    onCheckedChange = onWindowChange,
                )
            }
            Text(
                "تظهر ساعة قابلة للسحب فوق التطبيقات مع إشعار دائم. منح إذن SYSTEM_ALERT_WINDOW " +
                    "يتطلب موافقتك في إعداد Android؛ يمكنك إيقاف النافذة من هذا المفتاح أو من الإشعار.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onManagePermission) {
                Text(if (state.granted) "إدارة إذن الظهور فوق التطبيقات" else "تفعيل إذن الظهور فوق التطبيقات")
            }
            settingsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            serviceError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
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
