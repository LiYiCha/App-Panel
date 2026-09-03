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

    // ---------------------------------------------------------------- 格式化

    fun formatSeconds(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
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
