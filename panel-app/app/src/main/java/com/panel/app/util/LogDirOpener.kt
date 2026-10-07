package com.panel.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
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

        // 使用 FileProvider 生成 content:// URI，避免 file:// 在 Android 7.0+ 触发 FileUriExposedException
        // 同时避免 *//* MIME 类型触发 APK 安装器
        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dir
            )
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider 生成 URI 失败: ${e.message}")
            return
        }

        // 优先使用 resource/folder，回退到 vnd.android.document.directory
        val mimeTypes = listOf("resource/folder", "vnd.android.document/directory")

        for (mime in mimeTypes) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "打开日志目录")
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
