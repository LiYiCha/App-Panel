package com.panel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PanelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化日志落盘目录并回灌上次会话的日志尾部（冷启动也能看到上次崩溃）
        com.panel.app.data.logger.AppLogger.init(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 关键：崩溃日志必须同步写文件。
            // 以前只写内存列表，进程一死就没了，所以控制台永远查不到崩溃原因
            com.panel.app.data.logger.AppLogger.recordCrash(thread, throwable)
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
