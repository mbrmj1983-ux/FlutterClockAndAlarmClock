package com.example.clockalarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
    var notificationPolicyAccess by remember {
        mutableStateOf(
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
        )
    }
    var bypassDndEnabled by remember {
        mutableStateOf(AlarmAudioSettings.bypassDndEnabled(context))
    }
    var alarmAudioSettingsError by remember { mutableStateOf<String?>(null) }
    var enableDndBypassAfterSettings by remember { mutableStateOf(false) }
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
    val notificationPolicyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        notificationPolicyAccess =
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        if (enableDndBypassAfterSettings && notificationPolicyAccess) {
            AlarmAudioSettings.setBypassDndEnabled(context, true)
            bypassDndEnabled = true
            alarmAudioSettingsError = null
        } else if (enableDndBypassAfterSettings) {
            AlarmAudioSettings.setBypassDndEnabled(context, false)
            bypassDndEnabled = false
            alarmAudioSettingsError = "لم يُمنح إذن تجاوز وضع عدم الإزعاج."
        }
        enableDndBypassAfterSettings = false
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alarms.clear()
                alarms.addAll(AlarmStore.load(context))
                notificationPolicyAccess =
                    context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
                bypassDndEnabled =
                    AlarmAudioSettings.bypassDndEnabled(context) && notificationPolicyAccess
                if (!notificationPolicyAccess) {
                    AlarmAudioSettings.setBypassDndEnabled(context, false)
                }
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

    fun changeDndBypass(enabled: Boolean) {
        alarmAudioSettingsError = null
        if (!enabled) {
            AlarmAudioSettings.setBypassDndEnabled(context, false)
            bypassDndEnabled = false
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.isNotificationPolicyAccessGranted) {
            AlarmAudioSettings.setBypassDndEnabled(context, true)
            bypassDndEnabled = true
            notificationPolicyAccess = true
            return
        }
        enableDndBypassAfterSettings = true
        try {
            notificationPolicyLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            enableDndBypassAfterSettings = false
            alarmAudioSettingsError = "تعذر فتح إعدادات الوصول إلى وضع عدم الإزعاج."
        } catch (_: SecurityException) {
            enableDndBypassAfterSettings = false
            alarmAudioSettingsError = "منع Android فتح إعدادات الوصول إلى وضع عدم الإزعاج."
        }
    }

    LaunchedEffect(Unit) {
        alarms.filter(Alarm::enabled).forEach { AlarmScheduler.schedule(context, it) }
        PrayerReminderScheduler.scheduleAll(context)
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
                        bypassDndEnabled = bypassDndEnabled,
                        notificationPolicyAccess = notificationPolicyAccess,
                        alarmAudioSettingsError = alarmAudioSettingsError,
                        onDndBypassChange = ::changeDndBypass,
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
            onSave = {
                    hour,
                    minute,
                    label,
                    linkUrl,
                    linkLabel,
                    linkButtonColor,
                    linkTargetType,
                    linkTarget,
                    date,
                    repeatDays,
                    recurrence,
                    soundEnabled,
                ->
                val existing = alarms.firstOrNull { it.id == editingAlarmId }
                val updated = existing?.copy(
                    hour = hour,
                    minute = minute,
                    label = label,
                    linkUrl = linkUrl,
                    linkLabel = linkLabel,
                    linkButtonColor = linkButtonColor,
                    linkTargetType = linkTargetType,
                    linkTarget = linkTarget,
                    repeatDays = repeatDays,
                    date = date,
                    visualEnabled = true,
                    soundEnabled = soundEnabled,
                    recurrence = recurrence,
                ) ?: Alarm(
                    id = System.currentTimeMillis(),
                    hour = hour,
                    minute = minute,
                    label = label,
                    linkUrl = linkUrl,
                    linkLabel = linkLabel,
                    linkButtonColor = linkButtonColor,
                    linkTargetType = linkTargetType,
                    linkTarget = linkTarget,
                    repeatDays = repeatDays,
                    date = date,
                    visualEnabled = true,
                    soundEnabled = soundEnabled,
                    recurrence = recurrence,
                )

                if (
                    recurrence == AlarmRecurrence.ONCE &&
                    nextAlarmTimeMillis(updated, ZonedDateTime.now()) == null
                ) {
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
    bypassDndEnabled: Boolean,
    notificationPolicyAccess: Boolean,
    alarmAudioSettingsError: String?,
    onDndBypassChange: (Boolean) -> Unit,
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
        AlarmAudioPolicyCard(
            bypassDndEnabled = bypassDndEnabled,
            notificationPolicyAccess = notificationPolicyAccess,
            error = alarmAudioSettingsError,
            onEnabledChange = onDndBypassChange,
        )
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
private fun AlarmAudioPolicyCard(
    bypassDndEnabled: Boolean,
    notificationPolicyAccess: Boolean,
    error: String?,
    onEnabledChange: (Boolean) -> Unit,
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
                    Text("صوت المنبّه في الأوضاع الصامتة", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (notificationPolicyAccess) "إذن عدم الإزعاج متاح"
                        else "يتطلب إذن الوصول إلى سياسة عدم الإزعاج",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = bypassDndEnabled && notificationPolicyAccess,
                    onCheckedChange = onEnabledChange,
                )
            }
            Text(
                "عند التفعيل، تسمح قناة المنبّه الصوتي بتجاوز عدم الإزعاج وتستخدم مسار صوت التنبيه " +
                    "للهاتف في الوضع الصامت. يبقى مستوى صوت المنبّه وإعدادات الشركة المصنّعة مؤثرًا.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!notificationPolicyAccess) {
                TextButton(onClick = { onEnabledChange(true) }) {
                    Text("منح إذن عدم الإزعاج")
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

        for (minuteMark in 0 until 60) {
            val angle = Math.toRadians(minuteMark * 6.0 - 90.0)
            val isFiveMinuteMark = minuteMark % 5 == 0
            val isQuarterHourMark = minuteMark % 15 == 0
            val outerRadius = radius * 0.94f
            val innerRadius = radius * when {
                isQuarterHourMark -> 0.81f
                isFiveMinuteMark -> 0.85f
                else -> 0.90f
            }
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
                strokeWidth = when {
                    isQuarterHourMark -> 2.5.dp.toPx()
                    isFiveMinuteMark -> 1.75.dp.toPx()
                    else -> 1.dp.toPx()
                },
                cap = StrokeCap.Round,
            )
        }

        val numeralPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = faceColor.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            textSize = 12.dp.toPx()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val numeralRadius = radius * 0.66f
        val numeralBaselineOffset = -(numeralPaint.ascent() + numeralPaint.descent()) / 2f
        val nativeCanvas = drawContext.canvas.nativeCanvas
        for (hour in 0 until 12) {
            val angle = Math.toRadians(hour * 30.0 - 90.0)
            val numeral = if (hour == 0) "١٢" else hour.toString().map { digit ->
                ('٠'.code + digit.digitToInt()).toChar()
            }.joinToString("")
            nativeCanvas.drawText(
                numeral,
                center.x + kotlin.math.cos(angle).toFloat() * numeralRadius,
                center.y + kotlin.math.sin(angle).toFloat() * numeralRadius + numeralBaselineOffset,
                numeralPaint,
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
    val context = LocalContext.current
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
                alarm.label?.takeIf(String::isNotBlank)?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = alarmDescription(alarm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val linkLabel = alarm.linkLabel
                if (alarm.hasActionTarget()) {
                    Button(
                        onClick = {
                            openAlarmAction(
                                context,
                                alarm.actionTargetType,
                                alarm.linkUrl,
                                alarm.linkTarget,
                            )
                        },
                        colors = alarm.linkButtonColor?.let {
                            ButtonDefaults.buttonColors(containerColor = Color(it))
                        } ?: ButtonDefaults.buttonColors(),
                    ) {
                        Text(linkLabel.orEmpty())
                    }
                }
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
    onSave: (
        Int,
        Int,
        String?,
        String?,
        String?,
        Long?,
        AlarmLinkTargetType?,
        String?,
        LocalDate?,
        Set<DayOfWeek>,
        AlarmRecurrence,
        Boolean,
    ) -> Boolean,
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
    var showDatePicker by remember { mutableStateOf(false) }
    var recurrence by remember {
        mutableStateOf(existing?.effectiveRecurrence ?: AlarmRecurrence.DAILY)
    }
    var repeatDays by remember {
        mutableStateOf(existing?.repeatDays ?: setOf(now.dayOfWeek))
    }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var linkUrl by remember { mutableStateOf(existing?.linkUrl.orEmpty()) }
    var linkLabel by remember { mutableStateOf(existing?.linkLabel.orEmpty()) }
    var linkButtonColor by remember { mutableStateOf(existing?.linkButtonColor) }
    var linkTargetType by remember {
        mutableStateOf(existing?.actionTargetType)
    }
    var linkTarget by remember { mutableStateOf(existing?.linkTarget) }
    var showAppPicker by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(existing?.soundEnabled ?: false) }
    var showInvalidDate by remember { mutableStateOf(false) }
    var showInvalidLink by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                linkTargetType = AlarmLinkTargetType.FILE
                linkTarget = uri.toString()
                linkUrl = ""
                linkLabel = alarmDocumentName(context, uri)
                showInvalidLink = false
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.file_permission_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val availableApps = remember(showAppPicker) {
        if (showAppPicker) launchableAlarmApps(context) else emptyList()
    }
    val fullScreenPermissionMissing =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val exactAlarmPermissionMissing =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

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
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("تسمية (اختيارية)") },
                    singleLine = true,
                )
                Text(
                    "زر يفتح وجهة عند الضغط عليه",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                )
                val targetTypes = listOf(
                    null to "بدون زر",
                    AlarmLinkTargetType.WEB to "ويب",
                    AlarmLinkTargetType.APP to "تطبيق",
                    AlarmLinkTargetType.FILE to "ملف",
                    AlarmLinkTargetType.OTHER to "رابط آخر",
                )
                targetTypes.chunked(3).forEach { rowTypes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowTypes.forEach { (type, title) ->
                            FilterChip(
                                selected = linkTargetType == type,
                                onClick = {
                                    if (linkTargetType != type) {
                                        linkTargetType = type
                                        linkUrl = ""
                                        linkTarget = null
                                        linkLabel = ""
                                        showInvalidLink = false
                                    }
                                    if (type == AlarmLinkTargetType.APP) showAppPicker = true
                                    if (type == AlarmLinkTargetType.FILE) {
                                        filePicker.launch(arrayOf("*/*"))
                                    }
                                },
                                label = { Text(title) },
                            )
                        }
                    }
                }
                when (linkTargetType) {
                    AlarmLinkTargetType.WEB,
                    AlarmLinkTargetType.OTHER,
                    -> OutlinedTextField(
                        value = linkUrl,
                        onValueChange = {
                            linkUrl = it
                            showInvalidLink = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (linkTargetType == AlarmLinkTargetType.WEB) {
                                    "رابط ويب"
                                } else {
                                    "رابط تطبيق أو خدمة"
                                },
                            )
                        },
                        placeholder = {
                            Text(
                                if (linkTargetType == AlarmLinkTargetType.WEB) {
                                    "https://youtu.be/… أو example.com"
                                } else {
                                    "mailto: أو tel: أو geo: أو market:"
                                },
                            )
                        },
                        singleLine = true,
                    )
                    AlarmLinkTargetType.APP -> OutlinedButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(linkLabel.takeIf(String::isNotBlank) ?: "اختيار تطبيق")
                    }
                    AlarmLinkTargetType.FILE -> OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(linkLabel.takeIf(String::isNotBlank) ?: "اختيار ملف")
                    }
                    null -> Unit
                }
                val actionConfigured = when (linkTargetType) {
                    AlarmLinkTargetType.WEB -> normalizeWebUrl(linkUrl) != null
                    AlarmLinkTargetType.OTHER -> normalizeOtherAlarmTarget(linkUrl) != null
                    AlarmLinkTargetType.APP, AlarmLinkTargetType.FILE -> !linkTarget.isNullOrBlank()
                    null -> false
                }
                if (linkTargetType != null && actionConfigured) {
                    OutlinedTextField(
                        value = linkLabel,
                        onValueChange = {
                            linkLabel = it
                            showInvalidLink = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("تسمية الزر") },
                        singleLine = true,
                    )
                    val linkColors = listOf(
                        null to "افتراضي",
                        0xFF6750A4L to "بنفسجي",
                        0xFF006C4CL to "أخضر",
                        0xFF0061A4L to "أزرق",
                        0xFFBA1A1AL to "أحمر",
                        0xFF8C5000L to "برتقالي",
                    )
                    Text(
                        "لون الزر (اختياري)",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    linkColors.chunked(3).forEach { colorRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            colorRow.forEach { (argb, labelText) ->
                                FilterChip(
                                    selected = linkButtonColor == argb,
                                    onClick = { linkButtonColor = argb },
                                    label = { Text(labelText) },
                                )
                            }
                        }
                    }
                }
                if (showInvalidLink) {
                    Text(
                        "اختر تطبيقًا أو ملفًا، أو أدخل رابطًا صالحًا وتسمية للزر.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "وتيرة التكرار",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                )
                val recurrenceOptions = listOf(
                    AlarmRecurrence.MINUTELY to "كل دقيقة",
                    AlarmRecurrence.HOURLY to "كل ساعة",
                    AlarmRecurrence.DAILY to "يوميًا",
                    AlarmRecurrence.WEEKLY to "أسبوعيًا",
                    AlarmRecurrence.MONTHLY to "شهريًا",
                    AlarmRecurrence.YEARLY to "سنويًا",
                    AlarmRecurrence.ONCE to "مرة واحدة",
                )
                recurrenceOptions.chunked(3).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowOptions.forEach { (option, label) ->
                            FilterChip(
                                selected = recurrence == option,
                                onClick = {
                                    recurrence = option
                                    showInvalidDate = false
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                TimePicker(state = timePickerState)
                if (
                    recurrence == AlarmRecurrence.MINUTELY ||
                    recurrence == AlarmRecurrence.HOURLY
                ) {
                    Text(
                        "يبدأ من الوقت المحدد ثم يتكرر حسب الوتيرة.",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (recurrence == AlarmRecurrence.WEEKLY) {
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
                if (
                    recurrence == AlarmRecurrence.ONCE ||
                    recurrence == AlarmRecurrence.MONTHLY ||
                    recurrence == AlarmRecurrence.YEARLY
                ) {
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
                }
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
                if (fullScreenPermissionMissing) {
                    Text(
                        "قد يمنع Android فتح شاشة التنبيه فوق التطبيقات حتى تسمح بملء الشاشة من إعدادات النظام.",
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
                if (exactAlarmPermissionMissing) {
                    Text(
                        "قد يتأخر التنبيه إذا لم تسمح بجدولة المنبّهات الدقيقة من إعدادات النظام.",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                            )
                        },
                    ) {
                        Text("إعدادات دقة التنبيه")
                    }
                }
                if (showInvalidDate) {
                    Text(
                        "اختر يومًا واحدًا على الأقل للتكرار الأسبوعي أو تاريخًا مستقبليًا للمنبّه المنفرد.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedDate = datePickerState.selectedDateMillis
                        ?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: LocalDate.now()
                    val date = when (recurrence) {
                        AlarmRecurrence.ONCE, AlarmRecurrence.MONTHLY, AlarmRecurrence.YEARLY ->
                            selectedDate
                        AlarmRecurrence.MINUTELY, AlarmRecurrence.HOURLY ->
                            existing?.date ?: selectedDate
                        else -> null
                    }
                    val days = if (recurrence == AlarmRecurrence.WEEKLY) repeatDays else emptySet()
                    val normalizedLink = when (linkTargetType) {
                        AlarmLinkTargetType.WEB ->
                            linkUrl.takeIf(String::isNotBlank)?.let(::normalizeWebUrl)
                        AlarmLinkTargetType.OTHER ->
                            linkUrl.takeIf(String::isNotBlank)?.let(::normalizeOtherAlarmTarget)
                        else -> null
                    }
                    val actionConfigured = when (linkTargetType) {
                        AlarmLinkTargetType.WEB, AlarmLinkTargetType.OTHER -> normalizedLink != null
                        AlarmLinkTargetType.APP, AlarmLinkTargetType.FILE -> !linkTarget.isNullOrBlank()
                        null -> true
                    }
                    if (linkTargetType != null && (!actionConfigured || linkLabel.isBlank())) {
                        showInvalidLink = true
                    } else if (recurrence == AlarmRecurrence.WEEKLY && days.isEmpty()) {
                        showInvalidDate = true
                    } else if (onSave(
                            timePickerState.hour,
                            timePickerState.minute,
                            label.trim().takeIf(String::isNotEmpty),
                            normalizedLink,
                            linkLabel.trim().takeIf { linkTargetType != null && it.isNotEmpty() },
                            linkButtonColor.takeIf { linkTargetType != null },
                            linkTargetType,
                            linkTarget.takeIf {
                                linkTargetType == AlarmLinkTargetType.APP ||
                                    linkTargetType == AlarmLinkTargetType.FILE
                            },
                            date,
                            days,
                            recurrence,
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

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("اختر تطبيقًا") },
            text = {
                if (availableApps.isEmpty()) {
                    Text(stringResource(R.string.no_launchable_apps))
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        availableApps.forEach { app ->
                            TextButton(
                                onClick = {
                                    linkTargetType = AlarmLinkTargetType.APP
                                    linkTarget = app.packageName
                                    linkUrl = ""
                                    linkLabel = app.label
                                    showInvalidLink = false
                                    showAppPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(app.label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("إلغاء")
                }
            },
        )
    }

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
    when (alarm.effectiveRecurrence) {
        AlarmRecurrence.MINUTELY -> return "كل دقيقة"
        AlarmRecurrence.HOURLY -> return "كل ساعة"
        AlarmRecurrence.DAILY -> return "يوميًا"
        AlarmRecurrence.MONTHLY -> return "شهريًا في اليوم ${alarm.date?.dayOfMonth ?: 1}"
        AlarmRecurrence.YEARLY -> return alarm.date
            ?.format(DateTimeFormatter.ofPattern("سنويًا — d MMMM", Locale("ar")))
            ?: "سنويًا"
        AlarmRecurrence.ONCE -> return alarm.date
            ?.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar")))
            ?: "مرة واحدة"
        AlarmRecurrence.WEEKLY -> Unit
    }
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
