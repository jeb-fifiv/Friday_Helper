package com.example.friday_helper.alarm

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

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.example.friday_helper.alarm.RING") return
        val app = context.applicationContext
        val label = AlarmScheduler.extractLabel(intent)
        createChannel(app)
        val openIntent = Intent(app, com.example.friday_helper.MainActivity::class.java).apply {
            setPackage(app.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val requestId = intent.getIntExtra("request_id", 0)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingOpen = PendingIntent.getActivity(app, requestId, openIntent, pendingFlags)
        val title = app.getString(R.string.alarm_notification_title)
        val text = if (label.isNotBlank()) label else app.getString(R.string.menu_alarm)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(requestId, notification)
        } catch (_: SecurityException) {}
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.enableVibration(true)
        channel.setShowBadge(true)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "alarm_clock"
    }
}
