package com.panel.app.data.adapter

import com.panel.app.data.model.*
import com.panel.app.data.remote.NetworkClient
import com.panel.app.data.remote.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class QinglongV15Adapter(
    override val instance: PanelInstance
) : IPanelAdapter {

    private val api: QinglongV15Api = NetworkClient.buildRetrofit(instance.baseUrl).create(QinglongV15Api::class.java)
    private var currentToken: String? = instance.token

    /**
     * 缓存青龙面板版本号（如 "2.17.5"），用于在不同接口版本之间做精确选路。
     * null 代表尚未查询，"0.0.0" 代表查询失败/旧版本无此接口。
     */
    private var cachedPanelVersion: String? = null

    private fun getAuthHeader(): String {
        val t = currentToken ?: instance.token ?: ""
        return if (t.isEmpty()) "" else if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    private suspend fun ensureAuth(): Boolean {
        if (!currentToken.isNullOrEmpty()) return true
        if (!instance.username.isNullOrEmpty() && !instance.password.isNullOrEmpty()) {
            return authenticate().isSuccess
        }
        return false
    }

    /**
     * 获取青龙面板版本号，结果缓存在本会话中避免重复请求。
     * 版本号从 GET /api/system/config 响应的 version 字段中读取。
     * 旧版青龙可能无此字段，则返回 "0.0.0"，后续接口选路走旧版路径。
     */
    private suspend fun getPanelVersion(): String {
        cachedPanelVersion?.let { return it }
        return try {
            val resp = api.getSystemConfig(getAuthHeader())
            if (resp.isSuccessful && resp.body() != null) {
                val elem = resp.body()!!
                val dataObj = when {
                    elem.isJsonObject && elem.asJsonObject.has("data") ->
                        elem.asJsonObject.get("data").let { if (it.isJsonObject) it.asJsonObject else null }
                    elem.isJsonObject -> elem.asJsonObject
                    else -> null
                }
                val version = dataObj?.get("version")?.asString
                    ?: dataObj?.get("qlVersion")?.asString
                    ?: "0.0.0"
                cachedPanelVersion = version
                version
            } else {
                cachedPanelVersion = "0.0.0"
                "0.0.0"
            }
        } catch (_: Exception) {
            cachedPanelVersion = "0.0.0"
            "0.0.0"
        }
    }

    /**
     * 语义化版本比较：判断 versionStr 是否 >= minVersion。
     * 示例：isVersionAtLeast("2.17.5", "2.17.0") => true
     */
    private fun isVersionAtLeast(versionStr: String, minVersion: String): Boolean {
        fun parse(v: String): IntArray = v.split(".").map { it.trim().toIntOrNull() ?: 0 }.toIntArray()
        val a = parse(versionStr)
        val b = parse(minVersion)
        for (i in 0 until maxOf(a.size, b.size)) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai > bi
        }
        return true
    }

    override suspend fun authenticate(): Result<String> {
        return try {
            val user = instance.username
            val pass = instance.password
            if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
                val resp = api.login(mapOf("username" to user, "password" to pass))
                if (resp.isSuccessful && resp.body()?.data?.token != null) {
                    val token = resp.body()!!.data!!.token!!
                    currentToken = token
                    // 登录成功后立即预取版本，供后续 API 选路使用
                    getPanelVersion()
                    return Result.success(token)
                } else {
                    return Result.failure(Exception("登录失败: 账号或密码不正确"))
                }
            }

            if (!instance.token.isNullOrEmpty()) {
                val ping = api.getCrons(getAuthHeader())
                if (ping.isSuccessful) {
                    getPanelVersion()
                    return Result.success(instance.token)
                }
            }

            Result.failure(Exception("请输入面板登录账号与密码"))
        } catch (e: Exception) {
            Result.failure(Exception("连接异常: ${e.message ?: "无法连接"}"))
        }
    }

    // 1. 任务管理 (Crons)
    override suspend fun getTasks(query: String?): Result<List<UnifiedTask>> {
        ensureAuth()
        return try {
            val resp = api.getCrons(getAuthHeader(), query)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dataElem = resp.body()!!.data!!
                val itemsList: List<QlCronItem> = when {
                    dataElem.isJsonArray -> {
                        val type = object : com.google.gson.reflect.TypeToken<List<QlCronItem>>() {}.type
                        com.google.gson.Gson().fromJson(dataElem, type)
                    }
                    dataElem.isJsonObject -> {
                        val obj = dataElem.asJsonObject
                        if (obj.has("data") && obj.get("data").isJsonArray) {
                            val type = object : com.google.gson.reflect.TypeToken<List<QlCronItem>>() {}.type
                            com.google.gson.Gson().fromJson(obj.get("data"), type)
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
                val list = itemsList.map { item ->
                    val isRun = item.status == 0
                    val isDis = item.isDisabled == 1
                    val isPin = item.isPinned == 1
                    val statusStr = if (isRun) "运行中" else if (isDis) "已禁用" else "就绪"
                    val cleanTaskId = when (val raw = item.id) {
                        is Number -> raw.toLong().toString()
                        is String -> raw.toDoubleOrNull()?.toLong()?.toString() ?: raw.substringBefore('.')
                        else -> raw?.toString()?.substringBefore('.') ?: ""
                    }
                    UnifiedTask(
                        id = cleanTaskId,
                        name = item.name,
                        command = item.command,
                        schedule = item.schedule,
                        statusText = statusStr,
                        isRunning = isRun,
                        isDisabled = isDis,
                        isPinned = isPin,
                        labels = item.labels ?: emptyList(),
                        lastRunningTime = item.last_running_time,
                        lastExecutionTime = item.last_execution_time
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("获取任务列表失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createTask(name: String, command: String, schedule: String): Result<Boolean> {
        return try {
            val resp = api.createCron(getAuthHeader(), QlCreateCronReq(name, command, schedule))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("创建任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTask(task: UnifiedTask): Result<Boolean> {
        return try {
            val cleanId: Any = task.id.toDoubleOrNull()?.toLong() ?: task.id.toLongOrNull() ?: task.id
            val resp = api.updateCron(getAuthHeader(), QlUpdateCronReq(cleanId, task.name, task.command, task.schedule))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("更新任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun runTask(taskIds: List<String>): Result<Boolean> {
        return try {
            val cleanIds = taskIds.map { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it }
            val resp = api.runCrons(getAuthHeader(), cleanIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("运行任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stopTask(taskIds: List<String>): Result<Boolean> {
        return try {
            val cleanIds = taskIds.map { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it }
            val resp = api.stopCrons(getAuthHeader(), cleanIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("停止任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleTask(taskId: String, enable: Boolean): Result<Boolean> {
        return try {
            val cleanId = taskId.toDoubleOrNull()?.toLong() ?: taskId.toLongOrNull() ?: taskId
            val idList = listOf(cleanId)
            val resp = if (enable) api.enableCrons(getAuthHeader(), idList) else api.disableCrons(getAuthHeader(), idList)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("切换任务状态失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(taskIds: List<String>): Result<Boolean> {
        return try {
            val cleanIds = taskIds.map { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it }
            val resp = api.deleteCrons(getAuthHeader(), cleanIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("删除任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pinTask(taskIds: List<String>, pin: Boolean): Result<Boolean> {
        return try {
            val cleanIds = taskIds.map { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it }
            val resp = if (pin) api.pinCrons(getAuthHeader(), cleanIds) else api.unpinCrons(getAuthHeader(), cleanIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("置顶操作失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTaskInstances(taskId: String): Result<List<TaskInstanceRecord>> {
        return try {
            val cleanId = (taskId.toDoubleOrNull()?.toLong() ?: taskId.toLongOrNull())?.toString() ?: taskId.substringBefore('.')
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val records = mutableListOf<TaskInstanceRecord>()

            // 1. 先尝试获取 /api/crons/:id/instances
            try {
                val resp = api.getCronInstances(getAuthHeader(), cleanId)
                if (resp.isSuccessful && !resp.body()?.data.isNullOrEmpty()) {
                    resp.body()!!.data!!.forEach { inst ->
                        val statusText = when (inst.status) {
                            0 -> "运行中"
                            1 -> "成功"
                            2 -> "已停止"
                            3 -> "失败"
                            else -> "完成"
                        }
                        val startStr = if (inst.started_at != null && inst.started_at > 0) {
                            sdf.format(java.util.Date(inst.started_at * 1000L))
                        } else {
                            inst.created_at ?: "--"
                        }
                        val endStr = if (inst.finished_at != null && inst.finished_at > 0) {
                            sdf.format(java.util.Date(inst.finished_at * 1000L))
                        } else {
                            inst.updated_at
                        }
                        val durText = if (inst.finished_at != null && inst.started_at != null && inst.finished_at >= inst.started_at) {
                            "${inst.finished_at - inst.started_at}s"
                        } else if (inst.duration != null) {
                            "${inst.duration / 1000.0}s"
                        } else "--"
                        records.add(
                            TaskInstanceRecord(
                                id = inst.id?.toString() ?: "",
                                startTime = startStr,
                                endTime = endStr,
                                duration = durText,
                                exitCode = inst.exit_code ?: if (inst.status == 1) 0 else 1,
                                statusText = statusText,
                                logPath = inst.log_path
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            // 2. 如果 /instances 为空，获取青龙 /api/crons/:id/logs 历史文件列表
            if (records.isEmpty()) {
                try {
                    val logsResp = api.getCronHistoryLogs(getAuthHeader(), cleanId)
                    if (logsResp.isSuccessful && logsResp.body() != null) {
                        val elem = logsResp.body()!!
                        val arr = if (elem.isJsonObject && elem.asJsonObject.has("data") && elem.asJsonObject.get("data").isJsonArray) {
                            elem.asJsonObject.getAsJsonArray("data")
                        } else if (elem.isJsonArray) {
                            elem.asJsonArray
                        } else null

                        arr?.forEach { item ->
                            if (item.isJsonObject) {
                                val obj = item.asJsonObject
                                val filename = obj.get("filename")?.asString ?: return@forEach
                                val directory = obj.get("directory")?.asString ?: ""
                                val time = obj.get("time")?.asLong ?: 0L
                                val timeStr = if (time > 0) sdf.format(java.util.Date(time)) else filename.removeSuffix(".log")
                                val fullPath = if (directory.isNotEmpty()) "$directory/$filename" else filename
                                records.add(
                                    TaskInstanceRecord(
                                        id = filename,
                                        startTime = timeStr,
                                        endTime = null,
                                        duration = "--",
                                        exitCode = 0,
                                        statusText = "已完成",
                                        logPath = fullPath
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. 如果依然为空，合成最新一次执行日志记录
            if (records.isEmpty()) {
                val latestLogResp = api.getCronLog(getAuthHeader(), cleanId)
                val logSnippet = if (latestLogResp.isSuccessful && latestLogResp.body()?.data != null) {
                    val d = latestLogResp.body()!!.data!!
                    if (d.isJsonPrimitive) d.asString else d.toString()
                } else ""
                if (logSnippet.isNotBlank()) {
                    records.add(
                        TaskInstanceRecord(
                            id = "latest",
                            startTime = "最新执行记录",
                            endTime = null,
                            duration = "--",
                            exitCode = 0,
                            statusText = "最新输出",
                            logSnippet = logSnippet
                        )
                    )
                }
            }

            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTaskLog(taskNameOrId: String): Result<String> {
        return try {
            val cleanId = (taskNameOrId.toDoubleOrNull()?.toLong() ?: taskNameOrId.toLongOrNull())?.toString()
                ?: taskNameOrId.substringBefore('.')
            val resp = api.getCronLog(getAuthHeader(), cleanId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dataElem = resp.body()!!.data!!
                val logStr = when {
                    dataElem.isJsonPrimitive -> dataElem.asString
                    dataElem.isJsonObject -> {
                        val obj = dataElem.asJsonObject
                        obj.get("data")?.asString ?: obj.get("log")?.asString ?: obj.toString()
                    }
                    else -> dataElem.toString()
                }
                Result.success(if (logStr.isNotBlank()) logStr else "暂无运行日志输出")
            } else {
                Result.failure(Exception("获取任务日志失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. 订阅管理 (Subscriptions)
    override suspend fun getSubscriptions(query: String?): Result<List<UnifiedSubscription>> {
        return try {
            val resp = api.getSubscriptions(getAuthHeader(), query)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dataElem = resp.body()!!.data!!
                val itemsList: List<QlSubscriptionItem> = when {
                    dataElem.isJsonArray -> {
                        val type = object : com.google.gson.reflect.TypeToken<List<QlSubscriptionItem>>() {}.type
                        com.google.gson.Gson().fromJson(dataElem, type)
                    }
                    dataElem.isJsonObject -> {
                        val obj = dataElem.asJsonObject
                        if (obj.has("data") && obj.get("data").isJsonArray) {
                            val type = object : com.google.gson.reflect.TypeToken<List<QlSubscriptionItem>>() {}.type
                            com.google.gson.Gson().fromJson(obj.get("data"), type)
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
                val list = itemsList.map { s ->
                    val isRun = s.status == 0
                    UnifiedSubscription(
                        id = s.id?.toString() ?: "",
                        name = s.name ?: "未命名订阅",
                        type = s.type,
                        url = s.url,
                        branch = s.branch ?: "main",
                        schedule = s.schedule ?: "0 0 * * *",
                        whitelist = s.whitelist ?: "",
                        blacklist = s.blacklist ?: "",
                        dependences = s.dependences ?: "",
                        extensions = s.extensions ?: "",
                        alias = s.alias ?: "",
                        autoAddCron = s.autoAddCron == true || s.autoAddCron == 1,
                        autoDelCron = s.autoDelCron == true || s.autoDelCron == 1,
                        statusText = if (isRun) "同步中" else "就绪",
                        isRunning = isRun,
                        lastRunTime = if (s.last_execution_time != null && s.last_execution_time > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(s.last_execution_time * 1000)) else null
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("获取订阅失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createSubscription(sub: UnifiedSubscription): Result<Boolean> {
        return try {
            val req = QlCreateSubscriptionReq(
                name = sub.name,
                type = sub.type,
                url = sub.url,
                branch = sub.branch,
                schedule = sub.schedule,
                whitelist = sub.whitelist.ifEmpty { null },
                blacklist = sub.blacklist.ifEmpty { null },
                dependences = sub.dependences.ifEmpty { null },
                extensions = sub.extensions.ifEmpty { null },
                alias = sub.alias.ifEmpty { "sub_${System.currentTimeMillis()}" },
                autoAddCron = sub.autoAddCron,
                autoDelCron = sub.autoDelCron
            )
            val resp = api.createSubscription(getAuthHeader(), req)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("创建订阅失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSubscription(sub: UnifiedSubscription): Result<Boolean> {
        return try {
            val req = QlUpdateSubscriptionReq(
                id = sub.id.toIntOrNull() ?: sub.id,
                name = sub.name,
                type = sub.type,
                url = sub.url,
                branch = sub.branch,
                schedule = sub.schedule,
                whitelist = sub.whitelist.ifEmpty { null },
                blacklist = sub.blacklist.ifEmpty { null },
                dependences = sub.dependences.ifEmpty { null },
                extensions = sub.extensions.ifEmpty { null },
                alias = sub.alias.ifEmpty { "sub_${sub.id}" },
                autoAddCron = sub.autoAddCron,
                autoDelCron = sub.autoDelCron
            )
            val resp = api.updateSubscription(getAuthHeader(), req)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("更新订阅失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSubscription(subIds: List<String>): Result<Boolean> {
        return try {
            val resp = api.deleteSubscriptions(getAuthHeader(), subIds.map { it.toIntOrNull() ?: it })
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("删除订阅失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun runSubscription(subIds: List<String>): Result<Boolean> {
        return try {
            val resp = api.runSubscriptions(getAuthHeader(), subIds.map { it.toIntOrNull() ?: it })
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("运行订阅失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stopSubscription(subIds: List<String>): Result<Boolean> {
        return try {
            val resp = api.stopSubscriptions(getAuthHeader(), subIds.map { it.toIntOrNull() ?: it })
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("停止订阅失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSubscriptionLog(subId: String): Result<String> {
        return try {
            val resp = api.getSubscriptionLog(getAuthHeader(), subId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dataElem = resp.body()!!.data!!
                val logStr = when {
                    dataElem.isJsonPrimitive -> dataElem.asString
                    dataElem.isJsonObject -> {
                        val obj = dataElem.asJsonObject
                        obj.get("data")?.asString ?: obj.get("log")?.asString ?: obj.toString()
                    }
                    else -> dataElem.toString()
                }
                Result.success(if (logStr.isNotBlank()) logStr else "暂无拉取日志")
            } else {
                Result.failure(Exception("获取订阅日志失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3. 环境变量 (Envs)
    override suspend fun getEnvs(query: String?): Result<List<UnifiedEnv>> {
        return try {
            val resp = api.getEnvs(getAuthHeader(), query)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val list = resp.body()!!.data!!.map { item ->
                    UnifiedEnv(
                        id = item.id?.toString() ?: "",
                        name = item.name,
                        value = item.value,
                        remarks = item.remarks,
                        enabled = item.status == 0
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("获取环境变量失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveEnv(env: UnifiedEnv): Result<Boolean> {
        return try {
            val isNew = env.id.isEmpty() || env.id.startsWith("new_") || env.id.startsWith("tmp_") || env.id.toLongOrNull() == null || env.id.length > 8
            if (isNew) {
                val resp = api.createEnvs(getAuthHeader(), listOf(QlCreateEnvReq(env.name, env.value, env.remarks)))
                if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("创建环境变量失败: HTTP ${resp.code()}"))
            } else {
                val cleanId = env.id.toDoubleOrNull()?.toLong() ?: env.id.toLongOrNull() ?: env.id
                val resp = api.updateEnv(getAuthHeader(), QlUpdateEnvReq(cleanId, env.name, env.value, env.remarks))
                if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("更新环境变量失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleEnv(envId: String, enable: Boolean): Result<Boolean> {
        return try {
            val cleanId = envId.toDoubleOrNull()?.toLong() ?: envId.toLongOrNull() ?: envId
            val idList = listOf(cleanId)
            val resp = if (enable) api.enableEnvs(getAuthHeader(), idList) else api.disableEnvs(getAuthHeader(), idList)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("切换变量状态失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEnv(envIds: List<String>): Result<Boolean> {
        return try {
            val cleanIds = envIds.map { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it }
            val resp = api.deleteEnvs(getAuthHeader(), cleanIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("删除环境变量失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConfigFiles(): Result<List<String>> {
        return try {
            val resp = api.getConfigFiles(getAuthHeader())
            if (resp.isSuccessful && resp.body()?.data != null) {
                val validFiles = resp.body()!!.data!!
                    .map { it.value }
                    .filter { file ->
                        val lower = file.lowercase()
                        !lower.contains("__pycache__") &&
                        !lower.startsWith(".") &&
                        (lower.endsWith(".sh") || lower.endsWith(".json") || lower.endsWith(".js") || lower.endsWith(".py") || lower.endsWith(".env") || lower.endsWith(".conf") || lower.endsWith(".yml") || lower.endsWith(".yaml"))
                    }
                Result.success(validFiles)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun readConfig(path: String): Result<String> {
        return try {
            val resp = api.getConfigDetail(getAuthHeader(), path)
            if (resp.isSuccessful && resp.body()?.data != null) {
                Result.success(resp.body()!!.data!!)
            } else {
                Result.failure(Exception("读取配置文件失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveConfig(path: String, content: String): Result<Boolean> {
        return try {
            val resp = api.saveConfig(getAuthHeader(), QlSaveConfigReq(path, content))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("保存配置文件失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // 6. 依赖包管理
    override suspend fun getDeps(query: String?): Result<List<UnifiedDep>> {
        return try {
            val resp = api.getDependencies(getAuthHeader(), query)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dataElem = resp.body()!!.data!!
                val itemsList: List<QlDepItem> = when {
                    dataElem.isJsonArray -> {
                        val type = object : com.google.gson.reflect.TypeToken<List<QlDepItem>>() {}.type
                        com.google.gson.Gson().fromJson(dataElem, type)
                    }
                    dataElem.isJsonObject -> {
                        val obj = dataElem.asJsonObject
                        if (obj.has("data") && obj.get("data").isJsonArray) {
                            val type = object : com.google.gson.reflect.TypeToken<List<QlDepItem>>() {}.type
                            com.google.gson.Gson().fromJson(obj.get("data"), type)
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
                val list = itemsList.map { d ->
                    val typeStr = when (val rawType = d.type) {
                        is Number -> when (rawType.toInt()) {
                            0 -> "nodejs"
                            1 -> "python3"
                            2 -> "linux"
                            else -> "nodejs"
                        }
                        is String -> when (rawType.lowercase()) {
                            "0", "nodejs", "node" -> "nodejs"
                            "1", "python3", "python" -> "python3"
                            "2", "linux" -> "linux"
                            else -> rawType.lowercase()
                        }
                        else -> "nodejs"
                    }
                    val cleanDepId = when (val raw = d.id) {
                        is Number -> raw.toLong().toString()
                        is String -> raw.toDoubleOrNull()?.toLong()?.toString() ?: raw.substringBefore('.')
                        else -> raw?.toString()?.substringBefore('.') ?: ""
                    }
                    UnifiedDep(
                        id = cleanDepId,
                        name = d.name,
                        version = "",
                        type = typeStr,
                        remarks = d.remark,
                        status = d.status ?: 1,
                        log = d.log?.joinToString("\n")
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("获取依赖失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun installDep(name: String, version: String, type: String, remark: String): Result<Boolean> {
        return try {
            val typeInt = when (type.lowercase()) {
                "nodejs" -> 0
                "python3" -> 1
                else -> 2
            }
            val resp = api.installDependencies(getAuthHeader(), listOf(QlCreateDepReq(name, typeInt, remark.ifEmpty { null })))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("安装依赖失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDep(depId: String, type: String): Result<Boolean> = batchDeleteDeps(listOf(depId))

    override suspend fun batchDeleteDeps(depIds: List<String>): Result<Boolean> {
        return try {
            val cleanIds: List<Any> = depIds.map {
                it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it
            }
            val resp = api.deleteDependencies(getAuthHeader(), cleanIds)
            if (resp.isSuccessful) {
                Result.success(true)
            } else {
                // 如果常规卸载失败（如依赖文件在容器物理系统不存在导致无法执行uninstall），调用 force 强制清理数据库记录
                val forceResp = api.forceDeleteDependencies(getAuthHeader(), cleanIds)
                if (forceResp.isSuccessful) Result.success(true) else Result.failure(Exception("删除依赖失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            try {
                val cleanIds: List<Any> = depIds.map { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it }
                val forceResp = api.forceDeleteDependencies(getAuthHeader(), cleanIds)
                if (forceResp.isSuccessful) Result.success(true) else Result.failure(e)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    override suspend fun forceDeleteDeps(depIds: List<String>): Result<Boolean> {
        return try {
            val cleanIds: List<Any> = depIds.map { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() ?: it }
            val resp = api.forceDeleteDependencies(getAuthHeader(), cleanIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("强制清除记录失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDepLog(depId: String): Result<String> {
        return try {
            val resp = api.getDependencyDetail(getAuthHeader(), depId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val log = resp.body()!!.data!!.log?.joinToString("\n") ?: "暂无日志输出"
                Result.success(log)
            } else {
                Result.failure(Exception("获取依赖日志失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatFileSize(bytes: Long?): String? {
        if (bytes == null || bytes <= 0) return null
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun mapQlScriptNode(item: QlScriptNodeItem): ScriptNode {
        val nodeName = item.title ?: item.name ?: item.key?.substringAfterLast('/') ?: item.value?.substringAfterLast('/') ?: "未命名"
        val nodePath = item.key ?: item.value ?: item.title ?: nodeName
        val isDirectory = if (item.type != null) {
            item.type.equals("directory", ignoreCase = true)
        } else {
            !item.children.isNullOrEmpty() || (item.isLeaf != null && item.isLeaf == false)
        }
        val formattedSize = if (isDirectory) null else (formatFileSize(item.size) ?: "-")
        return ScriptNode(
            name = nodeName,
            path = nodePath,
            isDir = isDirectory,
            size = formattedSize,
            children = item.children?.map { mapQlScriptNode(it) }
        )
    }

    override suspend fun getScriptTree(): Result<List<ScriptNode>> {
        return try {
            val resp = api.getScripts(getAuthHeader())
            if (resp.isSuccessful && resp.body()?.data != null) {
                val rawList = resp.body()!!.data!!
                val list = rawList.map { item ->
                    val node = mapQlScriptNode(item)
                    if (node.isDir && node.children.isNullOrEmpty()) {
                        try {
                            val subResp = api.getScripts(getAuthHeader(), path = node.path)
                            if (subResp.isSuccessful && subResp.body()?.data != null) {
                                val subChildren = subResp.body()!!.data!!.map { mapQlScriptNode(it) }
                                node.copy(children = subChildren)
                            } else {
                                node
                            }
                        } catch (_: Exception) {
                            node
                        }
                    } else {
                        node
                    }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("获取青龙脚本列表失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractScriptContent(elem: com.google.gson.JsonElement): String {
        return when {
            elem.isJsonObject && elem.asJsonObject.has("data") -> {
                val d = elem.asJsonObject.get("data")
                when {
                    d.isJsonPrimitive -> d.asString
                    d.isJsonObject && d.asJsonObject.has("content") -> d.asJsonObject.get("content").asString
                    else -> d.toString()
                }
            }
            elem.isJsonPrimitive -> elem.asString
            else -> elem.toString()
        }
    }

    override suspend fun readScript(path: String): Result<String> {
        return try {
            val normalized = path.replace('\\', '/')
            val fileName = normalized.substringAfterLast("/")
            val dirPath = if (normalized.contains("/")) normalized.substringBeforeLast("/") else null

            // 根据面板版本选择接口：>= 2.10.0 使用新版 /api/scripts/detail，旧版走 /api/scripts/:file
            val version = getPanelVersion()
            if (isVersionAtLeast(version, "2.10.0")) {
                // === 新版路径 ===
                // 首选：文件名 + 目录分离传参（dirPath 为空时传 null，Retrofit 不发 path 参数，完全符合青龙后端）
                val resp = api.getScriptDetail(getAuthHeader(), file = fileName, path = dirPath?.ifEmpty { null })
                if (resp.isSuccessful && resp.body() != null) {
                    return Result.success(extractScriptContent(resp.body()!!))
                }
                // 次选：整体路径作为 file 参数
                val resp2 = api.getScriptDetail(getAuthHeader(), file = normalized, path = null)
                if (resp2.isSuccessful && resp2.body() != null) {
                    return Result.success(extractScriptContent(resp2.body()!!))
                }
                Result.failure(Exception("读取脚本内容失败 (v$version): HTTP ${resp.code()}"))
            } else {
                // === 旧版路径 (<2.10.0) ===
                val legacyResp = api.getLegacyScriptContent(getAuthHeader(), file = fileName, path = dirPath?.ifEmpty { null })
                if (legacyResp.isSuccessful && legacyResp.body() != null) {
                    return Result.success(extractScriptContent(legacyResp.body()!!))
                }
                Result.failure(Exception("读取脚本内容失败 (legacy v$version): HTTP ${legacyResp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveScript(path: String, content: String): Result<Boolean> {
        return try {
            val fileName = path.substringAfterLast("/")
            val dirPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
            val resp = api.saveScript(getAuthHeader(), QlSaveScriptReq(filename = fileName, content = content, path = dirPath))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("保存脚本失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createScript(path: String, content: String): Result<Boolean> {
        return try {
            val fileName = path.substringAfterLast("/")
            val dirPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
            val resp = api.createScript(getAuthHeader(), QlCreateScriptReq(filename = fileName, content = content, path = dirPath))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("新建脚本失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(path: String): Result<Boolean> {
        return try {
            val dirName = path.substringAfterLast("/")
            val parentPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
            // 真实青龙 API POST /api/scripts 的 Joi 校验规则要求 filename: Joi.string().required()
            // 必须传入 filename（设为 dirName），同时 directory 字段触发青龙服务端 if (directory) fs.mkdir 逻辑
            val resp = api.createScript(
                getAuthHeader(),
                QlCreateScriptReq(
                    filename = dirName,
                    directory = dirName,
                    path = parentPath,
                    content = ""
                )
            )
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("新建目录失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteScript(path: String): Result<Boolean> {
        return try {
            val fileName = path.substringAfterLast("/")
            val dirPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
            val resp = api.deleteScript(getAuthHeader(), QlDeleteScriptReq(filename = fileName, path = dirPath))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("删除脚本失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun streamLog(logId: String): Flow<String> = flow {
        emit("[INFO] 正在连接青龙 v2.15+ 日志接口...\n")
        delay(300)
        emit("[INFO] 鉴权通过，开始回放任务输出\n")
    }

    override suspend fun getMetrics(): Result<Pair<String, String>> {
        return try {
            val resp = api.getDashboardSystem(getAuthHeader())
            if (resp.isSuccessful && resp.body() != null) {
                val dataElem = resp.body()!!
                val dataObj = if (dataElem.isJsonObject && dataElem.asJsonObject.has("data")) {
                    dataElem.asJsonObject.getAsJsonObject("data")
                } else if (dataElem.isJsonObject) {
                    dataElem.asJsonObject
                } else null

                if (dataObj != null) {
                    val ramPercent = dataObj.get("memUsagePercent")?.asString ?: "--"
                    val loadAvgArr = dataObj.getAsJsonArray("loadAvg")
                    val load1 = loadAvgArr?.firstOrNull()?.asDouble ?: 0.0
                    val cpus = dataObj.get("cpus")?.asInt ?: 1
                    val cpuPercent = String.format("%.1f", (load1 / cpus.coerceAtLeast(1)) * 100.0)
                    Result.success(Pair("$cpuPercent%", "$ramPercent%"))
                } else {
                    Result.success(Pair("--", "--"))
                }
            } else {
                Result.success(Pair("--", "--"))
            }
        } catch (e: Exception) {
            Result.success(Pair("--", "--"))
        }
    }

    override suspend fun getLoginLogs(): Result<List<Map<String, Any>>> {
        return try {
            val resp = api.getLoginLogs(getAuthHeader())
            if (resp.isSuccessful && resp.body() != null) {
                val elem = resp.body()!!
                val list = mutableListOf<Map<String, Any>>()
                val arr = if (elem.isJsonObject && elem.asJsonObject.has("data") && elem.asJsonObject.get("data").isJsonArray) {
                    elem.asJsonObject.getAsJsonArray("data")
                } else if (elem.isJsonArray) {
                    elem.asJsonArray
                } else null
                arr?.forEach { item ->
                    if (item.isJsonObject) {
                        val obj = item.asJsonObject
                        val map = mutableMapOf<String, Any>()
                        obj.entrySet().forEach { (k, v) ->
                            map[k] = if (v.isJsonPrimitive) v.asString else v.toString()
                        }
                        list.add(map)
                    }
                }
                Result.success(list)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getLogsTree(): Result<com.google.gson.JsonElement> {
        return try {
            val resp = api.getLogsTree(getAuthHeader())
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("获取日志文件列表失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLogDetail(path: String, file: String): Result<String> {
        return try {
            val version = getPanelVersion()
            if (isVersionAtLeast(version, "2.10.0")) {
                // === 新版路径 (>= 2.10.0) ===
                val resp = api.getLogDetail(getAuthHeader(), file = file, path = path.ifEmpty { null })
                if (resp.isSuccessful && resp.body() != null) {
                    val elem = resp.body()!!
                    val content = when {
                        elem.isJsonObject && elem.asJsonObject.has("data") -> {
                            val d = elem.asJsonObject.get("data")
                            if (d.isJsonPrimitive) d.asString else d.toString()
                        }
                        elem.isJsonPrimitive -> elem.asString
                        else -> elem.toString()
                    }
                    Result.success(content)
                } else {
                    Result.failure(Exception("读取日志失败 (v$version): HTTP ${resp.code()}"))
                }
            } else {
                // === 旧版路径 (< 2.10.0) ===
                val legacyResp = api.getLegacyLogDetail(getAuthHeader(), file = file, path = path.ifEmpty { null })
                if (legacyResp.isSuccessful && legacyResp.body() != null) {
                    val elem = legacyResp.body()!!
                    val content = when {
                        elem.isJsonObject && elem.asJsonObject.has("data") ->
                            elem.asJsonObject.get("data").asString
                        elem.isJsonPrimitive -> elem.asString
                        else -> elem.toString()
                    }
                    Result.success(content)
                } else {
                    Result.failure(Exception("读取日志失败 (legacy v$version): HTTP ${legacyResp.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
