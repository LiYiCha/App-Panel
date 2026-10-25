package com.panel.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 用第三方文件管理器打开目录（照搬 Sesame-TK 已验证可用的 FolderLauncher 方案）：
 * 不对目录本身发 content:// URI —— 目录 URI 配上非标准 MIME（resource/folder）会被
 * 注册了 application/* 通配的 APK 安装器抢走；改为对目录内最新一个真实文件生成
 * FileProvider URI + */* MIME + ClipData，弹出系统"选择其他应用打开"面板。
 */
object FolderLauncher {

    private const val TAG = "FolderLauncher"

    fun open(context: Context, path: String) {
        val targetFile = File(path).candidateFile()
        if (targetFile == null) {
            Log.w(TAG, "目录内无可用文件: $path")
            Toast.makeText(context, "目录为空: $path", Toast.LENGTH_LONG).show()
            return
        }

        val contentUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "生成 URI 失败: ${e.message}")
            Toast.makeText(context, "路径: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "*/*")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
            clipData = ClipData.newRawUri(targetFile.name, contentUri)
        }

        try {
            val chooser = Intent.createChooser(intent, "选择其他应用打开")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "无可用应用: ${e.message}")
            Toast.makeText(context, "路径: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "选择应用打开失败: ${e.message}")
            Toast.makeText(context, "路径: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * FileProvider 只能对实际文件生成可用 URI：目录内有文件则取最新的一个，
     * 只有子目录时递归取子目录内最新的一个，全为空时返回 null。
     */
    private fun File.candidateFile(): File? {
        val files = listFiles() ?: return null
        files.filter { it.isFile }
            .maxByOrNull { it.lastModified() }
            ?.let { return it }
        return files.filter { it.isDirectory }.firstNotNullOfOrNull { it.candidateFile() }
    }
}
