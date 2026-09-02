package com.panel.app.data.logger

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.util.concurrent.atomic.AtomicLong

data class LogEntry(
    val id: Long = 0L,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val method: String? = null,
    val url: String? = null,
    val code: Int? = null,
    val durationMs: Long? = null,
    val requestBody: String? = null,
    val responseBody: String? = null,
    val error: String? = null
)

enum class LogLevel {
    INFO,
    HTTP_OK,
    HTTP_ERR,
    WARN,
    ERROR
}

/**
 * 开发者调试控制台的日志缓冲。
 *
 * ### 这里必须做线程安全处理（曾经的直接闪退根因）
 * `logs` 是 Compose 的 `mutableStateListOf`，**只能在主线程修改**；
 * 而调用方是 OkHttp 的拦截器，跑在 OkHttp 自己的调度线程上，且
 * `MainViewModel.refreshPanelRemoteData()` 会一次并发发起 7 个请求，
 * 于是出现两种崩溃：
 *  1. 多线程并发修改 Compose 快照状态 —— 抛 IllegalStateException /
 *     ConcurrentModificationException；
 *  2. 共享的 `SimpleDateFormat.format()` 不是线程安全的（内部会改写 Calendar
 *     状态），并发调用会抛 NumberFormatException / 数组越界。
 *
 * 处理办法：
 *  - 时间戳改用不可变、线程安全的 `DateTimeFormatter`；
 *  - 列表写入一律 post 回主线程；
 *  - 自增 [idSequence] 保证日志 key 唯一（LazyColumn 的 key 重复会直接抛异常）。
 *
 * ### 为什么崩溃日志要单独走 [recordCrash]
 * 崩溃日志写进内存列表后进程就死了，列表随之消失，控制台永远看不到崩溃原因
 * （这就是之前"只记录到 HTTP 异常、没记录到崩溃异常"的原因）。
 * 所以崩溃必须**同步写文件**（见 [LogStorage.appendSync]），并在下次启动时回灌。
 */
object AppLogger {
    private const val MAX_LOGS = 500

    private val mainHandler = Handler(Looper.getMainLooper())
    private val idSequence = AtomicLong(0)

    var isDevModeEnabled: Boolean = false

    /** 只读给 UI 用；写入全部经 [append] 收敛到主线程 */
    val logs = mutableStateListOf<LogEntry>()

    /** Application.onCreate 里调用一次：初始化落盘目录，并回灌上次会话的日志尾部 */
    fun init(context: Context) {
        LogStorage.init(context)
        val tail = runCatching { LogStorage.readLatestTail() }.getOrNull() ?: return
        val entry = LogEntry(
            id = idSequence.incrementAndGet(),
            timestamp = LogStorage.now(),
            level = LogLevel.INFO,
            tag = "上次会话",
            message = "以下为上次运行遗留的日志尾部（已按保留天数持久化在本地）：\n$tail"
        )
        append(entry)
    }

    fun log(level: LogLevel, tag: String, message: String, error: String? = null) {
        if (!isDevModeEnabled && level != LogLevel.ERROR) return
        append(
            LogEntry(
                id = idSequence.incrementAndGet(),
                timestamp = LogStorage.now(),
                level = level,
                tag = tag,
                message = message,
                error = error
            )
        )
    }

    fun httpDetailed(
        method: String,
        url: String,
        code: Int,
        durationMs: Long,
        requestBody: String? = null,
        responseBody: String? = null,
        error: String? = null
    ) {
        if (!isDevModeEnabled) return
        val level = if (code in 200..299) LogLevel.HTTP_OK else LogLevel.HTTP_ERR
        val summary = "[$method] $code (${durationMs}ms) $url"
        append(
            LogEntry(
                id = idSequence.incrementAndGet(),
                timestamp = LogStorage.now(),
                level = level,
                tag = "HTTP",
                message = summary,
                method = method,
                url = url,
                code = code,
                durationMs = durationMs,
                requestBody = requestBody,
                responseBody = responseBody,
                error = error
            )
        )
    }

    /**
     * 崩溃专用：**不走开发者模式开关、不走主线程队列，同步落盘**。
     * 崩溃线程不是主线程时，post 到主线程的任务根本来不及执行进程就没了。
     */
    fun recordCrash(thread: Thread, throwable: Throwable) {
        val entry = LogEntry(
            id = idSequence.incrementAndGet(),
            timestamp = LogStorage.now(),
            level = LogLevel.ERROR,
            tag = "CRASH_GUARD",
            message = "未捕获异常 线程[${thread.name}]: ${throwable.message ?: throwable.javaClass.simpleName}",
            error = android.util.Log.getStackTraceString(throwable)
        )
        // 第一优先级：同步写文件，进程死前必须落盘
        runCatching { LogStorage.appendSync(entry) }
        // 第二优先级：如果主线程还活着（后台线程崩溃），让控制台立刻可见
        if (isMainThread()) {
            appendOnMain(entry)
        } else {
            mainHandler.post { appendOnMain(entry) }
        }
    }

    fun clear() {
        if (isMainThread()) {
            logs.clear()
        } else {
            mainHandler.post { logs.clear() }
        }
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun append(entry: LogEntry) {
        if (isMainThread()) {
            appendOnMain(entry)
        } else {
            // 后台线程（OkHttp 调度线程）只负责投递，绝不直接改快照状态
            mainHandler.post { appendOnMain(entry) }
        }
    }

    private fun appendOnMain(entry: LogEntry) {
        if (logs.size >= MAX_LOGS) {
            logs.removeAt(0)
        }
        logs.add(entry)
        // 同步落盘（异步写不阻塞 UI，崩溃另有 recordCrash 的同步路径）
        LogStorage.appendAsync(entry)
    }
}
