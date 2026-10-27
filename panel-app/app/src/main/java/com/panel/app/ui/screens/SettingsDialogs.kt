package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.BuildConfig
import com.panel.app.data.model.PanelInstance
import com.panel.app.data.model.PanelType
import com.panel.app.ui.viewmodel.MainViewModel
import com.panel.app.util.AppUpdateInfo
import com.panel.app.util.AppUpdateManager
import com.panel.app.util.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadStateHolder(
    var downloadUrl: String? = null,
    var dlState: DownloadManager.State? = null,
    var dlDownloaded: Long = 0L,
    var dlTotal: Long = 0L,
    var dlPercent: Int = 0,
    var dlSpeed: Long = 0L,
    var dlRetries: Int = 0,
    var dlError: String? = null,
    var dlDone: Boolean = false,
    var dlDownloading: Boolean = false,
)

/** 一类可单独清理的应用缓存 */
data class AppCacheEntry(
    val title: String,
    val desc: String,
    val sizeBytes: Long,
    val clear: () -> Unit,
)

// 分类盘点应用缓存：更新安装包 / 面板数据快照 / 仪表盘缓存 / 其他临时文件，各自可独立清理
fun collectAppCacheEntries(context: android.content.Context): List<AppCacheEntry> {
    val apkFiles = listOf(
        File(context.cacheDir, "Panel-App-download.apk"),
        File(context.cacheDir, "Panel-App-download.apk.url")
    )
    val dataCacheDir = File(context.filesDir, "cache")
    val allDataFiles = dataCacheDir.listFiles()?.toList() ?: emptyList()
    val snapshotFiles = allDataFiles.filter { it.name.startsWith("panel_cache_") }
    val dashboardFiles = allDataFiles.filter { it.name.startsWith("dashboard_cache_") }
    val knownApkNames = apkFiles.map { it.name }.toSet()
    val otherFiles = mutableListOf<File>()
    context.cacheDir.listFiles()?.forEach { if (it.name !in knownApkNames) otherFiles.add(it) }
    context.externalCacheDir?.listFiles()?.forEach { otherFiles.add(it) }

    fun sizeOf(files: List<File>) = files.filter { it.exists() }.sumOf { if (it.isDirectory) it.walk().filter { f -> f.isFile }.sumOf { f -> f.length() } else it.length() }

    return listOf(
        AppCacheEntry("更新安装包缓存", "应用更新下载的 APK 安装包", sizeOf(apkFiles)) { apkFiles.forEach { it.delete() } },
        AppCacheEntry("面板数据快照", "任务/变量离线快照（重新进入面板后自动重建）", sizeOf(snapshotFiles)) { snapshotFiles.forEach { it.delete() } },
        AppCacheEntry("仪表盘缓存", "面板仪表盘离线数据", sizeOf(dashboardFiles)) { dashboardFiles.forEach { it.delete() } },
        AppCacheEntry("其他临时缓存", "网络/Web 运行时临时文件", sizeOf(otherFiles)) {
            otherFiles.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
        }
    )
}

// 版本更新弹窗（自治组件：内部管理下载状态/协程/缓存 APK 校验）
@Composable
fun UpdateCheckDialog(info: AppUpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dlHolder = remember { mutableStateOf(DownloadStateHolder()) }
    var dlJob by remember { mutableStateOf<Job?>(null) }
        // 检查缓存目录是否已有已下载的 APK，允许直接安装无需重新下载
        // 必须校验下载来源 URL：旧版本 APK 缓存不能用于新版安装，否则陷入"安装了还是旧包"的死循环
        val cachedApkFile = File(context.cacheDir, "Panel-App-download.apk")
        val cachedUrlFile = File(context.cacheDir, "Panel-App-download.apk.url")
        val cachedApk = if (cachedApkFile.exists() && cachedApkFile.length() > 0
            && cachedUrlFile.exists() && cachedUrlFile.readText() == info.downloadUrl
        ) cachedApkFile else {
            cachedApkFile.delete()
            cachedUrlFile.delete()
            null
        }
        AlertDialog(
            onDismissRequest = {
                dlJob?.cancel()
                if (dlHolder.value.dlState?.status == DownloadManager.State.Status.DOWNLOADING) {
                    DownloadManager.cancel(dlHolder.value.downloadUrl ?: "")
                }
                onDismiss()
            },
            title = {
                Text(
                    when {
                        cachedApk != null && !dlHolder.value.dlDone -> "APK 已就绪"
                        dlHolder.value.dlState != null && dlHolder.value.dlState!!.status == DownloadManager.State.Status.DONE -> "下载完成"
                        dlHolder.value.dlError != null -> "下载失败"
                        else -> if (info.hasUpdate) "发现新版本 ${info.latestVersion}" else "当前已是最新版本"
                    },
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 版本信息（始终显示）
                    Text("当前: ${info.currentVersion}  |  最新: ${info.latestVersion}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (info.publishedAt.isNotBlank() && info.publishedAt != "--") {
                        Text("发布: ${info.publishedAt}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    if (cachedApk != null) {
                        val sizeStr = when {
                            cachedApk.length() >= 1024 * 1024 -> "${String.format("%.1f", cachedApk.length().toDouble() / 1024 / 1024)} MB"
                            cachedApk.length() >= 1024 -> "${String.format("%.1f", cachedApk.length().toDouble() / 1024)} KB"
                            else -> "${cachedApk.length()} B"
                        }
                        Text("缓存 APK: ${cachedApk.name} ($sizeStr)", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("更新说明:", fontSize = 11.sp, style = MaterialTheme.typography.titleSmall)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = info.releaseNotes,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                        )
                    }

                    // 下载进度卡（仅在有效状态下显示）
                    if (dlHolder.value.dlDownloading || dlHolder.value.dlState != null || dlHolder.value.dlPercent > 0 || dlHolder.value.dlError != null || dlHolder.value.dlDone) {
                        DownloadProgressCard(
                            downloaded = dlHolder.value.dlDownloaded,
                            total = dlHolder.value.dlTotal,
                            percent = dlHolder.value.dlPercent,
                            speed = dlHolder.value.dlSpeed,
                            retries = dlHolder.value.dlRetries,
                            isError = dlHolder.value.dlError != null,
                            isDone = dlHolder.value.dlDone,
                            isDownloading = dlHolder.value.dlDownloading
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    cachedApk != null && !dlHolder.value.dlDone -> {
                        // 已有缓存 APK，直接安装
                        Button(
                            onClick = {
                                // 现场重校验：缓存可能在弹窗打开期间被"清除缓存"删掉，
                                // 文件失效时静默 return 就是"点了好几下都没反应"的根源 —— 转为重新下载
                                if (cachedApkFile.exists() && cachedApkFile.length() > 0L) {
                                    DownloadManager.installAndCleanup(context, cachedApk)
                                    dlHolder.value = dlHolder.value.copy(dlDone = true)
                                    onDismiss()
                                } else {
                                    cachedApkFile.delete()
                                    cachedUrlFile.delete()
                                    Toast.makeText(context, "安装包缓存已失效，请点击下载", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("立即安装", fontSize = 12.sp)
                        }
                    }
                    dlHolder.value.dlError != null -> {
                        Button(
                            onClick = {
                                dlHolder.value = dlHolder.value.copy(dlError = null, dlRetries = 0)
                                dlHolder.value.downloadUrl?.let { url ->
                                    dlJob?.cancel()
                                    dlJob = coroutineScope.launch(Dispatchers.IO) {
                                        runDownload(context, url, dlHolder)
                                    }
                                }
                            }
                        ) { Text("重新下载", fontSize = 12.sp) }
                    }
                    dlHolder.value.dlDone -> {
                        Button(
                            onClick = {
                                val apkFile = dlHolder.value.dlState?.file ?: return@Button
                                DownloadManager.installAndCleanup(context, apkFile)
                                dlHolder.value = dlHolder.value.copy(dlDone = false, dlState = null)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("立即安装", fontSize = 12.sp)
                        }
                    }
                    !info.downloadUrl.isNullOrEmpty() -> {
                        Button(
                            onClick = {
                                dlJob?.cancel()
                                dlJob = coroutineScope.launch(Dispatchers.IO) {
                                    runDownload(context, info.downloadUrl, dlHolder)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("立即下载 APK", fontSize = 12.sp)
                        }
                    }
                    else -> {
                        Button(onClick = { onDismiss() }) { Text("我知道了", fontSize = 12.sp) }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dlJob?.cancel()
                        if (dlHolder.value.dlState?.status == DownloadManager.State.Status.DOWNLOADING) {
                            DownloadManager.cancel(dlHolder.value.downloadUrl ?: "")
                        }
                        resetDownloadState(dlHolder.value)
                        onDismiss()
                    }
                ) { Text("取消", fontSize = 12.sp) }
            }
        )
}

// ── 下载辅助函数 ────────────────────────────────
/**
 * 启动下载；回调使用 Handler 切回主线程更新 holder，确保 Compose MutableState 在主线程写入。
 */
fun runDownload(
    context: android.content.Context,
    url: String,
    holder: MutableState<DownloadStateHolder>,
) {
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    val apkFile = File(context.cacheDir, "Panel-App-download.apk")

    // 重置状态并开始下载（主线程）
    mainHandler.post {
        holder.value = holder.value.copy(
            downloadUrl = url, dlDone = false, dlError = null, dlRetries = 0,
            dlPercent = 0, dlSpeed = 0L, dlDownloading = true
        )
    }

    DownloadManager.download(
        url = url,
        outputFile = apkFile,
        onProgress = { bytes, total, percent, speed ->
            mainHandler.post {
                holder.value = holder.value.copy(dlDownloaded = bytes, dlTotal = total, dlPercent = percent, dlSpeed = speed)
            }
        },
        onSuccess = { file ->
            File(context.cacheDir, "Panel-App-download.apk.url").writeText(url)
            mainHandler.post {
                holder.value = holder.value.copy(
                    dlDownloading = false, dlDone = true,
                    dlState = DownloadManager.State(
                        url = url, file = file, downloaded = file.length(),
                        total = file.length(), percent = 100, retries = 0,
                        status = DownloadManager.State.Status.DONE
                    )
                )
                Toast.makeText(context, "APK 下载完成，可以安装了", Toast.LENGTH_SHORT).show()
            }
        },
        onFailure = { msg ->
            mainHandler.post {
                holder.value = holder.value.copy(dlDownloading = false, dlError = msg, dlState = null)
                Toast.makeText(context, "下载失败: $msg", Toast.LENGTH_LONG).show()
            }
        },
        onRetry = {
            mainHandler.post {
                holder.value = holder.value.copy(dlRetries = holder.value.dlRetries + 1, dlError = null)
                Toast.makeText(
                    context,
                    "自动重试中... (${holder.value.dlRetries}/${DownloadManager.MAX_RETRIES})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )
}

fun resetDownloadState(stateHolder: DownloadStateHolder) {
    DownloadManager.cancel(stateHolder.downloadUrl ?: "")
}

@Composable
private fun DownloadProgressCard(
    downloaded: Long,
    total: Long,
    percent: Int,
    speed: Long,
    retries: Int,
    isError: Boolean,
    isDone: Boolean,
    isDownloading: Boolean = false,
) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = when {
                isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                isDone -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 头部：状态文字 + 百分比
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isError) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        } else if (isDone) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            text = when {
                                isError -> "下载失败"
                                isDone -> "下载完成"
                                else -> if (percent <= 0) "连接中..." else "正在下载 ${percent}%"
                            },
                            fontSize = 12.sp,
                            fontWeight = if (isDone) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isError -> MaterialTheme.colorScheme.error
                                isDone -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    if (speed > 0L) {
                        Text(formatSpeed(speed), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // 进度条
                LinearProgressIndicator(
                    progress = { if (total > 0) downloaded.toFloat() / total else (percent / 100f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        isDone -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                // 字节数 + 重试
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (total > 0) "${formatBytes(downloaded)} / ${formatBytes(total)}" else formatBytes(downloaded),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (retries > 0 && !isDone) {
                        Text("重试 $retries 次", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
}

fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec < 1024 -> "$bytesPerSec B/s"
    bytesPerSec < 1024 * 1024 -> "${"%.1f".format(bytesPerSec / 1024.0)} KB/s"
    else -> "${"%.2f".format(bytesPerSec / (1024.0 * 1024.0))} MB/s"
}
