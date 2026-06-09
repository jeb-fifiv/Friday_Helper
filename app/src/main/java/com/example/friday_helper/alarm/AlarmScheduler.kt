package com.example.friday_helper.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.Locale

/** Ставит будильник на заданное время; при срабатывании показывается уведомление. */
object AlarmScheduler {

    private const val ACTION_RING = "com.example.friday_helper.alarm.RING"
    private const val EXTRA_LABEL = "label"
    private const val EXTRA_REQUEST_ID = "request_id"
    /** Уникальный requestCode для «открыть приложение» при нажатии на будильник в системной панели. */
    private const val REQUEST_CODE_ALARM_SHOW = 0x0A17A000

    fun schedule(context: Context, triggerTimeMillis: Long, label: String) {
        val app = context.applicationContext
        val requestId = (triggerTimeMillis and 0x7FFFFFFF).toInt()
        val intent = Intent(app, AlarmReceiver::class.java).apply {
            setPackage(app.packageName)
            action = ACTION_RING
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_REQUEST_ID, requestId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(app, requestId, intent, flags)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = Intent(app, com.example.friday_helper.MainActivity::class.java).apply {
                setPackage(app.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            val showPending = PendingIntent.getActivity(app, REQUEST_CODE_ALARM_SHOW, showIntent, flags)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTimeMillis, showPending), pending)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pending)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pending)
            }
        }
    }

    /** Время в миллисекундах (RTC) для сегодня или завтра в HH:mm. */
    fun triggerTimeAt(hour: Int, minute: Int, useTomorrowIfPassed: Boolean = true): Long {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (useTomorrowIfPassed && cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun extractLabel(intent: Intent?): String = intent?.getStringExtra(EXTRA_LABEL) ?: ""
}
