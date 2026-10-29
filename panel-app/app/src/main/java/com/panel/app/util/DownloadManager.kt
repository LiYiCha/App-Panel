package com.panel.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 应用内 APK 下载管理器（多任务模型）。
 *
 * 特性：
 *  - 多任务并行：每个 URL 一个独立任务槽，互不干扰（standard/full 可同时下载）
 *  - 按资产名落盘：输出文件为 <cacheDir>/apk/<资产名>，同资产同名支持断点续传
 *  - 断点续传（HTTP Range / 206）；本地文件已完整时（416 Range 超界）直接判为成功
 *  - 实时进度、网速回调；失败自动重试（指数退避，最多 3 次），镜像耗尽回退 GitHub 直链
 *  - 取消精确到任务本身，只清理该任务的临时文件
 */
object DownloadManager {

    private const val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val GITHUB_UA: String by lazy {
        val osVersion = android.os.Build.VERSION.RELEASE
        val model = android.os.Build.MODEL
        "Mozilla/5.0 (Linux; Android $osVersion; $model) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private const val CHUNK_SIZE = 64 * 1024          // 64 KB
    internal const val MAX_RETRIES = 3
    private const val RETRY_BASE_DELAY_MS = 1_000L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 单个下载任务：URL → 协程 + 输出文件 */
    private class Task(val url: String, val file: File, val job: Job)

    /** 任务表：url → 任务；不同 URL 并行互不干扰 */
    private val tasks = ConcurrentHashMap<String, Task>()

    // ── 公开 API ────────────────────────────────────────────────────────────

    /**
     * 下载输出文件：以 Release 资产原始文件名落盘（apk/Panel-v3.4.0-standard.apk）。
     * 文件名即来源标识，同资产同名 → 天然支持断点续传与缓存识别，无需额外 .url 记录。
     */
    fun outputFileFor(context: Context, url: String): File {
        val rawName = url.substringBefore('?').substringAfterLast('/')
        val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "update.apk" }
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        return File(dir, safeName)
    }

    /** 指定 URL 的任务是否进行中 */
    fun isActive(url: String): Boolean = tasks[url]?.job?.isActive == true

    /**
     * 开始下载。
     *
     * 同一 URL 已在下载中时幂等返回（防重复点击）；不同 URL 各自独立任务，可并行。
     *
     * @param url         下载地址（GitHub Release 直链，内部自动应用镜像加速）
     * @param outputFile  输出路径（建议用 [outputFileFor] 按资产名生成）
     * @param onProgress  进度回调（已下载/总量/百分比/网速字节每秒）
     * @param onSuccess   完成回调（传出 File）
     * @param onFailure   失败回调（原因消息）
     * @param onRetry     每次重试前回调（可用来刷新 UI）
     */
    fun download(
        url: String,
        outputFile: File,
        onProgress: (bytes: Long, total: Long, percent: Int, speed: Long) -> Unit,
        onSuccess: (File) -> Unit,
        onFailure: (String) -> Unit,
        onRetry: () -> Unit = {},
    ) {
        if (tasks[url]?.job?.isActive == true) return
        val job = scope.launch {
            try {
                doDownload(url, outputFile, onProgress, onSuccess, onFailure, onRetry)
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    runCatching { outputFile.delete() }
                    runCatching { onFailure(t.message ?: "下载失败") }
                }
            } finally {
                tasks.remove(url)
            }
        }
        tasks[url] = Task(url, outputFile, job)
    }

    /** 取消指定任务，只清理该任务的临时文件 */
    fun cancel(url: String) {
        tasks.remove(url)?.let { t ->
            t.job.cancel()
            runCatching { t.file.delete() }
        }
    }

    // ── 内部实现 ────────────────────────────────────────────────────────────

    /**
     * 单任务执行体：镜像加速 → 重试（指数退避）→ 回退直链，循环直至成功/失败/取消。
     */
    private suspend fun doDownload(
        url: String,
        file: File,
        onProgress: (Long, Long, Int, Long) -> Unit,
        onSuccess: (File) -> Unit,
        onFailure: (String) -> Unit,
        onRetry: () -> Unit,
    ) {
        val startUrl = MirrorManager.applyUpdate(url)
        val fallbackUrl = if (startUrl != url) url else null
        var effectiveUrl = startUrl
        var usedFallback = false
        var retries = 0

        while (true) {
            // 断点续传：从已有文件长度继续；完整文件会得到 416，按「已完整」处理
            var downloaded = if (file.exists() && file.length() > 0) file.length() else 0L
            var lastTs = System.currentTimeMillis()
            var lastBytes = downloaded
            var response: okhttp3.Response? = null
            try {
                val requestBuilder = Request.Builder().url(effectiveUrl).header("User-Agent", GITHUB_UA)
                if (downloaded > 0L) requestBuilder.header("Range", "bytes=$downloaded-")
                response = httpClient.newCall(requestBuilder.build()).execute()
                when (response.code) {
                    200 -> {
                        // 全新下载：清空旧文件
                        downloaded = 0L
                        lastBytes = 0L
                        file.parentFile?.mkdirs()
                        if (file.exists()) file.delete()
                        if (!withContext(Dispatchers.IO) {
                                file.createNewFile()
                            }) throw IllegalStateException("无法创建下载文件")
                        val responseBody = response.body ?: throw IllegalStateException("下载响应无内容")
                        val total = responseBody.contentLength()
                        streamChunks(responseBody, file, 0L) { _, bytesRead ->
                            downloaded += bytesRead
                            val now = System.currentTimeMillis()
                            val dt = max((now - lastTs) / 1000.0, 0.001)
                            val speed = ((downloaded - lastBytes) / dt).toLong()
                            lastTs = now
                            lastBytes = downloaded
                            val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                            onProgress(downloaded, total, max(0, min(pct, 100)), speed)
                        }
                    }
                    206 -> {
                        // 续传：追加到已有文件
                        val responseBody = response.body ?: throw IllegalStateException("下载响应无内容")
                        val total = (downloaded + responseBody.contentLength()).coerceAtLeast(-1L)
                        streamChunks(responseBody, file, downloaded) { _, bytesRead ->
                            downloaded += bytesRead
                            val now = System.currentTimeMillis()
                            val dt = max((now - lastTs) / 1000.0, 0.001)
                            val speed = ((downloaded - lastBytes) / dt).toLong()
                            lastTs = now
                            lastBytes = downloaded
                            val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                            onProgress(downloaded, total, max(0, min(pct, 100)), speed)
                        }
                    }
                    416 -> {
                        // Range 超界 = 本地文件已完整（续传场景），直接判为成功
                        if (downloaded > 0L && file.exists() && file.length() > 0L) {
                            file.setExecutable(true, false)
                            runCatching { onSuccess(file) }
                                .onFailure { Log.e(TAG, "onSuccess 回调异常（不影响已完成的文件）", it) }
                            return
                        }
                        throw IOException("下载失败 HTTP 416")
                    }
                    403 -> throw IOException("下载被拒绝 (403)，GitHub 可能已限流，请稍后重试")
                    404 -> throw IOException("APK 文件不存在 (404)，请联系开发者检查 Release 配置")
                    else -> throw IOException("下载失败 HTTP ${response.code}")
                }

                // ── 成功 ──────────────────────────────────────────────────
                file.setExecutable(true, false)
                // 回调异常只记日志：文件已完整落盘，绝不能因 UI 层问题进入重试/删除流程
                runCatching { onSuccess(file) }
                    .onFailure { Log.e(TAG, "onSuccess 回调异常（不影响已完成的文件）", it) }
                return

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                retries++
                if (retries <= MAX_RETRIES) {
                    delay(RETRY_BASE_DELAY_MS * (1L shl (retries - 1))) // 1s / 2s / 4s
                    onRetry()
                    continue
                }
                // 重试耗尽：镜像源失败回退 GitHub 直链重来（清空进度从头下）
                if (fallbackUrl != null && !usedFallback) {
                    usedFallback = true
                    effectiveUrl = fallbackUrl
                    retries = 0
                    runCatching { file.delete() }
                    delay(RETRY_BASE_DELAY_MS)
                    onRetry()
                    continue
                }
                runCatching { file.delete() }
                onFailure(e.message ?: "下载失败")
                return
            } finally {
                response?.close()
            }
        }
    }

    /**
     * 触发系统安装器打开已下载的 APK，并在安装后自动清理临时文件。
     *
     * Android 8.0+ 中普通 App 无法获取 REQUEST_INSTALL_PACKAGES 签名权限，
     * 改用 ACTION_INSTALL_PACKAGE Intent + FLAG_REQUEST_INSTALL_PACKAGES，
     * 失败时引导用户到系统"安装未知应用"设置页。
     */
    fun installAndCleanup(context: android.content.Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.w(TAG, "installAndCleanup: APK 文件不存在 ${apkFile.absolutePath}")
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
                )
                putExtra(android.content.Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // 尝试打开系统"安装未知应用"设置页
            Log.w(TAG, "installAndCleanup: 直接安装失败，尝试引导至系统设置")
            try {
                val settingsIntent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                ).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                android.widget.Toast.makeText(
                    context,
                    "请在设置中允许本应用安装未知应用，然后重试",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e2: Exception) {
                android.widget.Toast.makeText(
                    context, "安装失败：请在系统设置 → 应用 → 特殊权限 → 安装未知应用中开启",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "installAndCleanup: 启动安装失败", e)
            android.widget.Toast.makeText(
                context, "启动安装失败: ${e.message}", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        // APK 保留在磁盘不自动删除，避免 Android VerifyApps 扫描时因文件消失导致安装失败
        // (ALLOW_LIST_DOWNLOAD_FILE_NOT_FOUND_EXCEPTION)
        // 用户可在设置页手动清理缓存 APK
    }

    /**
     * 以固定大小分块读取响应体并追加写入文件。
     *
     * @param body      OkHttp 响应体
     * @param file      目标 APK 文件
     * @param offset    追加偏移量（续传时使用）
     * @param onChunk   每读到一个 chunk 后的回调（传入 chunk 数组和实际读取字节数）
     */
    private suspend fun streamChunks(
        body: okhttp3.ResponseBody,
        file: File,
        offset: Long,
        onChunk: (ByteArray, Int) -> Unit,
    ) {
        val options = if (offset > 0L)
            listOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
        else
            listOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        val fc = FileChannel.open(file.toPath(), *options.toTypedArray())
            .apply { if (offset > 0L) position(offset) }
        val out = Channels.newOutputStream(fc)
        val buf = ByteArray(CHUNK_SIZE)
        var n: Int
        try {
            val src = body.source()
            while (src.read(buf).also { n = it } != -1) {
                // 纯阻塞 IO 无挂起点，必须主动检查取消，否则取消下载后仍会偷偷跑完
                currentCoroutineContext().ensureActive()
                out.write(buf, 0, n)
                onChunk(buf, n)
            }
            src.close()
        } finally {
            out.close()
            fc.close()
        }
    }
}
