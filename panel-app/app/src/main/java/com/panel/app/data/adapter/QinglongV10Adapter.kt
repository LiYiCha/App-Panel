package com.panel.app.data.adapter

import com.panel.app.data.model.*
import com.panel.app.data.remote.NetworkClient
import com.panel.app.data.remote.api.*
import com.panel.app.data.remote.unwrap
import com.panel.app.data.remote.unwrapTo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * 青龙面板 2.10 ~ 2.14 适配器。
 *
 * 青龙鉴权用 `Authorization: Bearer <token>`
 *（见官方前端 `src/utils/http.tsx` 的请求拦截器），与白虎的 Cookie 机制不同。
 */
class QinglongV10Adapter(
    override val instance: PanelInstance
) : IPanelAdapter {

    private val api: QinglongV10Api = NetworkClient.buildRetrofit(instance.baseUrl).create(QinglongV10Api::class.java)
    private var currentToken: String? = instance.token

    private fun getAuthHeader(): String {
        val t = currentToken ?: instance.token ?: ""
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    private suspend fun ensureAuth(): Boolean {
        if (!currentToken.isNullOrEmpty()) return true
        if (!instance.token.isNullOrEmpty()) {
            currentToken = instance.token
            return true
        }
        if (!instance.username.isNullOrEmpty() && !instance.password.isNullOrEmpty()) {
            return authenticate().isSuccess
        }
        return false
    }

    private fun toIds(ids: List<String>): List<Long> = ids.mapNotNull { it.toLongOrNull() }

    override suspend fun authenticate(): Result<String> {
        val saved = instance.token
        if (!saved.isNullOrEmpty()) {
            currentToken = saved
            return Result.success(saved)
        }
        val resp = try {
            api.login(QlV10LoginReq(instance.username ?: "admin", instance.password ?: ""))
        } catch (e: Exception) {
            return Result.failure(Exception("连接失败: ${e.message ?: "网络超时，请检查面板地址"}"))
        }
        val envelope = resp.unwrap("登录失败").getOrElse { return Result.failure(it) }
        val token = envelope.data?.token
            ?: return Result.failure(Exception("登录失败: 服务端未返回 token"))
        currentToken = token
        return Result.success(token)
    }

    // ---------------------------------------------------------------- 1. 任务

    override suspend fun getTasks(query: String?): Result<List<UnifiedTask>> {
        ensureAuth()
        return try {
            api.getCrons(getAuthHeader(), query)
                .unwrapTo("获取任务列表失败") { env ->
                    parseCronArray(env.data).map { it.toUnifiedTask() }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 青龙 /crons 可能返回数组，也可能返回 {data: [...], total} */
    private fun parseCronArray(data: com.google.gson.JsonElement?): List<QlCronItem> {
        if (data == null) return emptyList()
        val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, QlCronItem::class.java).type
        return when {
            data.isJsonArray -> com.google.gson.Gson().fromJson(data, type) ?: emptyList()
            data.isJsonObject && data.asJsonObject.get("data")?.isJsonArray == true ->
                com.google.gson.Gson().fromJson(data.asJsonObject.get("data"), type) ?: emptyList()
            else -> emptyList()
        }
    }

    private fun QlCronItem.toUnifiedTask(): UnifiedTask {
        // CrontabStatus: 0=running, 1=idle, 2=disabled, 3=queued
        val running = status == 0
        val queued = status == 3
        val disabled = isDisabled == 1
        return UnifiedTask(
            id = cleanId(id),
            name = name.orEmpty(),
            command = command.orEmpty(),
            schedule = schedule.orEmpty(),
            statusText = when {
                running -> "运行中"
                queued -> "排队中"
                disabled -> "已禁用"
                else -> "已启用"
            },
            isRunning = running,
            isDisabled = disabled,
            isPinned = isPinned == 1,
            labels = labels.orEmpty(),
            lastRunningTime = last_running_time,
            lastExecutionTime = last_execution_time,
            createdAt = createdAt,
            updatedAt = updatedAt,
            pid = pid
        )
    }

    private fun cleanId(raw: Any?): String = when (raw) {
        is Number -> raw.toLong().toString()
        is String -> raw.toDoubleOrNull()?.toLong()?.toString() ?: raw.substringBefore('.')
        else -> raw?.toString()?.substringBefore('.') ?: ""
    }

    override suspend fun createTask(name: String, command: String, schedule: String): Result<Boolean> {
        ensureAuth()
        return api.createCron(getAuthHeader(), QlCreateCronReq(name, command, schedule))
            .unwrap("创建任务失败").map { true }
    }

    override suspend fun updateTask(task: UnifiedTask): Result<Boolean> {
        ensureAuth()
        val id: Any = task.id.toLongOrNull() ?: task.id
        return api.updateCron(getAuthHeader(), QlUpdateCronReq(id, task.name, task.command, task.schedule))
            .unwrap("更新任务失败").map { true }
    }

    override suspend fun runTask(taskIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(taskIds)
        if (ids.isEmpty()) return Result.failure(Exception("任务 ID 无效"))
        return api.runCrons(getAuthHeader(), ids).unwrap("运行任务失败").map { true }
    }

    override suspend fun stopTask(taskIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(taskIds)
        if (ids.isEmpty()) return Result.failure(Exception("任务 ID 无效"))
        return api.stopCrons(getAuthHeader(), ids).unwrap("停止任务失败").map { true }
    }

    override suspend fun toggleTask(taskId: String, enable: Boolean): Result<Boolean> {
        ensureAuth()
        val id = taskId.toLongOrNull() ?: return Result.failure(Exception("任务 ID 无效"))
        return (if (enable) api.enableCrons(getAuthHeader(), listOf(id))
        else api.disableCrons(getAuthHeader(), listOf(id)))
            .unwrap("切换任务状态失败").map { true }
    }

    override suspend fun deleteTask(taskIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(taskIds)
        if (ids.isEmpty()) return Result.failure(Exception("任务 ID 无效"))
        return api.deleteCrons(getAuthHeader(), ids).unwrap("删除任务失败").map { true }
    }

    override suspend fun pinTask(taskIds: List<String>, pin: Boolean): Result<Boolean> =
        Result.failure(Exception("青龙 2.10 不支持置顶接口"))

    override suspend fun getTaskInstances(taskId: String): Result<List<TaskInstanceRecord>> =
        Result.failure(Exception("青龙 2.10 无运行实例接口，请升级到 2.15+"))

    override suspend fun getTaskLog(taskNameOrId: String): Result<String> {
        ensureAuth()
        return api.getCronLog(getAuthHeader(), taskNameOrId.substringBefore('.'))
            .unwrapTo("读取任务日志失败") { it.data ?: "暂无运行日志输出" }
    }

    // ---------------------------------------------------------------- 2. 订阅（旧版无此模块）

    override suspend fun getSubscriptions(query: String?): Result<List<UnifiedSubscription>> =
        Result.failure(Exception("青龙 2.10 无订阅模块，请升级到 2.15+"))

    override suspend fun createSubscription(sub: UnifiedSubscription): Result<Boolean> =
        Result.failure(Exception("青龙 2.10 无订阅模块"))

    override suspend fun updateSubscription(sub: UnifiedSubscription): Result<Boolean> =
        Result.failure(Exception("青龙 2.10 无订阅模块"))

    override suspend fun deleteSubscription(subIds: List<String>): Result<Boolean> =
        Result.failure(Exception("青龙 2.10 无订阅模块"))

    override suspend fun runSubscription(subIds: List<String>): Result<Boolean> =
        Result.failure(Exception("青龙 2.10 无订阅模块"))

    override suspend fun stopSubscription(subIds: List<String>): Result<Boolean> =
        Result.failure(Exception("青龙 2.10 无订阅模块"))

    override suspend fun getSubscriptionLog(subId: String): Result<String> =
        Result.failure(Exception("青龙 2.10 无订阅模块"))

    // ---------------------------------------------------------------- 3. 环境变量

    override suspend fun getEnvs(query: String?): Result<List<UnifiedEnv>> {
        ensureAuth()
        return api.getEnvs(getAuthHeader(), query)
            .unwrapTo("获取环境变量失败") { env ->
                env.data.orEmpty().map { item ->
                    UnifiedEnv(
                        id = cleanId(item.id),
                        name = item.name,
                        value = item.value,
                        remarks = item.remarks,
                        // EnvStatus: 0=normal, 1=disabled
                        enabled = item.status == 0
                    )
                }
            }
    }

    override suspend fun saveEnv(env: UnifiedEnv): Result<Boolean> {
        ensureAuth()
        val isNew = env.id.isEmpty() || env.id.toLongOrNull() == null
        return (if (isNew) {
            api.createEnvs(getAuthHeader(), listOf(QlCreateEnvReq(env.name, env.value, env.remarks)))
                .unwrap("创建环境变量失败")
        } else {
            val id: Any = env.id.toLongOrNull() ?: env.id
            api.updateEnv(getAuthHeader(), QlUpdateEnvReq(id, env.name, env.value, env.remarks))
                .unwrap("更新环境变量失败")
        }).map { true }
    }

    override suspend fun toggleEnv(envId: String, enable: Boolean): Result<Boolean> {
        ensureAuth()
        val id = envId.toLongOrNull() ?: return Result.failure(Exception("环境变量 ID 无效"))
        return (if (enable) api.enableEnvs(getAuthHeader(), listOf(id))
        else api.disableEnvs(getAuthHeader(), listOf(id)))
            .unwrap("切换变量状态失败").map { true }
    }

    override suspend fun deleteEnv(envIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(envIds)
        if (ids.isEmpty()) return Result.failure(Exception("环境变量 ID 无效"))
        return api.deleteEnvs(getAuthHeader(), ids).unwrap("删除环境变量失败").map { true }
    }

    // ---------------------------------------------------------------- 4. 配置文件

    override suspend fun getConfigFiles(): Result<List<String>> =
        Result.success(listOf("config.sh", "extra.sh", "config.json"))

    override suspend fun readConfig(path: String): Result<String> {
        ensureAuth()
        return api.getConfig(getAuthHeader(), path)
            .unwrapTo("读取配置文件失败") { it.data ?: "" }
    }

    override suspend fun saveConfig(path: String, content: String): Result<Boolean> {
        ensureAuth()
        return api.saveConfig(getAuthHeader(), QlSaveConfigReq(path, content))
            .unwrap("保存配置失败").map { true }
    }

    // ---------------------------------------------------------------- 5. 脚本

    private fun mapScriptNode(item: QlScriptNodeItem): ScriptNode {
        val nodeName = item.title ?: item.name ?: item.value ?: item.key?.substringAfterLast('/') ?: "未命名"
        val nodePath = item.key ?: item.value ?: item.title ?: nodeName
        val isDirectory = item.type?.equals("directory", ignoreCase = true)
            ?: (!item.children.isNullOrEmpty() || item.isLeaf == false)
        return ScriptNode(
            name = nodeName,
            path = nodePath,
            isDir = isDirectory,
            size = if (isDirectory) null else "-",
            mtime = item.mtime,
            children = item.children?.map { mapScriptNode(it) }
        )
    }

    override suspend fun getScriptTree(): Result<List<ScriptNode>> {
        ensureAuth()
        return api.getScripts(getAuthHeader())
            .unwrapTo("获取脚本文件树失败") { env ->
                env.data.orEmpty().map { mapScriptNode(it) }
            }
    }

    override suspend fun readScript(path: String): Result<String> {
        ensureAuth()
        return api.getScriptContent(getAuthHeader(), path)
            .unwrapTo("读取脚本失败") { it.data ?: "" }
    }

    override suspend fun saveScript(path: String, content: String): Result<Boolean> {
        ensureAuth()
        val fileName = path.substringAfterLast("/")
        val dirPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
        return api.saveScript(getAuthHeader(), QlSaveScriptReq(fileName, content, dirPath))
            .unwrap("保存脚本失败").map { true }
    }

    override suspend fun createScript(path: String, content: String): Result<Boolean> {
        ensureAuth()
        val fileName = path.substringAfterLast("/")
        val dirPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
        return api.createScript(getAuthHeader(), QlCreateScriptReq(fileName, content, dirPath))
            .unwrap("新建脚本失败").map { true }
    }

    override suspend fun createDirectory(path: String): Result<Boolean> {
        ensureAuth()
        val dirName = path.substringAfterLast("/")
        val parentPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
        return api.createScript(
            getAuthHeader(),
            QlCreateScriptReq(filename = dirName, directory = dirName, path = parentPath)
        ).unwrap("新建目录失败").map { true }
    }

    override suspend fun deleteScript(path: String): Result<Boolean> {
        ensureAuth()
        val fileName = path.substringAfterLast("/")
        val dirPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
        return api.deleteScript(getAuthHeader(), QlDeleteScriptReq(fileName, dirPath))
            .unwrap("删除脚本失败").map { true }
    }

    // ---------------------------------------------------------------- 6. 依赖

    override suspend fun getDeps(query: String?): Result<List<UnifiedDep>> {
        ensureAuth()
        return api.getDependencies(getAuthHeader(), query)
            .unwrapTo("获取依赖失败") { env ->
                parseDepArray(env.data).map { it.toUnifiedDep() }
            }
    }

    private fun parseDepArray(data: com.google.gson.JsonElement?): List<QlDepItem> {
        if (data == null) return emptyList()
        val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, QlDepItem::class.java).type
        return when {
            data.isJsonArray -> com.google.gson.Gson().fromJson(data, type) ?: emptyList()
            data.isJsonObject && data.asJsonObject.get("data")?.isJsonArray == true ->
                com.google.gson.Gson().fromJson(data.asJsonObject.get("data"), type) ?: emptyList()
            else -> emptyList()
        }
    }

    private fun QlDepItem.toUnifiedDep(): UnifiedDep = UnifiedDep(
        id = cleanId(id),
        name = name,
        version = "",
        type = depTypeToString(type),
        remarks = remark,
        status = status ?: 1,
        log = log?.joinToString("\n")
    )

    private fun depTypeToString(raw: Any?): String = when (raw) {
        is Number -> when (raw.toInt()) { 0 -> "nodejs"; 1 -> "python3"; 2 -> "linux"; else -> "nodejs" }
        is String -> when (raw.lowercase()) {
            "0", "nodejs", "node" -> "nodejs"
            "1", "python3", "python" -> "python3"
            "2", "linux" -> "linux"
            else -> raw.lowercase()
        }
        else -> "nodejs"
    }

    override suspend fun installDep(name: String, version: String, type: String, remark: String): Result<Boolean> {
        ensureAuth()
        val typeInt = when (type.lowercase()) {
            "nodejs", "node" -> 0
            "python3", "python" -> 1
            else -> 2
        }
        return api.installDependencies(
            getAuthHeader(),
            listOf(QlCreateDepReq(name, typeInt, remark.trim().ifEmpty { null }))
        ).unwrap("安装依赖失败").map { true }
    }

    override suspend fun deleteDep(depId: String, type: String): Result<Boolean> = batchDeleteDeps(listOf(depId))

    override suspend fun batchDeleteDeps(depIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(depIds)
        if (ids.isEmpty()) return Result.failure(Exception("依赖 ID 无效"))
        return api.deleteDependencies(getAuthHeader(), ids).unwrap("删除依赖失败").map { true }
    }

    override suspend fun forceDeleteDeps(depIds: List<String>): Result<Boolean> = batchDeleteDeps(depIds)

    override suspend fun getDepLog(depId: String): Result<String> =
        Result.failure(Exception("青龙 2.10 无依赖日志接口，请升级到 2.15+"))

    // ---------------------------------------------------------------- 7. 日志流

    override fun streamLog(logId: String): Flow<String> = flow {
        var last = ""
        while (currentCoroutineContext().isActive) {
            val res = api.getCronLog(getAuthHeader(), logId).unwrap("读取日志失败")
            if (res.isFailure) {
                emit("[ERROR] ${res.exceptionOrNull()?.message}")
                return@flow
            }
            val chunk = res.getOrNull()
            val content = chunk?.data ?: ""
            if (content != last) {
                last = content
                emit(content)
            }
            if (chunk?.logStatus != "running") return@flow
            delay(2000)
        }
    }

    // ---------------------------------------------------------------- 8. 监控 / 审计

    override suspend fun getMetrics(): Result<Pair<String, String>> =
        Result.failure(Exception("青龙 2.10 无监控接口，请升级到 2.15+"))

    override suspend fun getLoginLogs(): Result<List<Map<String, Any>>> =
        Result.failure(Exception("青龙 2.10 无登录日志接口"))

    override suspend fun getLogsTree(): Result<com.google.gson.JsonElement> =
        Result.failure(Exception("青龙 2.10 无日志文件树接口"))

    override suspend fun getLogDetail(path: String, file: String): Result<String> =
        Result.failure(Exception("青龙 2.10 无日志详情接口"))

    suspend fun fetchSystemSettings(): Result<Map<String, String>> =
        QinglongV15Adapter(instance).fetchSystemSettings()

    suspend fun saveLogRemoveFrequency(days: Int?): Result<Boolean> =
        QinglongV15Adapter(instance).saveLogRemoveFrequency(days)

    suspend fun saveCronConcurrency(count: Int?): Result<Boolean> =
        QinglongV15Adapter(instance).saveCronConcurrency(count)

    suspend fun sendTestNotify(title: String, content: String): Result<Boolean> =
        QinglongV15Adapter(instance).sendTestNotify(title, content)

    suspend fun reloadSystem(type: String? = null): Result<Boolean> =
        QinglongV15Adapter(instance).reloadSystem(type)
}
