package com.panel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.panel.app.data.logger.AppLogger
import com.panel.app.script.LocalScriptFeature
import com.panel.app.util.MirrorManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PanelApp : Application() {

    @Inject
    lateinit var localScriptFeature: LocalScriptFeature

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // 初始化日志落盘目录并回灌上次会话的日志尾部（冷启动也能看到上次崩溃）
        AppLogger.init(this)
        // 下载加速镜像管理中心（须先于 LocalScriptKit，PyPI 镜像 provider 依赖其 SP）
        MirrorManager.init(this)
        // 本地脚本引擎（JS/Python）与依赖管理装配（standard 变体为空实现）
        localScriptFeature.init(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 关键：崩溃日志必须同步写文件。
            // 以前只写内存列表，进程一死就没了，所以控制台永远查不到崩溃原因
            AppLogger.recordCrash(thread, throwable)
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

        /** 全局 Application Context（仅持有 Application 自身，无 Activity 泄漏风险）。
         *  供无 Hilt 注入的底层类（如适配器能力表持久化）安全获取 SharedPreferences */
        @Suppress("StaticFieldLeak")
        lateinit var appContext: android.content.Context
            private set
    }
}
