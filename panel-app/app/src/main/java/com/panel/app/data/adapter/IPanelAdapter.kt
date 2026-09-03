package com.panel.app.data.adapter

import com.panel.app.data.backup.*
import com.panel.app.data.model.*
import kotlinx.coroutines.flow.Flow

interface IPanelAdapter {
    val instance: PanelInstance

    // 1. 认证
    suspend fun authenticate(): Result<String>

    /**
     * 退出登录。面板侧吊销会话后，由调用方负责清空本地保存的 token。
     * 面板不支持该接口时静默成功（本地清除即可）。
     */
    suspend fun logout(): Result<Boolean> = Result.success(true)

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
    suspend fun deleteTaskInstance(instanceId: String): Result<Boolean> =
        Result.failure(Exception("当前面板不支持删除任务实例"))
    suspend fun batchDeleteTaskInstances(instanceIds: List<String>): Result<Boolean> =
        Result.failure(Exception("当前面板不支持批量删除任务实例"))

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

    // ==================== 备份 / 恢复 ====================

    /** 恢复任务列表，返回逐项报告。默认只恢复 name/command/schedule */
    suspend fun restoreTasks(tasks: List<BackupTask>): Result<RestoreReport> {
        val errors = mutableListOf<String>()
        var ok = 0
        var skipped = 0
        for (t in tasks) {
            if (t.name.isBlank() || t.command.isBlank() || t.schedule.isBlank()) {
                skipped++
                continue
            }
            createTask(t.name, t.command, t.schedule)
                .onSuccess { ok++ }
                .onFailure { errors.add("${t.name}: ${it.message}") }
        }
        return Result.success(RestoreReport("任务", tasks.size, ok, skipped, errors))
    }

    /** 恢复环境变量列表，返回逐项报告。重复项自动跳过 */
    suspend fun restoreEnvs(envs: List<BackupEnv>): Result<RestoreReport> {
        val errors = mutableListOf<String>()
        var ok = 0
        var skipped = 0
        for (e in envs) {
            if (e.name.isBlank()) {
                skipped++
                continue
            }
            val res = saveEnv(UnifiedEnv(id = "", name = e.name, value = e.value, remarks = e.remarks, enabled = e.enabled))
            if (res.isFailure) {
                val errMsg = res.exceptionOrNull()?.message ?: ""
                if ("已存在".contains(errMsg) || "exist".equals(errMsg, ignoreCase = true) ||
                    "duplicate".equals(errMsg, ignoreCase = true) || errMsg.contains("重复")) {
                    skipped++
                    continue
                }
                errors.add("${e.name}: ${res.exceptionOrNull()?.message}")
            } else {
                ok++
            }
        }
        return Result.success(RestoreReport("环境变量", envs.size, ok, skipped, errors))
    }

    /** 恢复脚本列表（逐条创建），返回逐项报告。重复项自动跳过 */
    suspend fun restoreScripts(scripts: List<BackupScript>): Result<RestoreReport> {
        val errors = mutableListOf<String>()
        var ok = 0
        var skipped = 0
        for (s in scripts) {
            if (s.path.isBlank()) {
                skipped++
                continue
            }
            val res = createScript(s.path, s.content)
            if (res.isFailure) {
                val errMsg = res.exceptionOrNull()?.message ?: ""
                if ("已存在".contains(errMsg) || "exist".equals(errMsg, ignoreCase = true) ||
                    "duplicate".equals(errMsg, ignoreCase = true) || errMsg.contains("重复")) {
                    skipped++
                    continue
                }
                errors.add("${s.path}: ${res.exceptionOrNull()?.message}")
            } else {
                ok++
            }
        }
        return Result.success(RestoreReport("脚本", scripts.size, ok, skipped, errors))
    }

    /**
     * 恢复配置文件。配置文件结构因面板而异，
     * 只有来源面板与目标面板类型一致时才允许恢复，否则整类跳过。
     * 重复项自动跳过。
     */
    suspend fun restoreConfigFiles(
        files: List<BackupConfigFile>,
        sourceType: PanelType
    ): Result<RestoreReport> {
        if (files.isEmpty()) return Result.success(RestoreReport("配置文件", 0, 0, 0, emptyList()))
        if (sourceType != instance.type) {
            return Result.success(
                RestoreReport("配置文件", files.size, 0, files.size, listOf("来源面板与当前面板类型不同，已跳过配置文件"))
            )
        }
        val errors = mutableListOf<String>()
        var ok = 0
        var skipped = 0
        for (f in files) {
            if (f.path.isBlank()) {
                skipped++
                continue
            }
            val res = saveConfig(f.path, f.content)
            if (res.isFailure) {
                val errMsg = res.exceptionOrNull()?.message ?: ""
                if ("已存在".contains(errMsg) || "exist".equals(errMsg, ignoreCase = true) ||
                    "duplicate".equals(errMsg, ignoreCase = true) || errMsg.contains("重复")) {
                    skipped++
                    continue
                }
                errors.add("${f.path}: ${res.exceptionOrNull()?.message}")
            } else {
                ok++
            }
        }
        return Result.success(RestoreReport("配置文件", files.size, ok, skipped, errors))
    }

    /**
     * 批量导入环境变量，返回成功条数。
     * 默认实现逐条调用 saveEnv；支持批量接口的面板应覆写为单次请求。
     * 关键点：必须把失败暴露出来，导入失败却提示成功是最坑的静默错误。
     */
    suspend fun importEnvs(envs: List<UnifiedEnv>): Result<Int> {
        if (envs.isEmpty()) return Result.failure(Exception("没有可导入的环境变量"))
        val errors = mutableListOf<String>()
        var ok = 0
        for (env in envs) {
            saveEnv(env)
                .onSuccess { ok++ }
                .onFailure { errors.add("${env.name}: ${it.message}") }
        }
        return if (errors.isEmpty()) Result.success(ok)
        else Result.failure(Exception("成功 $ok 条；失败 ${errors.size} 条 — ${errors.take(3).joinToString("; ")}"))
    }

    /** 置顶环境变量（id 列表） */
    suspend fun pinEnv(envIds: List<String>): Result<Boolean> =
        Result.failure(Exception("当前面板不支持置顶环境变量"))

    /** 取消置顶 */
    suspend fun unpinEnv(envIds: List<String>): Result<Boolean> =
        Result.failure(Exception("当前面板不支持取消置顶环境变量"))

    /** 移动环境变量位置（fromIndex → toIndex，0-based） */
    suspend fun moveEnv(envId: String, fromIndex: Int, toIndex: Int): Result<Boolean> =
        Result.failure(Exception("当前面板不支持移动环境变量位置"))

    /** 批量启用环境变量 */
    suspend fun batchEnableEnvs(envIds: List<String>): Result<Boolean> =
        Result.failure(Exception("当前面板不支持批量启用环境变量"))

    /** 批量禁用环境变量 */
    suspend fun batchDisableEnvs(envIds: List<String>): Result<Boolean> =
        Result.failure(Exception("当前面板不支持批量禁用环境变量"))

    /** 导出选中环境变量为 JSON 字符串（供分享/保存用） */
    suspend fun exportEnvsJson(envIds: List<String>): Result<String> {
        val all = getEnvs(null).getOrNull() ?: return Result.failure(Exception("导出失败：无法获取变量列表"))
        val subset = all.filter { it.id in envIds }
        if (subset.isEmpty()) return Result.failure(Exception("未选中任何变量"))
        val json = subset.joinToString(",\n") { e ->
            """  {"name": "${e.name.replace("\"", "\\\"")}",
               "value": "${e.value.replace("\"", "\\\"")}",
               "remarks": "${(e.remarks ?: "").replace("\"", "\\\"")}",
               "enabled": ${e.enabled}}"""
        }
        return Result.success("[$json\n]")
    }

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

    /** 重命名/移动。青龙走 PUT /scripts/rename，白虎走 POST /files/rename 或 /files/move */
    suspend fun renameScript(path: String, newPath: String): Result<Boolean> =
        Result.failure(Exception("当前面板不支持重命名"))

    /** 复制文件。青龙无对应接口，白虎走 POST /files/copy */
    suspend fun copyScript(path: String, newPath: String): Result<Boolean> =
        Result.failure(Exception("当前面板不支持复制文件"))

    // 7. 依赖包管理 (Dependencies)
    suspend fun getDeps(query: String? = null): Result<List<UnifiedDep>>
    suspend fun installDep(name: String, version: String, type: String, remark: String): Result<Boolean>
    suspend fun deleteDep(depId: String, type: String): Result<Boolean>
    suspend fun batchDeleteDeps(depIds: List<String>): Result<Boolean>
    suspend fun forceDeleteDeps(depIds: List<String>): Result<Boolean>
    suspend fun getDepLog(depId: String): Result<String>

    /** 重新安装依赖。青龙走 /dependencies/reinstall，白虎走 /deps/reinstall/{id} */
    suspend fun reinstallDeps(depIds: List<String>): Result<Boolean> =
        Result.failure(Exception("当前面板类型不支持重新安装依赖"))

    /** 取消正在进行的安装。目前只有青龙提供 /dependencies/cancel */
    suspend fun cancelDeps(depIds: List<String>): Result<Boolean> =
        Result.failure(Exception("当前面板类型不支持取消安装"))

    /**
     * 当前正在运行的任务实例。
     * 与"停止任务"配套使用：只有拿到真实运行实例才能精确停止，
     * 直接拿任务 ID 去停止在白虎侧会失败（那里需要的是日志 ID）。
     */
    suspend fun getRunningTasks(): Result<List<RunningTaskInfo>> =
        Result.failure(Exception("当前面板不支持查询运行中任务"))

    /** 停止一个运行中的实例。instanceId 为 null 时由适配器自行解析（白虎） */
    suspend fun stopRunningTask(taskId: String, instanceId: String?): Result<Boolean> =
        stopTask(listOf(taskId))

    fun streamLog(logId: String): Flow<String>

    // 8. 硬件与性能监控 (Metrics)
    suspend fun getMetrics(): Result<Pair<String, String>>

    /** 仪表盘聚合数据。面板不支持时返回失败，UI 显示空态而不是假数据 */
    suspend fun getDashboard(): Result<PanelDashboard> =
        Result.failure(Exception("当前面板不支持仪表盘统计"))

    // 9. 审计与系统中心
    suspend fun getLoginLogs(): Result<List<Map<String, Any>>>
    suspend fun getLogsTree(): Result<com.google.gson.JsonElement>
    suspend fun getLogDetail(path: String, file: String): Result<String>
}
