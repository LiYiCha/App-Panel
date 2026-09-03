package com.panel.app.data.adapter

import com.google.gson.JsonObject
import com.panel.app.data.remote.api.BaihuApi

/**
 * 白虎适配器公共辅助函数（提取自 BaihuPanelAdapter）。
 */
object BaihuApiHelpers {

    /**
     * 把错误列表汇总为一个 Result。
     * 无错误 → success(true)；有错误 → failure（消息最多显示 3 条）。
     */
    fun summarize(errors: List<String>): Result<Boolean> {
        val distinct = errors.distinct()
        return if (distinct.isEmpty()) Result.success(true)
        else Result.failure(Exception(distinct.joinToString("; ")))
    }

    /** 把时长（秒）格式化为可读字符串 */
    fun formatDuration(duration: Long): String {
        val seconds = if (duration > 100_000) duration / 1000 else duration
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
