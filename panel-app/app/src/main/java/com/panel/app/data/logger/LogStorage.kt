package com.panel.app.data.logger

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 调试日志的落盘存储。
 *
 * ### 为什么要落盘（而不是只放内存）
 * 之前的崩溃日志写在 `AppLogger.logs`（Compose 快照列表）里：
 *  1. 崩溃发生在 OkHttp 后台线程，往快照列表写会再抛一次并发异常；
 *  2. 就算写进去了，进程马上就死，内存列表随之消失 —— 下次冷启动是全新空列表，
 *     所以控制台**永远看不到崩溃原因**。
 *
 * 崩溃处理器里必须**同步**写文件（那是进程死前最后的机会），
 * 普通日志则走单线程执行器异步写，不阻塞 UI。
 *
 * ### 存储位置
 * 首选 App 自己的 media 目录：`/sdcard/Android/media/com.panel.app/files/logs`。
 * 选这里而不是 `Android/data` 是有原因的：Android 11+ 对 `Android/data`
 * 做了访问隔离，第三方文件管理器（包括 MT 管理器）直读会受限；
 * 而 `Android/media/<包名>/` 无需任何授权即可被任意文件管理器浏览。
 * media 目录不可用时依次回退到 `getExternalFilesDir()/logs` 与应用私有目录。
 *
 * ### 文件组织
 * 按天一个文件：`panel-log-2026-09-02.log`，方便按时间定位与按保留天数清理。
 */
object LogStorage {

    private const val DIR_NAME = "logs"
    private const val FILE_PREFIX = "panel-log-"
    private const val FILE_EXT = ".log"
    private const val PREFS_NAME = "panel_log_prefs"
    private const val KEY_RETENTION = "log_retention_days"
    private const val SEPARATOR = "──────────────────────────────────────────"

    /** 永久保留（不自动清理） */
    const val KEEP_FOREVER = -1

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PanelLogWriter").apply { isDaemon = true }
    }

    // SimpleDateFormat 只在单线程执行器与崩溃线程使用，统一在 writeLock 内调用保证线程安全
    private val writeLock = Any()
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var appContext: Context? = null

    /** 在 Application.onCreate 里调用一次 */
    fun init(context: Context) {
        appContext = context.applicationContext
        pruneExpired()
    }

    // ---------------------------------------------------------- 目录与文件

    fun logDir(): File {
        val ctx = appContext ?: return File(DIR_NAME)
        // 首选 App 自己的 media 目录（/sdcard/Android/media/<包名>/files/logs）：
        // Android 11+ 上 Android/data 被隔离，第三方文件管理器直读受限；
        // Android/media 无需任何授权即可被 MT 管理器等工具直接浏览
        @Suppress("DEPRECATION")
        val media = ctx.getExternalMediaDirs()?.firstOrNull()?.let { File(it, DIR_NAME) }
        if (media != null) return media
        val external = ctx.getExternalFilesDir(null)?.let { File(it, DIR_NAME) }
        return external ?: File(ctx.filesDir, DIR_NAME)
    }

    fun logDirPath(): String = logDir().absolutePath

    /** 今天这份日志的文件名，供"用文件管理器打开"兜底使用 */
    fun currentFileName(): String = FILE_PREFIX + today() + FILE_EXT

    private fun todayFile(): File = File(logDir(), currentFileName())

    private fun allLogFiles(): List<File> =
        logDir().listFiles()?.filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_EXT) }
            ?.sortedByDescending { it.name } ?: emptyList()

    // ---------------------------------------------------------- 保留天数

    fun retentionDays(): Int = prefs().getInt(KEY_RETENTION, 7)

    fun setRetentionDays(days: Int) {
        prefs().edit().putInt(KEY_RETENTION, days).apply()
        pruneExpired()
    }

    private fun prefs(): SharedPreferences =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("LogStorage 未初始化，请先调用 init(context)")

    /** 启动与修改保留天数时调用：删除超出保留期的日志文件 */
    fun pruneExpired() {
        val days = retentionDays()
        if (days == KEEP_FOREVER) return
        val cutoff = System.currentTimeMillis() - days.toLong() * 24L * 3600L * 1000L
        allLogFiles().forEach { file ->
            val fileTime = parseFileDate(file.name) ?: file.lastModified()
            if (fileTime < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun parseFileDate(name: String): Long? = try {
        val raw = name.removePrefix(FILE_PREFIX).removeSuffix(FILE_EXT)
        synchronized(writeLock) { dayFormat.parse(raw)?.time }
    } catch (_: ParseException) {
        null
    } catch (_: Exception) {
        null
    }

    // ---------------------------------------------------------- 写入

    /** 普通日志：异步写，不阻塞调用线程 */
    fun appendAsync(entry: LogEntry) {
        ioExecutor.execute { appendSync(entry) }
    }

    /**
     * 同步写盘。**仅供崩溃处理器使用**——进程随时会死，不能依赖异步队列。
     * 内部加锁，允许与 IO 线程并发调用。
     */
    fun appendSync(entry: LogEntry) {
        val file = todayFile()
        try {
            synchronized(writeLock) {
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                file.appendText(render(entry), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            // 日志写失败绝不能反过来把 App 打崩
        }
    }

    private fun today(): String = synchronized(writeLock) { dayFormat.format(Date()) }

    fun now(): String = synchronized(writeLock) { fullFormat.format(Date()) }

    private fun render(e: LogEntry): String = buildString {
        append(SEPARATOR).append('\n')
        append(e.timestamp).append("  [").append(e.level).append("]  [").append(e.tag).append("]\n")
        append(e.message).append('\n')
        if (!e.method.isNullOrBlank()) append("方法: ").append(e.method).append('\n')
        if (!e.url.isNullOrBlank()) append("URL: ").append(e.url).append('\n')
        if (e.code != null) append("状态码: ").append(e.code)
        if (e.durationMs != null) append("  耗时: ").append(e.durationMs).append("ms")
        if (e.code != null || e.durationMs != null) append('\n')
        if (!e.requestBody.isNullOrBlank()) append("请求入参:\n").append(e.requestBody).append('\n')
        if (!e.responseBody.isNullOrBlank()) append("响应数据:\n").append(e.responseBody).append('\n')
        if (!e.error.isNullOrBlank()) append("异常报错:\n").append(e.error).append('\n')
        append('\n')
    }

    // ---------------------------------------------------------- 解析（回灌用）

    /** 解析日志文件尾部文本，恢复成带原始 level/tag 的条目列表 */
    fun parseTailToEntries(tail: String, maxEntries: Int = 30): List<LogEntry> {
        val blocks = tail.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeLast(maxEntries)
        val result = ArrayList<LogEntry>(blocks.size)
        for (block in blocks) {
            result.add(parseBlock(block))
        }
        return result
    }

    private fun parseBlock(block: String): LogEntry {
        val lines = block.lines()
        val header = lines.firstOrNull() ?: ""
        // header 格式: "2026-09-21 10:00:00.000  [HTTP_OK]  [HTTP]"
        val headerMatch = Regex("""^(\S+\s+\S+)\s+\[(\w+)\]\s+\[([^\]]*)\]""").find(header)
        val timestamp = headerMatch?.groupValues?.get(1) ?: now()
        val levelStr = headerMatch?.groupValues?.get(2) ?: "INFO"
        val tag = headerMatch?.groupValues?.get(3) ?: "上次会话"
        val level = runCatching { LogLevel.valueOf(levelStr) }.getOrDefault(LogLevel.INFO)

        // 去掉 header 行，剩余为 message 及可选的结构化字段
        val rest = lines.drop(1).joinToString("\n").trim()
        val message = StringBuilder()
        var method: String? = null
        var url: String? = null
        var code: Int? = null
        var durationMs: Long? = null
        var requestBody: String? = null
        var responseBody: String? = null
        var error: String? = null

        val fieldRegex = Regex("""^(方法|URL|状态码|请求入参|响应数据|异常报错):""")
        var currentField: String? = null
        val currentValue = StringBuilder()

        fun flushField() {
            when (currentField) {
                "方法" -> method = currentValue.toString().trim()
                "URL" -> url = currentValue.toString().trim()
                "状态码" -> {
                    val raw = currentValue.toString().trim()
                    Regex("""(\d+)""").find(raw)?.let { code = it.groupValues[1].toIntOrNull() }
                    Regex("""耗时:\s*(\d+)""").find(raw)?.let { durationMs = it.groupValues[1].toLongOrNull() }
                }
                "请求入参" -> requestBody = currentValue.toString().trim()
                "响应数据" -> responseBody = currentValue.toString().trim()
                "异常报错" -> error = currentValue.toString().trim()
            }
            currentField = null
            currentValue.clear()
        }

        for (line in rest.lines()) {
            val match = fieldRegex.find(line)
            if (match != null) {
                flushField()
                currentField = match.groupValues[1]
                val afterColon = line.substring(match.range.last + 1).trim()
                if (afterColon.isNotEmpty()) currentValue.append(afterColon)
            } else if (currentField != null) {
                if (currentValue.isNotEmpty()) currentValue.append('\n')
                currentValue.append(line)
            } else {
                if (message.isNotEmpty()) message.append('\n')
                message.append(line)
            }
        }
        flushField()

        return LogEntry(
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message.toString().trim(),
            method = method,
            url = url,
            code = code,
            durationMs = durationMs,
            requestBody = requestBody,
            responseBody = responseBody,
            error = error
        )
    }

    // ---------------------------------------------------------- 清理 / 读取

    /** 清除"当前"日志：今天的文件 + 由调用方负责清空内存列表 */
    fun clearCurrent() {
        runCatching { todayFile().delete() }
    }

    /** 清除全部日志：所有日志文件 + 由调用方负责清空内存列表 */
    fun clearAll() {
        allLogFiles().forEach { runCatching { it.delete() } }
    }

    /**
     * 读取最近一份日志文件的末尾内容（流式，只读尾部 N 行，避免大文件 OOM）。
     * 冷启动时用它把"上次会话"（尤其上次崩溃）恢复进控制台内存列表。
     */
    fun readLatestTail(maxLines: Int = 80, maxBytes: Long = 128 * 1024): String? {
        val files = allLogFiles()
        for (file in files) {
            if (!file.exists() || file.length() == 0L) continue
            try {
                val lines = ArrayDeque<String>(maxLines)
                file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                    // 只跳到文件末尾前 maxBytes 处，避免把整个大文件读进内存
                    val skipBytes = (file.length() - maxBytes).coerceAtLeast(0)
                    if (skipBytes > 0) {
                        reader.skip(skipBytes)
                        reader.readLine() // 丢弃可能不完整的第一行
                    }
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (lines.size >= maxLines) lines.removeFirst()
                        lines.addLast(line!!)
                    }
                }
                if (lines.isNotEmpty()) return lines.joinToString("\n")
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}
