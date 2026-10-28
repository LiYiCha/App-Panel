package com.panel.corescript.runner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.panel.corescript.engine.ExitKind
import com.panel.corescript.engine.OutputStream
import com.panel.corescript.engine.ScriptEngine
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.engine.ScriptRunListener
import com.panel.corescript.engine.ScriptRunRequest
import com.panel.corescript.engine.ScriptSession
import com.panel.corescript.engine.quickjs.QuickJsEngine
import com.panel.corescript.engine.python.PythonEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 脚本执行前台服务：承载 JS / Python 脚本运行，输出流式写入运行日志文件并推送到 [ScriptRunBus]。
 * 每次运行创建独立引擎实例，运行结束关闭，避免全局状态污染。
 *
 * 约束：
 * - Python 解释器为进程级单例（stdout 重定向是全局的），同一时间只允许运行一个 py 脚本；
 * - 单次运行日志文件超过 [MAX_RUN_LOG_BYTES] 停止写文件（内存缓冲仍有 2000 行上限）；
 * - run_logs 目录仅保留最近 [MAX_LOG_FILES] 个日志文件；
 * - 运行超过 [RUN_TIMEOUT_MS] 自动发送停止信号。
 */
class ScriptRunnerService : Service() {

    private class ActiveRun(val lang: ScriptLang, val startedAt: Long) {
        @Volatile
        var session: ScriptSession? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = ConcurrentHashMap<String, ActiveRun>()
    private val pythonBusy = AtomicBoolean(false)

    private val runLogDir: File by lazy {
        File(filesDir, "run_logs").apply { mkdirs() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUN -> handleRun(intent, startId)
            ACTION_STOP -> handleStop(intent)
            ACTION_PAUSE -> handlePauseResume(intent, pause = true)
            ACTION_RESUME -> handlePauseResume(intent, pause = false)
            else -> stopSelfIfIdle()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---------- 运行 ----------

    private fun handleRun(intent: Intent, startId: Int) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: return
        val langName = intent.getStringExtra(EXTRA_LANG) ?: return
        val lang = runCatching { ScriptLang.valueOf(langName) }.getOrNull() ?: return
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val args = intent.getStringExtra(EXTRA_ARGS)?.takeIf { it.isNotEmpty() }?.split('\n') ?: emptyList()
        val entryFile = File(path)

        startForegroundCompat(startId, entryFile.name, lang)
        scope.launch { pruneRunLogs() }

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        // Python 解释器进程级单例，禁止并发：直接落一条失败记录并结束
        if (lang == ScriptLang.PYTHON && !pythonBusy.compareAndSet(false, true)) {
            ScriptRunBus.onRunStarted(
                ScriptRunState(runId, lang, entryFile.name, System.currentTimeMillis(), RunStatus.RUNNING)
            )
            ScriptRunBus.onOutput(ConsoleLine(timeFormat.format(Date()), OutputStream.STDERR, "已有 Python 脚本在运行，请先停止后再试"))
            ScriptRunBus.onRunFinished(runId, ExitKind.FAILED, "已有 Python 脚本在运行")
            stopSelfIfIdle()
            return
        }

        val request = ScriptRunRequest(
            runId = runId,
            lang = lang,
            entryFile = entryFile,
            workDir = entryFile.parentFile ?: filesDir,
            depsDir = if (lang == ScriptLang.PYTHON) File(filesDir, "py_deps") else File(filesDir, "js_deps"),
            args = args
        )

        val logFile = File(runLogDir, "$runId.log")
        val writer = Channel<String>(Channel.UNLIMITED)
        scope.launch { runLogWriter(writer, logFile) }

        ScriptRunBus.onRunStarted(
            ScriptRunState(
                runId = runId,
                lang = lang,
                scriptName = entryFile.name,
                startedAt = System.currentTimeMillis(),
                status = RunStatus.RUNNING
            )
        )
        writer.trySend(formatLine(timeFormat, OutputStream.STDOUT, "== 运行开始 ${entryFile.name} [${lang.label}] =="))
        // 起始行同步进总线：控制台立即有可见输出，也便于判断是服务未启动还是引擎无输出
        ScriptRunBus.onOutput(
            ConsoleLine(timeFormat.format(Date()), OutputStream.STDOUT, "== 运行开始 ${entryFile.name} [${lang.label}] ==")
        )

        val active = ActiveRun(lang, System.currentTimeMillis())
        sessions[runId] = active
        val finishedReported = AtomicBoolean(false)

        val engine: ScriptEngine = when (lang) {
            ScriptLang.JS -> QuickJsEngine()
            ScriptLang.PYTHON -> PythonEngine(applicationContext)
        }

        val listener = object : ScriptRunListener {
            override fun onOutput(runId: String, stream: OutputStream, text: String) {
                ScriptRunBus.onOutput(ConsoleLine(timeFormat.format(Date()), stream, text))
                writer.trySend(formatLine(timeFormat, stream, text))
            }

            override fun onFinished(runId: String, exitKind: ExitKind, detail: String?) {
                // 引擎正常结束 / 强制看门狗 / 超时路径可能并发到达，仅首次生效
                if (!finishedReported.compareAndSet(false, true)) return
                sessions.remove(runId)
                if (lang == ScriptLang.PYTHON) pythonBusy.set(false)
                val summary = when (exitKind) {
                    ExitKind.SUCCESS -> "运行成功"
                    ExitKind.STOPPED -> "已停止"
                    ExitKind.FAILED -> "运行失败${detail?.let { "：$it" } ?: ""}"
                }
                ScriptRunBus.onRunFinished(runId, exitKind, detail)
                writer.trySend(formatLine(timeFormat, OutputStream.STDOUT, "== $summary =="))
                writer.close()
                engine.close()
                stopSelfIfIdle()
            }
        }

        scope.launch {
            val session = try {
                engine.run(request, listener)
            } catch (t: Throwable) {
                listener.onFinished(runId, ExitKind.FAILED, t.message ?: t.javaClass.simpleName)
                return@launch
            }
            active.session = session
        }

        // 运行超时看门狗：到时发送停止信号（真正的结束由引擎回调上报）
        scope.launch {
            delay(RUN_TIMEOUT_MS)
            if (finishedReported.get()) return@launch
            sessions[runId]?.let { run ->
                run.session?.stop()
                val tip = "脚本运行超过 ${RUN_TIMEOUT_MS / 60000} 分钟，已自动发送停止信号"
                ScriptRunBus.onOutput(ConsoleLine(timeFormat.format(Date()), OutputStream.STDERR, tip))
                writer.trySend(formatLine(timeFormat, OutputStream.STDERR, tip))
            }
        }
    }

    private fun handleStop(intent: Intent) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: return
        sessions[runId]?.session?.stop()
    }

    private fun handlePauseResume(intent: Intent, pause: Boolean) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: return
        val session = sessions[runId]?.session ?: return
        if (pause) {
            session.pause()
            ScriptRunBus.onStatusChanged(runId, RunStatus.PAUSED)
        } else {
            session.resume()
            ScriptRunBus.onStatusChanged(runId, RunStatus.RUNNING)
        }
    }

    private fun stopSelfIfIdle() {
        if (sessions.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---------- 运行日志 ----------

    /** 逐行消费输出并追加写入运行日志文件；超过大小上限后停止写文件（继续消费防止输出堆积） */
    private suspend fun runLogWriter(lines: Channel<String>, logFile: File) {
        FileOutputStream(logFile, true).use { out ->
            var written = 0L
            var truncated = false
            for (line in lines) {
                if (truncated) continue
                val bytes = (line + "\n").toByteArray()
                if (written + bytes.size > MAX_RUN_LOG_BYTES) {
                    val tip = "[--] 日志已达 ${MAX_RUN_LOG_BYTES / 1024 / 1024}MB 上限，停止写入文件（脚本继续运行）\n"
                    out.write(tip.toByteArray())
                    out.flush()
                    truncated = true
                    continue
                }
                out.write(bytes)
                out.flush()
                written += bytes.size
            }
        }
    }

    /** 清理历史运行日志，仅保留最近 [MAX_LOG_FILES] 个 */
    private suspend fun pruneRunLogs() {
        val logs = runLogDir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: return
        if (logs.size <= MAX_LOG_FILES) return
        logs.sortedByDescending { it.lastModified() }
            .drop(MAX_LOG_FILES)
            .forEach { it.delete() }
    }

    private fun formatLine(timeFormat: SimpleDateFormat, stream: OutputStream, text: String): String {
        val prefix = if (stream == OutputStream.STDERR) "[err] " else ""
        return "[${timeFormat.format(Date())}] $prefix$text"
    }

    // ---------- 通知 ----------

    private fun startForegroundCompat(startId: Int, scriptName: String, lang: ScriptLang) {
        ensureChannel()
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ScriptRunnerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("脚本运行中")
            .setContentText("${lang.label} · $scriptName")
            .setOngoing(true)
            .addAction(0, "停止", stopIntent)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "脚本运行", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "本地脚本执行状态"
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "channel_script_run"
        private const val NOTIFICATION_ID = 2001

        /** 单次运行日志文件大小上限 */
        private const val MAX_RUN_LOG_BYTES = 10L * 1024 * 1024

        /** run_logs 目录保留的历史日志数量上限 */
        private const val MAX_LOG_FILES = 50

        /** 单次运行时长上限（超时自动停止） */
        private const val RUN_TIMEOUT_MS = 30L * 60 * 1000

        const val ACTION_RUN = "com.panel.corescript.action.RUN"
        const val ACTION_STOP = "com.panel.corescript.action.STOP"
        const val ACTION_PAUSE = "com.panel.corescript.action.PAUSE"
        const val ACTION_RESUME = "com.panel.corescript.action.RESUME"
        private const val EXTRA_RUN_ID = "runId"
        private const val EXTRA_LANG = "lang"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ARGS = "args"

        /** 启动脚本运行，返回 runId；UI 用它订阅 [ScriptRunBus] 与查找日志文件 */
        fun start(context: Context, lang: ScriptLang, scriptFile: File, args: List<String> = emptyList()): String {
            // 时间前缀格式（yyyyMMdd_HHmmss_uuid8）：运行记录列表可直接按文件名排序与展示
            val runId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
                "_" + UUID.randomUUID().toString().take(8)
            val intent = Intent(context, ScriptRunnerService::class.java)
                .setAction(ACTION_RUN)
                .putExtra(EXTRA_RUN_ID, runId)
                .putExtra(EXTRA_LANG, lang.name)
                .putExtra(EXTRA_PATH, scriptFile.absolutePath)
                .putExtra(EXTRA_ARGS, args.joinToString("\n"))
            context.startForegroundService(intent)
            return runId
        }

        fun stop(context: Context, runId: String) {
            context.startService(actionIntent(context, ACTION_STOP, runId))
        }

        /** 请求暂停（仅 Python 生效，JS 空操作） */
        fun pause(context: Context, runId: String) {
            context.startService(actionIntent(context, ACTION_PAUSE, runId))
        }

        /** 请求继续 */
        fun resume(context: Context, runId: String) {
            context.startService(actionIntent(context, ACTION_RESUME, runId))
        }

        private fun actionIntent(context: Context, action: String, runId: String) =
            Intent(context, ScriptRunnerService::class.java)
                .setAction(action)
                .putExtra(EXTRA_RUN_ID, runId)

        fun runLogFile(context: Context, runId: String): File =
            File(File(context.filesDir, "run_logs"), "$runId.log")
    }
}
