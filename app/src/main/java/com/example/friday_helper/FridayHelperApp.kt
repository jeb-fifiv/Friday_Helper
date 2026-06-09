package com.example.friday_helper

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.example.friday_helper.security.SecuritySession
import com.example.friday_helper.tasks.TaskNotificationReceiver

class FridayHelperApp : Application(), Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        createTaskNotificationChannel()
    }

    private fun createTaskNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            TaskNotificationReceiver.CHANNEL_ID,
            getString(R.string.tasks_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0) {
            // App went to background -> require auth again next time.
            SecuritySession.clear()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

