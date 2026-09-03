package com.panel.app.data.adapter

import com.google.gson.JsonObject
import com.panel.app.data.remote.ApiEnvelope
import com.panel.app.data.remote.unwrap
import com.panel.app.data.remote.api.QinglongV15Api

/**
 * 青龙系统设置辅助函数（提取自 QinglongV15Adapter）。
 *
 * 包含：extractEntries、fetchSystemSettings、saveLogRemoveFrequency、
 * saveCronConcurrency、sendTestNotify、reloadSystem。
 */
object QinglongSystemHelpers {

    /**
     * 递归遍历 JsonObject，收集所有叶节点（key 扁平化用点分隔）。
     * 同时保留无前缀的短 key，方便按名字直接查。
     */
    fun extractEntries(target: JsonObject, result: MutableMap<String, String>, prefix: String = "") {
        target.entrySet().forEach { (k, v) ->
            val fullKey = if (prefix.isEmpty()) k else "$prefix.$k"
            if (v.isJsonPrimitive) {
                result[fullKey] = v.asString
                if (!result.containsKey(k)) result[k] = v.asString
            } else if (v.isJsonObject) {
                extractEntries(v.asJsonObject, result, fullKey)
            }
        }
    }
}

// ---------------------------------------------------------------- 扩展方法（作用于 QinglongApi）

/** 读取系统配置里的常用项。字段随版本变化，只按名字取需要的键。 */
suspend fun QinglongV15Api.fetchSystemSettings(auth: String): Result<Map<String, String>> {
    val result = mutableMapOf<String, String>()
    try {
        runCatching {
            val env = this@fetchSystemSettings.getSystemConfig(auth)
                .unwrap("读取配置失败").getOrNull()
            val obj = env?.data?.takeIf { it.isJsonObject }?.asJsonObject
            if (obj != null) QinglongSystemHelpers.extractEntries(obj, result)
        }
        if (!result.containsKey("logRemoveFrequency") || !result.containsKey("cronConcurrency")) {
            runCatching {
                val sysEnv = this@fetchSystemSettings.getSystemInfo(auth)
                    .unwrap("读取系统信息失败").getOrNull()
                val sysObj = sysEnv?.data?.takeIf { it.isJsonObject }?.asJsonObject
                if (sysObj != null) QinglongSystemHelpers.extractEntries(sysObj, result)
            }
        }
        return Result.success(result)
    } catch (e: Exception) {
        return Result.failure(e)
    }
}

/** 日志保留天数，null 表示不自动清理 */
suspend fun QinglongV15Api.saveLogRemoveFrequency(auth: String, days: Int?): Result<Boolean> =
    this.updateLogRemoveFrequency(auth, mapOf("logRemoveFrequency" to days))
        .unwrap("保存日志保留天数失败").map { true }

/** 任务并发数，null 表示不限制 */
suspend fun QinglongV15Api.saveCronConcurrency(auth: String, count: Int?): Result<Boolean> =
    this.updateCronConcurrency(auth, mapOf("cronConcurrency" to count))
        .unwrap("保存任务并发数失败").map { true }

/** 发送测试通知 */
suspend fun QinglongV15Api.sendTestNotify(auth: String, title: String, content: String): Result<Boolean> =
    this.testNotify(auth, mapOf("title" to title, "content" to content))
        .unwrap("发送测试通知失败").map { true }

/** 修改配置后需重载才会对调度器生效 */
suspend fun QinglongV15Api.reloadSystem(auth: String, type: String? = null): Result<Boolean> =
    this.reloadSystem(auth, if (type != null) mapOf("type" to type) else emptyMap())
        .unwrap("重载配置失败").map { true }
