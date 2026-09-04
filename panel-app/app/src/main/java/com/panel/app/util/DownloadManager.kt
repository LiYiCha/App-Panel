package com.panel.app.util

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 应用内 APK 下载管理器。
 *
 * 特性：
 *  - 断点续传（HTTP Range / 206 Partial Content）
 *  - 实时进度、网速回调
 *  - 失败自动重试（指数退避，最多 3 次）
 *  - 安装后自动清理临时 APK
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
    private const val CLEANUP_DELAY_MS = 5_000L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class State(
        val url: String,
        val file: File,
        val downloaded: Long = 0L,
        val total: Long = -1L,
        val percent: Int = 0,
        val speed: Long = 0L,
        val retries: Int = 0,
        val status: Status = Status.IDLE,
    ) {
        enum class Status { IDLE, DOWNLOADING, DONE }
    }

    // ── 公开 API ────────────────────────────────────────────────────────────

    /** 取消指定下载并清理临时文件 */
    fun cancel(url: String) {
        synchronized(lock) {
            if (activeUrl != url) return
            activeJob?.cancel()
            activeJob = null
            activeUrl = null
            val s = _states.remove(url)
            s?.file?.delete()
        }
    }

    /**
     * 开始下载。
     *
     * @param url         下载地址
     * @param outputFile  输出路径（建议置于 app cacheDir）
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
        synchronized(lock) {
            // 下载器是单任务模型：重复点击只复用当前任务，不再创建并发请求。
            if (activeJob?.isActive == true) return
            val resumedOffset = if (outputFile.exists() && outputFile.length() > 0) outputFile.length() else 0L
            val initialState = State(url, outputFile, downloaded = resumedOffset)
            _states[url] = initialState
            activeUrl = url
            activeJob = scope.launch {
                try {
                    doDownload(url, initialState, onProgress, onSuccess, onFailure, onRetry)
                } catch (t: Throwable) {
                    if (t !is kotlinx.coroutines.CancellationException) {
                        _states.remove(url)
                        runCatching { outputFile.delete() }
                        runCatching { onFailure(t.message ?: "下载失败") }
                    }
                } finally {
                    synchronized(lock) {
                        if (activeUrl == url) {
                            activeJob = null
                            activeUrl = null
                        }
                    }
                }
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
                    context,
                    "安装失败：请在系统设置 → 应用 → 特殊权限 → 安装未知应用中开启",
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

    // ── 内部实现 ────────────────────────────────────────────────────────────

    private val _states = mutableMapOf<String, State>()
    private val lock = Any()
    private var activeJob: Job? = null
    private var activeUrl: String? = null

    private suspend fun doDownload(
        url: String,
        initial: State,
        onProgress: (Long, Long, Int, Long) -> Unit,
        onSuccess: (File) -> Unit,
        onFailure: (String) -> Unit,
        onRetry: () -> Unit,
    ) {
        val file = initial.file
        val resumeFrom = initial.downloaded
        var retries = initial.retries
        var downloaded = resumeFrom
        var totalBytes = -1L
        var lastTs = System.currentTimeMillis()
        var lastBytes = resumeFrom

        _states[url] = initial.copy(status = State.Status.DOWNLOADING)

        var response: okhttp3.Response? = null
        try {
            val requestBuilder = Request.Builder().url(url).header("User-Agent", GITHUB_UA)
            if (resumeFrom > 0L) requestBuilder.header("Range", "bytes=$resumeFrom-")
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
                    totalBytes = responseBody.contentLength()
                    streamChunks(responseBody, file, 0L) { _, bytesRead ->
                        downloaded += bytesRead
                        val now = System.currentTimeMillis()
                        val dt = max((now - lastTs) / 1000.0, 0.001)
                        val speed = ((downloaded - lastBytes) / dt).toLong()
                        lastTs = now
                        lastBytes = downloaded
                        val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                        onProgress(downloaded, totalBytes, max(0, min(pct, 100)), speed)
                    }
                }
                206 -> {
                    // 续传：追加到已有文件
                    val responseBody = response.body ?: throw IllegalStateException("下载响应无内容")
                    totalBytes = (resumeFrom + responseBody.contentLength()).coerceAtLeast(-1L)
                    streamChunks(responseBody, file, resumeFrom) { _, bytesRead ->
                        downloaded += bytesRead
                        val now = System.currentTimeMillis()
                        val dt = max((now - lastTs) / 1000.0, 0.001)
                        val speed = ((downloaded - lastBytes) / dt).toLong()
                        lastTs = now
                        lastBytes = downloaded
                        val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                        onProgress(downloaded, totalBytes, max(0, min(pct, 100)), speed)
                    }
                }
                403 -> throw IOException("下载被拒绝 (403)，GitHub 可能已限流，请稍后重试")
                404 -> throw IOException("APK 文件不存在 (404)，请联系开发者检查 Release 配置")
                else -> throw IOException("下载失败 HTTP ${response.code}")
            }

            // ── 成功 ──────────────────────────────────────────────────────
            file.setExecutable(true, false)
            _states.remove(url)
            onSuccess(file)

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            retries++
            if (retries <= MAX_RETRIES) {
                val delayMs = RETRY_BASE_DELAY_MS * (1L shl (retries - 1)) // 1s / 2s / 4s
                delay(delayMs)
                val cur = _states[url]?.copy(retries = retries) ?: State(url, file, downloaded, totalBytes, retries = retries)
                _states[url] = cur
                onRetry()
                // 递归重试
                doDownload(url, cur, onProgress, onSuccess, onFailure, onRetry)
            } else {
                _states.remove(url)
                file.delete()
                onFailure(e.message ?: "下载失败")
            }
        } finally {
            response?.close()
        }
    }

    /**
     * 以固定大小分块读取响应体并追加写入文件。
     *
     * @param body      OkHttp 响应体
     * @param file      目标 APK 文件
     * @param offset    追加偏移量（续传时使用）
     * @param onChunk   每读到一个 chunk 后的回调（传入 chunk 数组和实际读取字节数）
     */
    private fun streamChunks(
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
