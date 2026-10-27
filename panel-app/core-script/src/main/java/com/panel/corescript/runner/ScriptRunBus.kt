package com.panel.corescript.runner

import com.panel.corescript.engine.ExitKind
import com.panel.corescript.engine.OutputStream
import com.panel.corescript.engine.ScriptLang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 控制台单行输出 */
data class ConsoleLine(
    val time: String,
    val stream: OutputStream,
    val text: String
)

enum class RunStatus { RUNNING, SUCCESS, FAILED, STOPPED }

/** 一次运行的状态快照 */
data class ScriptRunState(
    val runId: String,
    val lang: ScriptLang,
    val scriptName: String,
    val startedAt: Long,
    val status: RunStatus,
    val finishedDetail: String? = null
)

/**
 * 运行状态总线：前台服务写，UI 读。
 * 输出行保留在内存环形缓冲中（上限 [MAX_LINES]），控制台页面晚打开也能看到全部输出。
 */
object ScriptRunBus {
    private const val MAX_LINES = 2000

    private val _currentRun = MutableStateFlow<ScriptRunState?>(null)
    val currentRun: StateFlow<ScriptRunState?> = _currentRun.asStateFlow()

    private val buffer = ArrayDeque<ConsoleLine>()
    private val _revision = MutableStateFlow(0L)
    /** 每追加一行输出自增，UI 观察它触发重组 */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** app 层可注入的外部日志钩子（如写入 AppLogger），参数为 (level, tag, message) */
    @Volatile
    var externalLogger: ((String, String, String) -> Unit)? = null

    val currentLines: List<ConsoleLine> get() = synchronized(buffer) { buffer.toList() }

    fun onRunStarted(state: ScriptRunState) {
        synchronized(buffer) { buffer.clear() }
        _revision.value++
        _currentRun.value = state
        externalLogger?.invoke("INFO", "ScriptRunner", "开始运行 ${state.scriptName} [${state.lang.label}]")
    }

    fun onOutput(line: ConsoleLine) {
        synchronized(buffer) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(line)
        }
        _revision.value++
    }

    fun onRunFinished(runId: String, kind: ExitKind, detail: String?) {
        val current = _currentRun.value ?: return
        if (current.runId != runId) return
        val status = when (kind) {
            ExitKind.SUCCESS -> RunStatus.SUCCESS
            ExitKind.FAILED -> RunStatus.FAILED
            ExitKind.STOPPED -> RunStatus.STOPPED
        }
        _currentRun.value = current.copy(status = status, finishedDetail = detail)
        _revision.value++
        val summary = detail?.takeIf { it.isNotBlank() } ?: status.name
        val level = if (kind == ExitKind.FAILED) "ERROR" else "INFO"
        externalLogger?.invoke(level, "ScriptRunner", "${current.scriptName} 结束：$summary")
    }
}
