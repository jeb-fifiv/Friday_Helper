package com.example.friday_helper.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.Locale

/**
 * Планирует и отменяет уведомления для задач с напоминаниями.
 * Использует AlarmManager для точного срабатывания.
 */
object TaskNotificationScheduler {

    private const val ACTION_NOTIFY = "com.example.friday_helper.tasks.NOTIFY"
    private const val EXTRA_TASK_ID = "task_id"
    private const val EXTRA_TASK_TITLE = "task_title"
    private const val EXTRA_REMINDER_TYPE = "reminder_type"
    private const val EXTRA_REMINDER_PARAM = "reminder_param"

    fun schedule(context: Context, task: TaskEntity) {
        val app = context.applicationContext
        if (task.reminderType == REMINDER_NONE) {
            cancel(app, task.id)
            return
        }
        var triggerTime = nextTriggerTime(task) ?: return
        val now = System.currentTimeMillis()
        if (triggerTime <= now) {
            triggerTime = now + 60 * 1000L
        }

        val intent = Intent(app, TaskNotificationReceiver::class.java).apply {
            action = ACTION_NOTIFY
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_REMINDER_TYPE, task.reminderType)
            putExtra(EXTRA_REMINDER_PARAM, task.reminderParam)
        }
        val requestCode = task.id.toInt() and 0x7FFFFFFF
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(app, requestCode, intent, flags)

        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = Intent(app, TasksActivity::class.java).apply {
                putExtra(TasksActivity.EXTRA_OPEN_TASK_ID, task.id)
            }
            val showPending = PendingIntent.getActivity(app, requestCode + 10000, showIntent, flags)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, showPending), pending)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pending)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val app = context.applicationContext
        val intent = Intent(app, TaskNotificationReceiver::class.java).apply {
            action = ACTION_NOTIFY
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val requestCode = taskId.toInt() and 0x7FFFFFFF
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(app, requestCode, intent, flags)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
    }

    /** Время следующего срабатывания в millis (RTC). */
    private fun nextTriggerTime(task: TaskEntity): Long? {
        val cal = Calendar.getInstance(Locale.getDefault())
        when (task.reminderType) {
            REMINDER_ONCE -> return task.reminderParam
            REMINDER_DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, task.dueTimeMinutes / 60)
                cal.set(Calendar.MINUTE, task.dueTimeMinutes % 60)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis())
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                return cal.timeInMillis
            }
            REMINDER_EVERY_2_DAYS -> return nextEveryNDays(task.dueDateMillis, task.dueTimeMinutes, 2)
            REMINDER_EVERY_3_DAYS -> return nextEveryNDays(task.dueDateMillis, task.dueTimeMinutes, 3)
            REMINDER_EVERY_WEEK -> return nextEveryNDays(task.dueDateMillis, task.dueTimeMinutes, 7)
            REMINDER_EVERY_N_DAYS -> {
                val n = task.reminderParam.coerceIn(1L, 365L).toInt()
                return nextEveryNDays(task.dueDateMillis, task.dueTimeMinutes, n)
            }
            REMINDER_EVERY_N_HOURS -> {
                val n = task.reminderParam.coerceIn(1L, 720L).toInt() // до 30 дней в часах
                cal.add(Calendar.HOUR_OF_DAY, n)
                return cal.timeInMillis
            }
            else -> return null
        }
    }

    private val DAY_MS = 24 * 60 * 60 * 1000L
    private fun nextEveryNDays(dayStartMillis: Long, timeMinutes: Int, days: Int): Long {
        val firstSlot = dayStartMillis + timeMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        if (firstSlot > now) return firstSlot
        val period = days * DAY_MS
        val elapsed = now - firstSlot
        val k = (elapsed / period).toInt() + 1
        return firstSlot + k * period
    }

    fun extractTaskId(intent: Intent?): Long =
        intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
    fun extractTaskTitle(intent: Intent?): String =
        intent?.getStringExtra(EXTRA_TASK_TITLE) ?: ""
    fun extractReminderType(intent: Intent?): String =
        intent?.getStringExtra(EXTRA_REMINDER_TYPE) ?: REMINDER_NONE
    fun extractReminderParam(intent: Intent?): Long =
        intent?.getLongExtra(EXTRA_REMINDER_PARAM, 0L) ?: 0L
}
