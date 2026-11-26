package com.example.clockalarm

import android.os.Build
import android.os.Bundle
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class AlarmRingingActivity : ComponentActivity() {
    private var alarmPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (handleNotificationAction(intent.action, alarmId)) return
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, -1)
        val alarmLabel = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL)?.takeIf(String::isNotBlank)
        val linkUrl = intent.getStringExtra(AlarmScheduler.EXTRA_LINK_URL)
        val linkLabel = intent.getStringExtra(AlarmScheduler.EXTRA_LINK_LABEL)
        val linkTargetType = intent.getStringExtra(AlarmScheduler.EXTRA_LINK_TARGET_TYPE)
            ?.let { name -> AlarmLinkTargetType.values().firstOrNull { it.name == name } }
        val linkTarget = intent.getStringExtra(AlarmScheduler.EXTRA_LINK_TARGET)
        val linkButtonColor = if (intent.hasExtra(AlarmScheduler.EXTRA_LINK_BUTTON_COLOR)) {
            intent.getLongExtra(AlarmScheduler.EXTRA_LINK_BUTTON_COLOR, 0L)
        } else {
            null
        }
        val shouldPlaySound = intent.getBooleanExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, false)
        if (alarmId < 0 || hour !in 0..23 || minute !in 0..59) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var soundEnabled by remember { mutableStateOf(setAlarmSoundEnabled(shouldPlaySound)) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = alarmLabel ?: getString(R.string.alarm_ringing_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "%02d:%02d".format(hour, minute),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = getString(R.string.alarm_ringing_message),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (
                            hasAlarmActionTarget(linkTargetType, linkUrl, linkTarget) &&
                            !linkLabel.isNullOrBlank()
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    openAlarmAction(
                                        this@AlarmRingingActivity,
                                        linkTargetType,
                                        linkUrl,
                                        linkTarget,
                                    )
                                },
                                colors = linkButtonColor?.let {
                                    ButtonDefaults.buttonColors(containerColor = Color(it))
                                } ?: ButtonDefaults.buttonColors(),
                            ) {
                                Text(linkLabel)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { soundEnabled = setAlarmSoundEnabled(!soundEnabled) },
                        ) {
                            Text(
                                if (soundEnabled) {
                                    getString(R.string.mute_alarm_sound)
                                } else {
                                    getString(R.string.enable_alarm_sound)
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { dismissAlarm(alarmId) }) {
                            Text(getString(R.string.dismiss_alarm))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { snoozeAlarm(alarmId) }) {
                            Text(getString(R.string.snooze_alarm))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        handleNotificationAction(intent.action, alarmId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        alarmPlayer?.release()
        alarmPlayer = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun handleNotificationAction(action: String?, alarmId: Long): Boolean {
        when (action) {
            AlarmScheduler.ACTION_DISMISS -> {
                if (alarmId >= 0) dismissAlarm(alarmId) else finish()
                return true
            }
            AlarmScheduler.ACTION_SNOOZE -> {
                if (alarmId >= 0) snoozeAlarm(alarmId) else finish()
                return true
            }
        }
        return false
    }

    private fun setAlarmSoundEnabled(enabled: Boolean): Boolean {
        if (!enabled) {
            alarmPlayer?.pause()
            return false
        }
        if (alarmPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            alarmPlayer = MediaPlayer.create(this, R.raw.alarm_morse_loop, audioAttributes, 0)
            if (alarmPlayer == null) {
                Toast.makeText(this, R.string.alarm_sound_unavailable, Toast.LENGTH_SHORT).show()
                return false
            }
            alarmPlayer?.isLooping = true
        }
        alarmPlayer?.start()
        return true
    }

    private fun dismissAlarm(alarmId: Long) {
        NotificationManagerCompat.from(this).cancel(alarmId.hashCode())
        finish()
    }

    private fun snoozeAlarm(alarmId: Long) {
        val alarm = AlarmStore.load(this).firstOrNull { it.id == alarmId }
        if (alarm == null) {
            dismissAlarm(alarmId)
            return
        }
        AlarmScheduler.scheduleSnooze(this, alarm)
        dismissAlarm(alarmId)
    }
}
