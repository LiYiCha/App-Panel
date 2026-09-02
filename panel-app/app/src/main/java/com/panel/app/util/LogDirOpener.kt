package com.panel.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.panel.app.data.logger.LogStorage
import java.io.File

/**
 * 用第三方文件管理器打开日志目录。
 *
 * ### 为什么要这么绕
 * Android 7 之后 `file://` URI 禁止直接传给其他 App（会抛 FileUriExposedException），
 * 必须经 FileProvider 换成 `content://`。而各家文件管理器注册的 MIME 不同：
 *  - 系统文件管理器 / DocumentsUI 认 `vnd.android.document/directory`
 *  - MT 管理器等第三方管理器认 `resource/folder`
 * 所以按顺序逐个尝试，全部失败时把路径复制到剪贴板兜底。
 *
 * 日志目录首选 `/sdcard/Android/media/<包名>/files/logs`，
 * 这个目录没有任何访问隔离，MT 管理器可直接进入。
 */
object LogDirOpener {

    /** 返回 null 表示已发起跳转；否则返回可直接展示给用户的提示文案 */
    fun open(context: Context): String? {
        val dir = LogStorage.logDir()
        runCatching { dir.mkdirs() }
        if (!dir.exists()) {
            return "日志目录尚未创建，先产生一条日志后再试"
        }

        val authority = "${context.packageName}.fileprovider"
        val dirUri = runCatching {
            FileProvider.getUriForFile(context, authority, dir)
        }.getOrNull()

        val candidates = buildList {
            if (dirUri != null) {
                add(viewIntent(dirUri, "vnd.android.document/directory"))
                add(viewIntent(dirUri, "resource/folder"))
            }
            // 兜底：直接打开今天这份日志文件
            val todayFile = File(dir, LogStorage.currentFileName())
            if (todayFile.exists()) {
                val fileUri = runCatching { FileProvider.getUriForFile(context, authority, todayFile) }.getOrNull()
                if (fileUri != null) add(viewIntent(fileUri, "text/plain"))
            }
        }

        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return null
            } catch (_: ActivityNotFoundException) {
                // 换下一个
            } catch (_: SecurityException) {
                // 换下一个
            }
        }

        // 全部失败：把绝对路径给到剪贴板，用户可在任意文件管理器手动进入
        copyToClipboard(context, dir.absolutePath)
        return "未找到可用的文件管理器，已把日志目录路径复制到剪贴板：\n${dir.absolutePath}"
    }

    private fun viewIntent(uri: Uri, mime: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // 显式授权，避免部分 ROM 上 FLAG_GRANT 对目录不生效
        clipData = ClipData.newRawUri("panel_log_dir", uri)
    }

    private fun copyToClipboard(context: Context, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.setPrimaryClip(ClipData.newPlainText("panel_log_dir", text))
        }
    }
}
