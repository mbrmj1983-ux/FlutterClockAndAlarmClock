package com.example.clockalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object FloatingClockState {
    private const val PREFERENCES = "floating_clock"

    fun isRunning(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean("running", false)

    fun setRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("running", running)
            .apply()
    }

    fun lastError(context: Context): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString("last_error", null)

    fun setError(context: Context, error: String?) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString("last_error", error)
            .apply()
    }
}

internal fun startFloatingClock(context: Context): String? {
    return try {
        ContextCompat.startForegroundService(
            context,
            Intent(context, FloatingClockService::class.java).setAction(FloatingClockService.ACTION_SHOW),
        )
        null
    } catch (exception: IllegalStateException) {
        Log.e("FloatingClockService", "Android rejected the foreground service start", exception)
        "منع Android بدء الساعة العائمة في الخلفية. أعد المحاولة من داخل التطبيق."
    } catch (exception: SecurityException) {
        Log.e("FloatingClockService", "The floating clock service lacks permission", exception)
        "تعذر تشغيل الساعة العائمة بسبب قيود الإذن أو الإشعارات."
    }
}

internal fun stopFloatingClock(context: Context) {
    context.stopService(Intent(context, FloatingClockService::class.java))
    FloatingClockState.setRunning(context, false)
}

class FloatingClockService : Service() {
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var clockView: TextView? = null

    private val updateClock = object : Runnable {
        override fun run() {
            clockView?.text = clockText()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            FloatingClockState.setRunning(this, false)
            FloatingClockState.setError(this, "إذن الظهور فوق التطبيقات غير ممنوح.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startAsForegroundService()
            showClockOverlay()
            FloatingClockState.setRunning(this, true)
            FloatingClockState.setError(this, null)
            handler.removeCallbacks(updateClock)
            handler.post(updateClock)
        } catch (exception: IllegalStateException) {
            failStart("Android منع تشغيل خدمة الساعة العائمة.", exception)
        } catch (exception: SecurityException) {
            failStart("منع Android إنشاء نافذة الساعة العائمة بسبب الإذن.", exception)
        } catch (exception: WindowManager.BadTokenException) {
            failStart("تعذر إنشاء نافذة الساعة العائمة.", exception)
        } catch (exception: IllegalArgumentException) {
            failStart("تعذر إنشاء نافذة الساعة العائمة على هذا الجهاز.", exception)
        }
        return START_NOT_STICKY
    }

    private fun startAsForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showClockOverlay() {
        if (clockView != null) return
        val view = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(16, 35, 63))
                cornerRadius = 48f
                setStroke(2, android.graphics.Color.rgb(99, 242, 210))
            }
            elevation = 12f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }
        view.setOnTouchListener(DragListener(params))
        windowManager.addView(view, params)
        clockView = view
    }

    private fun clockText(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.forLanguageTag("ar")))

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, FloatingClockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("الساعة العائمة")
            .setContentText("تظهر الساعة فوق التطبيقات")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "إيقاف الساعة العائمة", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "الساعة العائمة",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "إشعار دائم أثناء عرض الساعة فوق التطبيقات"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun failStart(message: String, exception: Exception) {
        Log.e("FloatingClockService", message, exception)
        FloatingClockState.setError(this, message)
        FloatingClockState.setRunning(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateClock)
        clockView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (exception: IllegalArgumentException) {
                Log.w("FloatingClockService", "Overlay view was already detached", exception)
            } catch (exception: SecurityException) {
                Log.w("FloatingClockService", "Unable to remove overlay after permission changed", exception)
            }
        }
        clockView = null
        FloatingClockState.setRunning(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class DragListener(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    val manager = view.context.getSystemService(WindowManager::class.java)
                    manager.updateViewLayout(view, params)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    companion object {
        const val ACTION_SHOW = "com.example.clockalarm.action.SHOW_FLOATING_CLOCK"
        const val ACTION_STOP = "com.example.clockalarm.action.STOP_FLOATING_CLOCK"
        private const val CHANNEL_ID = "floating_clock"
        private const val NOTIFICATION_ID = 7102
    }
}
