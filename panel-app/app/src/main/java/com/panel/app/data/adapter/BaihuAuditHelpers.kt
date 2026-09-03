package com.panel.app.data.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.panel.app.data.remote.ApiEnvelope
import com.panel.app.data.remote.unwrapTo
import com.panel.app.data.remote.api.BaihuApi

/**
 * 白虎审计日志辅助函数（提取自 BaihuPanelAdapter）。
 *
 * 包含：getLoginLogs、getLogsTree、getLogDetail（基于 extractLogOutput）。
 */

// ---------------------------------------------------------------- 扩展方法（作用于 BaihuApi）

/** 获取登录审计日志（最多 50 条） */
suspend fun BaihuApi.getLoginLogsHelper(): Result<List<Map<String, Any>>> {
    return try {
        getLoginLogs(pageSize = 50)
            .unwrapTo("获取登录日志失败") { env ->
                env.data?.data.orEmpty().map { item ->
                    mapOf(
                        "ip" to (item.ip ?: "127.0.0.1"),
                        "username" to (item.username ?: "--"),
                        "status" to (item.status ?: "success"),
                        "address" to (item.message ?: ""),
                        "createdAt" to (item.created_at ?: "--")
                    )
                }
            }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * 把日志列表聚合成树形 JsonArray，供 UI 文件树控件使用。
 * 按 task_name 分组，每组是一组子文件节点。
 */
suspend fun BaihuApi.getLogsTreeHelper(): Result<JsonElement> {
    return try {
        getLogs(pageSize = 100)
            .unwrapTo("获取日志列表失败") { env ->
                val groups = mutableMapOf<String, JsonArray>()
                env.data?.data.orEmpty().forEach { log ->
                    val groupName = log.task_name?.ifBlank { "通用任务" } ?: "通用任务"
                    val children = groups.computeIfAbsent(groupName) { JsonArray() }
                    val timeStr = log.start_time?.replace(' ', '_')?.replace(':', '-')
                        ?: log.created_at?.replace(' ', '_')?.replace(':', '-')
                        ?: log.id
                    children.add(JsonObject().apply {
                        addProperty("title", "$timeStr.log")
                        addProperty("type", "file")
                        addProperty("id", log.id)
                    })
                }
                JsonArray().apply {
                    groups.forEach { (groupName, children) ->
                        add(JsonObject().apply {
                            addProperty("title", groupName)
                            addProperty("type", "directory")
                            add("children", children)
                        })
                    }
                }
            }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
