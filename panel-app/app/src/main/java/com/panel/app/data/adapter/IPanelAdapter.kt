package com.panel.app.data.adapter

import com.panel.app.data.model.*
import kotlinx.coroutines.flow.Flow

interface IPanelAdapter {
    val instance: PanelInstance

    // 1. 认证
    suspend fun authenticate(): Result<String>

    // 2. 任务 (Tasks / Crons)
    suspend fun getTasks(query: String? = null): Result<List<UnifiedTask>>
    suspend fun createTask(name: String, command: String, schedule: String): Result<Boolean>
    suspend fun updateTask(task: UnifiedTask): Result<Boolean>
    suspend fun runTask(taskIds: List<String>): Result<Boolean>
    suspend fun stopTask(taskIds: List<String>): Result<Boolean>
    suspend fun toggleTask(taskId: String, enable: Boolean): Result<Boolean>
    suspend fun deleteTask(taskIds: List<String>): Result<Boolean>
    suspend fun pinTask(taskIds: List<String>, pin: Boolean): Result<Boolean>
    suspend fun getTaskInstances(taskId: String): Result<List<TaskInstanceRecord>>
    suspend fun getTaskLog(taskNameOrId: String): Result<String>

    // 3. 订阅管理 (Subscriptions)
    suspend fun getSubscriptions(query: String? = null): Result<List<UnifiedSubscription>>
    suspend fun createSubscription(sub: UnifiedSubscription): Result<Boolean>
    suspend fun updateSubscription(sub: UnifiedSubscription): Result<Boolean>
    suspend fun deleteSubscription(subIds: List<String>): Result<Boolean>
    suspend fun runSubscription(subIds: List<String>): Result<Boolean>
    suspend fun stopSubscription(subIds: List<String>): Result<Boolean>
    suspend fun getSubscriptionLog(subId: String): Result<String>

    // 4. 环境变量 (Envs)
    suspend fun getEnvs(query: String? = null): Result<List<UnifiedEnv>>
    suspend fun saveEnv(env: UnifiedEnv): Result<Boolean>
    suspend fun toggleEnv(envId: String, enable: Boolean): Result<Boolean>
    suspend fun deleteEnv(envIds: List<String>): Result<Boolean>

    // 5. 配置文件 (Configs: config.sh / config.json / extra.sh)
    suspend fun getConfigFiles(): Result<List<String>>
    suspend fun readConfig(path: String): Result<String>
    suspend fun saveConfig(path: String, content: String): Result<Boolean>

    // 6. 脚本文件 (Scripts)
    suspend fun getScriptTree(): Result<List<ScriptNode>>
    suspend fun readScript(path: String): Result<String>
    suspend fun saveScript(path: String, content: String): Result<Boolean>
    suspend fun createScript(path: String, content: String): Result<Boolean>
    suspend fun createDirectory(path: String): Result<Boolean>
    suspend fun deleteScript(path: String): Result<Boolean>

    // 7. 依赖包管理 (Dependencies)
    suspend fun getDeps(query: String? = null): Result<List<UnifiedDep>>
    suspend fun installDep(name: String, version: String, type: String, remark: String): Result<Boolean>
    suspend fun deleteDep(depId: String, type: String): Result<Boolean>
    suspend fun batchDeleteDeps(depIds: List<String>): Result<Boolean>
    suspend fun forceDeleteDeps(depIds: List<String>): Result<Boolean>
    suspend fun getDepLog(depId: String): Result<String>

    fun streamLog(logId: String): Flow<String>

    // 8. 硬件与性能监控 (Metrics)
    suspend fun getMetrics(): Result<Pair<String, String>>

    // 9. 审计与系统中心
    suspend fun getLoginLogs(): Result<List<Map<String, Any>>>
    suspend fun getLogsTree(): Result<com.google.gson.JsonElement>
    suspend fun getLogDetail(path: String, file: String): Result<String>
}
