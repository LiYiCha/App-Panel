package com.panel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PanelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stack = android.util.Log.getStackTraceString(throwable)
            android.util.Log.e("PanelApp", "Uncaught exception on thread ${thread.name}: $stack", throwable)
            com.panel.app.data.logger.AppLogger.log(
                level = com.panel.app.data.logger.LogLevel.ERROR,
                tag = "CRASH_GUARD",
                message = "全局捕获未处理异常 [${thread.name}]: ${throwable.message ?: throwable.javaClass.simpleName}",
                error = stack
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_ID_PANEL,
            "面板通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "任务执行结果与面板状态提醒"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID_PANEL = "channel_panel"
    }
}
