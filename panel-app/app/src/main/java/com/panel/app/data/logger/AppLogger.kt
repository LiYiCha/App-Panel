package com.panel.app.data.logger

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
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

object AppLogger {
    private const val MAX_LOGS = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    var isDevModeEnabled: Boolean = false

    val logs = mutableStateListOf<LogEntry>()

    fun log(level: LogLevel, tag: String, message: String, error: String? = null) {
        if (!isDevModeEnabled && level != LogLevel.ERROR) return
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message,
            error = error
        )
        synchronized(logs) {
            if (logs.size >= MAX_LOGS) {
                logs.removeAt(0)
            }
            logs.add(entry)
        }
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
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
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
        synchronized(logs) {
            if (logs.size >= MAX_LOGS) {
                logs.removeAt(0)
            }
            logs.add(entry)
        }
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
    }
}

