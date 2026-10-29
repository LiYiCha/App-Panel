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
import java.io.File

/**
 * 更新弹窗下载状态机（按 URL 一槽一状态，支持 standard/full 并行下载）：
 * Idle → Downloading → Done / Failed
 */
sealed class DownloadUiState {
    data class Downloading(
        val url: String,
        val downloaded: Long,
        val total: Long,
        val percent: Int,
        val speed: Long,
        val retries: Int,
    ) : DownloadUiState()

    data class Failed(val url: String, val message: String) : DownloadUiState()
    data class Done(val url: String, val file: File) : DownloadUiState()
}

/** 一类可单独清理的应用缓存 */
data class AppCacheEntry(
    val title: String,
    val desc: String,
    val sizeBytes: Long,
    val clear: () -> Unit,
)

// 分类盘点应用缓存：更新安装包 / 面板数据快照 / 仪表盘缓存 / 其他临时文件，各自可独立清理
fun collectAppCacheEntries(context: android.content.Context): List<AppCacheEntry> {
    // 更新安装包：按资产名落盘的 apk/ 目录（新版）+ 旧版固定名文件（迁移清理）
    val apkFiles = (File(context.cacheDir, "apk").listFiles()?.toList() ?: emptyList()) +
        listOf(
            File(context.cacheDir, "Panel-App-download.apk"),
            File(context.cacheDir, "Panel-App-download.apk.url")
        )
    val dataCacheDir = File(context.filesDir, "cache")
    val allDataFiles = dataCacheDir.listFiles()?.toList() ?: emptyList()
    val snapshotFiles = allDataFiles.filter { it.name.startsWith("panel_cache_") }
    val dashboardFiles = allDataFiles.filter { it.name.startsWith("dashboard_cache_") }
    val apkNames = apkFiles.map { it.name }.toSet()
    val otherFiles = mutableListOf<File>()
    context.cacheDir.listFiles()?.forEach { if (it.name !in apkNames && it.name != "apk") otherFiles.add(it) }
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

// 版本更新弹窗（自治组件：内部管理下载状态机/缓存 APK 识别）
@Composable
fun UpdateCheckDialog(info: AppUpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 下载状态表：url → 状态；standard/full 各自独立槽位，可并行下载
    var dlStates by remember { mutableStateOf<Map<String, DownloadUiState>>(emptyMap()) }
    // 缓存目录变更计数：任一下载完成后 +1，驱动缓存 APK 列表重组
    var cacheTick by remember { mutableIntStateOf(0) }

    // 缓存目录中属于当前 Release 的 APK（按资产名匹配，旧 Release 的文件不会误装）；
    // 优先返回匹配当前安装版本的资产，其余排后
    val matchedAssetName = info.downloadUrl?.substringBefore('?')?.substringAfterLast('/')
    val cachedApks = remember(info, cacheTick) {
        File(context.cacheDir, "apk").listFiles()
            ?.filter { it.isFile && it.length() > 0 && it.name in info.apkAssets.map { a -> a.name } }
            ?.sortedByDescending { it.name == matchedAssetName }
            .orEmpty()
    }

    fun startDownload(url: String) {
        if (DownloadManager.isActive(url)) return
        runDownload(context, url) { state ->
            if (state is DownloadUiState.Done) cacheTick++
            dlStates = dlStates + (url to state)
        }
    }
    fun cancelDownload(url: String) {
        DownloadManager.cancel(url)
        dlStates = dlStates - url
    }
    fun dismissCard(url: String) {
        dlStates = dlStates - url
    }

    val anyDownloading = dlStates.values.any { it is DownloadUiState.Downloading }
    val mainFailed = info.downloadUrl?.let { dlStates[it] } is DownloadUiState.Failed

    AlertDialog(
        onDismissRequest = {
            // 关闭弹窗：终止全部进行中的任务
            dlStates.keys.forEach { if (DownloadManager.isActive(it)) DownloadManager.cancel(it) }
            onDismiss()
        },
        title = {
            Text(
                when {
                    anyDownloading -> "正在下载"
                    cachedApks.isNotEmpty() -> "APK 已就绪"
                    dlStates.values.any { it is DownloadUiState.Failed } -> "下载失败"
                    else -> if (info.hasUpdate) "发现新版本 ${info.latestVersion}" else "当前已是最新版本"
                },
                fontSize = 15.sp
            )
        },
            text = {
                // 整体可滚动：更新说明较长时，保证 GitHub 链接、其他版本入口、进度卡不被裁切
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 版本信息（始终显示）
                    Text("当前: ${info.currentVersion}  |  最新: ${info.latestVersion}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (info.publishedAt.isNotBlank() && info.publishedAt != "--") {
                        Text("发布: ${info.publishedAt}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    // GitHub 仓库入口（点击跳转浏览器）
                    Surface(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(AppUpdateManager.GITHUB_REPO_URL))
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("GitHub 仓库", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text("github.com/LiYiCha/App-Panel", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                    }
                    // 已缓存的 Release APK（下载完成/历史下载的资产，可直接安装）
                    if (cachedApks.isNotEmpty()) {
                        Text(
                            if (cachedApks.size > 1) "已下载 ${cachedApks.size} 个安装包:" else "已下载安装包:",
                            fontSize = 11.sp,
                            style = MaterialTheme.typography.titleSmall
                        )
                        cachedApks.forEach { apk ->
                            Surface(
                                onClick = {
                                    // 现场重校验：缓存可能在弹窗打开期间被"清除缓存"删掉
                                    if (apk.exists() && apk.length() > 0L) {
                                        DownloadManager.installAndCleanup(context, apk)
                                        onDismiss()
                                    } else {
                                        cacheTick++
                                        Toast.makeText(context, "安装包缓存已失效，请重新下载", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(apk.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (apk.name == matchedAssetName) "当前版本 · 点击安装" else "另一版本 · 点击安装",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(formatApkSize(apk.length()), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    // 单个 APK 操作：分享（另存/传输）与删除
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "分享安装包",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp).clickable { shareApk(context, apk) }
                                    )
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除安装包",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(15.dp).clickable {
                                            runCatching { apk.delete() }
                                            cacheTick++
                                            Toast.makeText(context, "已删除 ${apk.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text("更新说明:", fontSize = 11.sp, style = MaterialTheme.typography.titleSmall)
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
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

                    // 全部 Release 资产下载入口（standard/full 各自独立，可并行下载）；
                    // 已在下载中或已缓存就绪的资产不重复显示
                    val downloadCards = info.apkAssets.filter { asset ->
                        val st = dlStates[asset.url]
                        st !is DownloadUiState.Downloading && asset.name !in cachedApks.map { it.name }
                    }
                    if (downloadCards.isNotEmpty()) {
                        downloadCards.forEach { apk ->
                            Surface(
                                onClick = { startDownload(apk.url) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(apk.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (apk.url == info.downloadUrl) "匹配当前版本 · ${formatApkSize(apk.sizeBytes)}" else "另一版本安装包 · ${formatApkSize(apk.sizeBytes)}",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text("下载", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    // 下载进度/失败卡（每 URL 一张，互不干扰；Done 状态由缓存 APK 卡片接管）
                    dlStates.forEach { (url, st) ->
                        when (st) {
                            is DownloadUiState.Downloading -> DownloadProgressCard(
                                downloaded = st.downloaded,
                                total = st.total,
                                percent = st.percent,
                                speed = st.speed,
                                retries = st.retries,
                                isError = false,
                                isDone = false,
                                isDownloading = true,
                                onCancel = { cancelDownload(url) }
                            )
                            is DownloadUiState.Failed -> DownloadProgressCard(
                                downloaded = 0, total = 0, percent = 0, speed = 0, retries = 0,
                                isError = true,
                                isDone = false,
                                onCancel = { dismissCard(url) }
                            )
                            is DownloadUiState.Done -> {}
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    // 有进行中的任务 → 只提供"后台/关闭"，不打断下载
                    anyDownloading -> {
                        Button(onClick = { onDismiss() }) { Text("后台下载", fontSize = 12.sp) }
                    }
                    // 有缓存 APK → 直接安装匹配当前版本的那个包
                    cachedApks.isNotEmpty() -> {
                        val target = cachedApks.first()
                        val editionLabel = when {
                            target.name.contains("full", ignoreCase = true) -> "安装完整版 (full)"
                            target.name.contains("standard", ignoreCase = true) -> "安装精简版 (standard)"
                            else -> "立即安装"
                        }
                        Button(
                            onClick = {
                                // 现场重校验：缓存可能在弹窗打开期间被"清除缓存"删掉
                                if (target.exists() && target.length() > 0L) {
                                    DownloadManager.installAndCleanup(context, target)
                                    onDismiss()
                                } else {
                                    cacheTick++
                                    Toast.makeText(context, "安装包缓存已失效，请重新下载", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(editionLabel, fontSize = 12.sp)
                        }
                    }
                    // 主下载入口失败 → 重试
                    mainFailed && info.downloadUrl != null -> {
                        val url = info.downloadUrl
                        Button(
                            onClick = {
                                dlStates = dlStates - url
                                startDownload(url)
                            }
                        ) { Text("重新下载", fontSize = 12.sp) }
                    }
                    // 常规下载
                    !info.downloadUrl.isNullOrEmpty() -> {
                        Button(
                            onClick = { startDownload(info.downloadUrl) }
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
                        // 取消：终止全部进行中的任务并清理
                        dlStates.keys.forEach { if (DownloadManager.isActive(it)) DownloadManager.cancel(it) }
                        onDismiss()
                    }
                ) { Text("取消", fontSize = 12.sp) }
            }
        )
}

// ── 下载辅助函数 ────────────────────────────────
/** APK 体积格式化：B / KB / MB */
private fun formatApkSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / 1024 / 1024)
    bytes >= 1024 -> String.format("%.1f KB", bytes.toDouble() / 1024)
    else -> "$bytes B"
}

/**
 * 通过 FileProvider 分享 APK（应用私有目录无法被文件管理器直接打开，
 * 分享是用户取出安装包的实际出口：可另存到任意位置或传输到其他设备）。
 */
private fun shareApk(context: android.content.Context, apk: File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(send, "分享安装包: ${apk.name}")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 启动指定 URL 的下载任务（可与其它 URL 的任务并行）。
 * 回调统一经 Handler 切回主线程，驱动弹窗状态机 [DownloadUiState]。
 * 输出文件按 Release 资产名落盘（apk/<资产名>），断点续传与缓存识别都以文件名为准。
 */
fun runDownload(
    context: android.content.Context,
    url: String,
    onState: (DownloadUiState) -> Unit,
) {
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var retries = 0
    var lastDownloaded = 0L
    var lastTotal = 0L
    var lastPercent = 0

    // 统一在主线程更新状态与 Toast（Toast 在无 Looper 的 IO 线程调用会抛异常，
    // 导致成功被误判为失败 → 重试 → 416 循环 → 文件被清理）
    fun notify(state: DownloadUiState, toast: String? = null) {
        mainHandler.post {
            onState(state)
            if (toast != null) android.widget.Toast.makeText(context, toast, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 初始 Downloading 状态（主线程）
    notify(DownloadUiState.Downloading(url, 0, 0, 0, 0, 0))

    DownloadManager.download(
        url = url,
        outputFile = DownloadManager.outputFileFor(context, url),
        onProgress = { bytes, total, percent, speed ->
            lastDownloaded = bytes; lastTotal = total; lastPercent = percent
            notify(DownloadUiState.Downloading(url, bytes, total, percent, speed, retries))
        },
        onRetry = {
            retries++
            notify(
                DownloadUiState.Downloading(url, lastDownloaded, lastTotal, lastPercent, 0, retries),
                "自动重试中... ($retries/${DownloadManager.MAX_RETRIES})"
            )
        },
        onSuccess = { file ->
            notify(DownloadUiState.Done(url, file), "APK 下载完成，可以安装了")
        },
        onFailure = { msg ->
            mainHandler.post {
                onState(DownloadUiState.Failed(url, msg))
                android.widget.Toast.makeText(context, "下载失败: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        },
    )
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
    onCancel: (() -> Unit)? = null,
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
                    // 下载中可手动取消
                    if (isDownloading && onCancel != null) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "取消下载",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp).clickable { onCancel() }
                        )
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
