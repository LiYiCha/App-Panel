package com.panel.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.panel.app.data.logger.LogStorage
import java.io.File

object LogDirOpener {

    private const val TAG = "LogDirOpener"

    fun open(context: Context) {
        val dir = LogStorage.logDir()
        runCatching { dir.mkdirs() }.onFailure { Log.w(TAG, "mkdirs failed: $it") }

        if (!dir.exists()) {
            Log.w(TAG, "日志目录尚未创建：${dir.absolutePath}")
            return
        }

        Log.d(TAG, "日志目录: ${dir.absolutePath}")

        // 使用 file:// URI 直接指向日志目录
        // external-media 目录（/Android/media/<包名>/）对所有文件管理器开放，无访问隔离
        val fileUri = android.net.Uri.fromFile(dir)
        val mimeTypes = listOf("resource/folder", "*/*", "vnd.android.document/directory")

        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            data = fileUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        for (mime in mimeTypes) {
            try {
                val chooser = Intent.createChooser(baseIntent.apply { type = mime }, "打开日志目录")
                context.startActivity(chooser)
                Log.d(TAG, "成功弹出选择框 mime=$mime")
                return
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "未找到支持 $mime 的应用，继续尝试...")
            } catch (e: Exception) {
                Log.w(TAG, "打开失败 mime=$mime: ${e.message}")
            }
        }

        Log.w(TAG, "所有文件管理器均无法打开：${dir.absolutePath}")
    }
}
