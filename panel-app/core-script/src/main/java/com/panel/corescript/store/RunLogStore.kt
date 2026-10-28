package com.panel.corescript.store

import com.panel.corescript.engine.OutputStream
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.runner.ConsoleLine
import com.panel.corescript.runner.RunStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** 一条历史运行记录（对应 run_logs/{runId}.log） */
data class RunLogEntry(
    val runId: String,
    /** 开始时间戳；新格式 runId 从文件名解析，旧 UUID 文件取文件 lastModified */
    val startedAt: Long,
    /** 脚本名（日志首行解析；解析失败显示占位） */
    val scriptName: String,
    val lang: ScriptLang?,
    /** 结束状态（日志尾行解析；运行被截断或解析失败为 null） */
    val status: RunStatus?,
    val sizeBytes: Long
)

/**
 * 运行日志管理：列表 / 回放解析 / 删除 / 清空。
 * 日志文件由 ScriptRunnerService 落盘，行格式 `[HH:mm:ss] [err] text`。
 * runId 为时间前缀格式 `yyyyMMdd_HHmmss_uuid8`（旧版为纯 UUID，兼容读取）。
 */
class RunLogStore(private val dir: File) {

    /** 全部记录，按开始时间倒序 */
    fun list(): List<RunLogEntry> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: return emptyList()
        return files.mapNotNull { f -> parseEntry(f) }
            .sortedByDescending { it.startedAt }
    }

    /** 回放一次运行的完整输出（与控制台实时格式一致） */
    fun read(runId: String): List<ConsoleLine> {
        val file = File(dir, "$runId.log")
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull { raw -> parseLine(raw) }
    }

    fun delete(runId: String) {
        File(dir, "$runId.log").delete()
    }

    fun clearAll() {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }?.forEach { it.delete() }
    }

    // ---------- 解析 ----------

    private fun parseEntry(file: File): RunLogEntry? {
        val runId = file.name.removeSuffix(".log")
        if (runId.isEmpty()) return null
        val startedAt = parseStartedAt(runId, file)
        var scriptName = "未知脚本"
        var lang: ScriptLang? = null

        // 首行：[HH:mm:ss] == 运行开始 hello.py [Python] ==
        val firstLine = runCatching { file.useLines { it.firstOrNull() } }.getOrNull()
        if (firstLine != null && firstLine.contains("== 运行开始")) {
            runCatching {
                val payload = firstLine.substringAfter("== 运行开始").substringBefore("==").trim()
                scriptName = payload.substringBeforeLast('[').trim().ifEmpty { scriptName }
                val langLabel = payload.substringAfterLast('[', "").substringBefore(']', "").trim()
                lang = ScriptLang.entries.firstOrNull { it.label == langLabel }
            }
        }

        // 尾部扫描结束状态关键字（结束行：== 运行成功 == / == 已停止 == / == 运行失败…）
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        val status = lines.asReversed().firstNotNullOfOrNull { raw ->
            when {
                raw.contains("== 运行成功 ==") -> RunStatus.SUCCESS
                raw.contains("== 已停止 ==") -> RunStatus.STOPPED
                raw.contains("== 运行失败") -> RunStatus.FAILED
                else -> null
            }
        }

        return RunLogEntry(
            runId = runId,
            startedAt = startedAt,
            scriptName = scriptName,
            lang = lang,
            status = status,
            sizeBytes = file.length()
        )
    }

    private fun parseStartedAt(runId: String, file: File): Long {
        // 新格式：yyyyMMdd_HHmmss_uuid8
        if (runId.length > 16) {
            runCatching {
                val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                fmt.isLenient = false
                return fmt.parse(runId.take(15))?.time ?: file.lastModified()
            }
        }
        return file.lastModified()
    }

    private fun parseLine(raw: String): ConsoleLine? {
        if (raw.length < 12 || !raw.startsWith("[")) return null
        val isErr = raw.contains("] [err] ")
        val text = if (isErr) raw.substringAfter("] [err] ") else raw.substringAfter("] ", "")
        return ConsoleLine(
            time = raw.substringBefore(']').removePrefix("["),
            stream = if (isErr) OutputStream.STDERR else OutputStream.STDOUT,
            text = text
        )
    }
}
