package com.panel.app.data.local

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.panel.app.MainActivity
import com.panel.app.PanelApp
import com.panel.app.R
import java.io.File

class LocalBaihuDaemonService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var daemonProcess: Process? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startDaemonProcess()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createDaemonNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 保持 CPU 微秒级唤醒，避开 Doze 休眠挂起
        }
    }

    /**
     * 运行本地白虎 Go 二进制
     * 关键解决策：使用 Context.applicationInfo.nativeLibraryDir 中的 libbaihu.so，绕过 Android 10+ W^X (Permission Denied) 拦截！
     */
    private fun startDaemonProcess() {
        try {
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val baihuBinary = File(nativeLibDir, "libbaihu.so")

            val executableFile = if (baihuBinary.exists()) baihuBinary else File(filesDir, "baihu")
            if (!executableFile.exists()) {
                android.util.Log.w("LocalBaihuDaemon", "未检测到本地 libbaihu.so 二进制文件，守护服务保持心跳等待外部或Termux引擎连接")
                return
            }

            val pb = ProcessBuilder(executableFile.absolutePath, "server")
                .directory(filesDir)
                .redirectErrorStream(true)

            daemonProcess = pb.start()
        } catch (e: Exception) {
            android.util.Log.e("LocalBaihuDaemon", "启动本地白虎进程异常: ${e.message}")
        }
    }

    private fun createDaemonNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, PanelApp.CHANNEL_ID_DAEMON)
            .setContentTitle("白虎面板守护引擎正在运行")
            .setContentText("127.0.0.1:5700 • CPU < 0.5% • 定时任务秒级高精触发")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        daemonProcess?.destroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 2891
        const val WAKE_LOCK_TAG = "PanelApp:LocalBaihuDaemonWakeLock"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, LocalBaihuDaemonService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
