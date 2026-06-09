package com.example.friday_helper.tasks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.friday_helper.R

class TaskNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.example.friday_helper.tasks.NOTIFY") return
        val taskId = TaskNotificationScheduler.extractTaskId(intent)
        val taskTitle = TaskNotificationScheduler.extractTaskTitle(intent)
        if (taskId < 0) return

        createChannel(context)
        val openIntent = Intent(context, TasksActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TasksActivity.EXTRA_OPEN_TASK_ID, taskId)
        }
        val requestCode = taskId.toInt() and 0x7FFFFFFF
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingOpen = PendingIntent.getActivity(context, requestCode, openIntent, pendingFlags)

        val title = context.getString(R.string.tasks_notification_title)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(taskTitle.ifEmpty { context.getString(R.string.title_tasks) })
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (_: SecurityException) {}

        // Повторяющееся напоминание: запланировать следующее
        val reminderType = TaskNotificationScheduler.extractReminderType(intent)
        if (reminderType != REMINDER_NONE && reminderType != REMINDER_ONCE) {
            Thread {
                try {
                    val db = TasksDatabase.getInstance(context)
                    val task = db.taskDao().getTaskById(taskId)
                    if (task != null && task.reminderType != REMINDER_NONE) {
                        TaskNotificationScheduler.schedule(context, task)
                    }
                } catch (_: Exception) {}
            }.start()
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tasks_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
    }
}
