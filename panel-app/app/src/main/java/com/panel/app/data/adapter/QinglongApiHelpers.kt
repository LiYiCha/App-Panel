package com.panel.app.data.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject


/**
 * 青龙适配器公共辅助函数（提取自 QinglongV15Adapter）。
 *
 * 分类：
 * - 通用 JSON 解析（parseJsonArray）
 * - ID 转换（cleanId、toId、toIds）
 * - 格式化工具（formatSeconds、formatBytes）
 * - 环境变量正则
 */
object QinglongApiHelpers {

    /** 后端 Joi 约束：name 必须是合法 shell 变量名 */
    const val ENV_NAME_REGEX = "^[a-zA-Z_][0-9a-zA-Z_]*$"

    // ---------------------------------------------------------------- 通用 JSON

    fun parseJsonArray(data: JsonElement?): List<JsonElement> = when {
        data == null -> emptyList()
        data.isJsonArray -> data.asJsonArray.toList()
        data.isJsonObject && data.asJsonObject.get("data")?.isJsonArray == true ->
            data.asJsonObject.getAsJsonArray("data").toList()
        else -> emptyList()
    }

    // ---------------------------------------------------------------- ID 转换

    fun toIds(ids: List<String>): List<Long> = ids.mapNotNull { it.toLongOrNull() }

    fun toId(id: String): Long? = id.toLongOrNull() ?: run {
        val d = id.toDoubleOrNull() ?: return null
        runCatching { d.toLong() }.getOrNull()
    }

    fun cleanId(raw: Any?): String = when (raw) {
        is Number -> runCatching { raw.toLong() }.getOrNull()?.toString() ?: raw.toString()
        is String -> raw.toDoubleOrNull()?.let { runCatching { it.toLong() }.getOrNull() }?.toString() ?: raw.substringBefore('.')
        else -> raw?.toString()?.substringBefore('.') ?: ""
    }

    // ---------------------------------------------------------------- 格式化与解析

    fun parseSeconds(raw: Any?): Long? = when (raw) {
        null -> null
        is Number -> raw.toLong()
        is String -> {
            val trimmed = raw.trim()
            trimmed.toLongOrNull()
                ?: trimmed.toDoubleOrNull()?.toLong()
                ?: Regex("""\d+""").find(trimmed)?.value?.toLongOrNull()
        }
        else -> null
    }

    fun parseTimestampToMillis(raw: Any?): Long? {
        if (raw == null) return null
        return when (raw) {
            is Number -> {
                val v = raw.toLong()
                if (v <= 0) null
                else if (v > 100_000_000_000L) v
                else v * 1000L
            }
            is String -> {
                val trimmed = raw.trim()
                val num = trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull()?.toLong()
                if (num != null && num > 0) {
                    if (num > 100_000_000_000L) num else num * 1000L
                } else {
                    parseIsoDateToMillis(trimmed)
                }
            }
            else -> null
        }
    }

    private fun parseIsoDateToMillis(str: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (fmt in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                if (fmt.contains("'Z'") || fmt.endsWith("Z")) {
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(str)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    fun formatSeconds(seconds: Long): String = when {
        seconds <= 0 -> "0s"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            if (s > 0) "${h}h ${m}m ${s}s" else "${h}h ${m}m"
        }
    }

    /**
     * 从日志内容（末尾或任意位置）提取执行耗时
     * 例如："## 执行结束... 2026-09-05 10:37:21  耗时 11181 秒"
     */
    fun parseDurationFromLog(log: String?): String? {
        if (log.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("""(?:执行结束|完成|失败|已停止).*?耗时\s*[:：]?\s*(\d+)\s*(?:秒|s)"""),
            Regex("""耗时\s*[:：]?\s*(\d+)\s*(?:秒|s)"""),
            Regex("""elapsed\s*[:：]?\s*(\d+)\s*(?:seconds|s)""", RegexOption.IGNORE_CASE),
            Regex("""(?:took|耗时)\s*[:：]?\s*(\d+)\s*(?:ms|毫秒)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(log)
            if (match != null) {
                val numStr = match.groupValues[1]
                val num = numStr.toLongOrNull() ?: continue
                val seconds = if (match.value.contains("ms", ignoreCase = true) || match.value.contains("毫秒")) {
                    if (num >= 1000) num / 1000 else 1
                } else {
                    num
                }
                return formatSeconds(seconds)
            }
        }
        return null
    }

    fun formatBytes(bytes: Long?): String? {
        if (bytes == null || bytes <= 0) return null
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
