package com.panel.app.data.adapter

import com.panel.app.data.backup.*
import com.panel.app.data.model.*
import com.panel.app.data.remote.NetworkClient
import com.panel.app.data.remote.api.*
import com.panel.app.data.remote.unwrap
import com.panel.app.data.remote.unwrapTo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * 青龙面板 2.15+ 适配器。
 *
 * 鉴权：`Authorization: Bearer <token>`（官方前端 `src/utils/http.tsx` 的拦截器就是这么写的）。
 *
 * 错误处理：青龙的业务错误用 HTTP 200 + `{"code":4xx,"message":"..."}` 返回，
 * 所有响应都要经 [unwrap] 解包。
 *
 * 契约来源：`qinglong/back/api` 目录下的路由与 celebrate(Joi) 校验规则。
 */
class QinglongV15Adapter(
    override val instance: PanelInstance
) : IPanelAdapter {

    private val api: QinglongV15Api = NetworkClient.buildRetrofit(instance.baseUrl).create(QinglongV15Api::class.java)
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

    private fun toId(id: String): Long? = id.toLongOrNull() ?: id.toDoubleOrNull()?.toLong()

    private fun cleanId(raw: Any?): String = when (raw) {
        is Number -> raw.toLong().toString()
        is String -> raw.toDoubleOrNull()?.toLong()?.toString() ?: raw.substringBefore('.')
        else -> raw?.toString()?.substringBefore('.') ?: ""
    }

    // ---------------------------------------------------------------- 认证

    override suspend fun authenticate(): Result<String> {
        val saved = instance.token
        if (!saved.isNullOrEmpty()) {
            currentToken = saved
            return Result.success(saved)
        }

        val user = instance.username
        val pass = instance.password
        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            val resp = try {
                api.login(mapOf("username" to user, "password" to pass))
            } catch (e: Exception) {
                return Result.failure(Exception("连接异常: ${e.message ?: "无法连接"}"))
            }
            val envelope = resp.unwrap("登录失败").getOrElse { return Result.failure(it) }
            val token = envelope.data?.token
                ?: return Result.failure(Exception("登录失败: 服务端未返回 token"))
            currentToken = token
            return Result.success(token)
        }

        return Result.failure(Exception("请输入面板登录账号与密码"))
    }

    /** POST /api/user/logout，面板侧吊销该 token */
    override suspend fun logout(): Result<Boolean> {
        val token = currentToken ?: instance.token
        if (token.isNullOrEmpty()) return Result.success(true)
        return api.logout(getAuthHeader()).unwrap("退出登录失败").map { true }
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

    /** /crons 可能返回数组，也可能返回 {data:[...], total} */
    private fun parseCronArray(data: com.google.gson.JsonElement?): List<QlCronItem> {
        if (data == null) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<QlCronItem>>() {}.type
        return when {
            data.isJsonArray -> com.google.gson.Gson().fromJson(data, type) ?: emptyList()
            data.isJsonObject && data.asJsonObject.get("data")?.isJsonArray == true ->
                com.google.gson.Gson().fromJson(data.asJsonObject.get("data"), type) ?: emptyList()
            else -> emptyList()
        }
    }

    private fun QlCronItem.toUnifiedTask(): UnifiedTask {
        // CrontabStatus (back/data/cron.ts): 0=running, 1=idle, 2=disabled, 3=queued
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

    override suspend fun createTask(name: String, command: String, schedule: String): Result<Boolean> {
        ensureAuth()
        return api.createCron(getAuthHeader(), QlCreateCronReq(name = name, command = command, schedule = schedule))
            .unwrap("创建任务失败").map { true }
    }

    override suspend fun updateTask(task: UnifiedTask): Result<Boolean> {
        ensureAuth()
        val id: Any = toId(task.id) ?: task.id
        return api.updateCron(
            getAuthHeader(),
            QlUpdateCronReq(id = id, name = task.name, command = task.command, schedule = task.schedule)
        ).unwrap("更新任务失败").map { true }
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

    /** 停止指定的运行实例（比 stopCrons 更精确，可只停掉某一个并发实例） */
    suspend fun stopCronInstance(cronId: String, instanceId: String): Result<Boolean> {
        ensureAuth()
        return api.stopCronInstance(getAuthHeader(), cronId, instanceId)
            .unwrap("停止运行实例失败").map { true }
    }

    override suspend fun toggleTask(taskId: String, enable: Boolean): Result<Boolean> {
        ensureAuth()
        val id = toId(taskId) ?: return Result.failure(Exception("任务 ID 无效"))
        return (if (enable) api.enableCrons(getAuthHeader(), listOf(id))
        else api.disableCrons(getAuthHeader(), listOf(id)))
            .unwrap("切换任务状态失败").map { true }
    }

    override suspend fun deleteTask(taskIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(taskIds)
        if (ids.isEmpty()) return Result.failure(Exception("任务 ID 列表为空或无效"))
        return api.deleteCrons(getAuthHeader(), ids).unwrap("删除任务失败").map { true }
    }

    override suspend fun pinTask(taskIds: List<String>, pin: Boolean): Result<Boolean> {
        ensureAuth()
        val ids = toIds(taskIds)
        if (ids.isEmpty()) return Result.failure(Exception("任务 ID 无效"))
        return (if (pin) api.pinCrons(getAuthHeader(), ids) else api.unpinCrons(getAuthHeader(), ids))
            .unwrap("置顶操作失败").map { true }
    }

    /** 批量打标签 / 去标签 */
    suspend fun addTaskLabels(taskIds: List<String>, labels: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(taskIds)
        if (ids.isEmpty() || labels.isEmpty()) return Result.failure(Exception("任务 ID 或标签为空"))
        return api.addCronLabels(getAuthHeader(), QlLabelBatchReq(ids, labels))
            .unwrap("添加标签失败").map { true }
    }

    suspend fun removeTaskLabels(taskIds: List<String>, labels: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(taskIds)
        if (ids.isEmpty() || labels.isEmpty()) return Result.failure(Exception("任务 ID 或标签为空"))
        return api.removeCronLabels(getAuthHeader(), QlLabelBatchReq(ids, labels))
            .unwrap("移除标签失败").map { true }
    }

    override suspend fun getTaskInstances(taskId: String): Result<List<TaskInstanceRecord>> {
        ensureAuth()
        val cronId = toId(taskId)?.toString() ?: taskId.substringBefore('.')
        // SimpleDateFormat 每次局部新建，且整体 try/catch：
        // 历史日志数组的字段（filename/time）若为 JsonNull，asString/asLong 会抛异常，
        // 不收敛的话会经 viewModelScope 冒到主线程导致闪退
        return try {
            loadTaskInstancesInternal(cronId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun loadTaskInstancesInternal(cronId: String): Result<List<TaskInstanceRecord>> {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        val fromInstances = runCatching {
            api.getCronInstances(getAuthHeader(), cronId)
                .unwrap("获取运行实例失败").getOrNull()
                ?.data.orEmpty()
                .map { inst ->
                    TaskInstanceRecord(
                        id = cleanId(inst.id),
                        startTime = inst.started_at?.takeIf { it > 0 }
                            ?.let { sdf.format(java.util.Date(it * 1000)) }
                            ?: inst.created_at ?: "--",
                        endTime = inst.finished_at?.takeIf { it > 0 }
                            ?.let { sdf.format(java.util.Date(it * 1000)) }
                            ?: inst.updated_at,
                        duration = when {
                            inst.finished_at != null && inst.started_at != null && inst.finished_at >= inst.started_at ->
                                formatSeconds(inst.finished_at - inst.started_at)
                            inst.duration != null -> formatSeconds(inst.duration)
                            else -> "--"
                        },
                        exitCode = inst.exit_code ?: if (inst.status == 1) 0 else 1,
                        // InstanceStatus: 0=running, 1=success, 2=stopped, 3=failed
                        statusText = when (inst.status) {
                            0 -> "运行中"
                            1 -> "成功"
                            2 -> "已停止"
                            3 -> "失败"
                            else -> "完成"
                        },
                        logPath = inst.log_path
                    )
                }
        }.getOrNull().orEmpty()

        if (fromInstances.isNotEmpty()) return Result.success(fromInstances)

        // 旧版本没有 running_instance 表，退回历史日志文件列表
        val history = runCatching {
            api.getCronHistoryLogs(getAuthHeader(), cronId)
                .unwrap("获取历史日志失败").getOrNull()?.data
                ?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { elem ->
                    if (!elem.isJsonObject) return@mapNotNull null
                    val obj = elem.asJsonObject
                    val filename = obj.strOrNull("filename") ?: return@mapNotNull null
                    val directory = obj.strOrNull("directory").orEmpty()
                    val time = obj.longOrNull("time") ?: 0L
                    TaskInstanceRecord(
                        id = filename,
                        startTime = if (time > 0) sdf.format(java.util.Date(time)) else filename.removeSuffix(".log"),
                        endTime = null,
                        duration = "--",
                        exitCode = 0,
                        statusText = "已完成",
                        logPath = if (directory.isNotEmpty()) "$directory/$filename" else filename
                    )
                }
        }.getOrNull()

        return Result.success(history ?: emptyList())
    }

    override suspend fun getTaskLog(taskNameOrId: String): Result<String> {
        ensureAuth()
        val cronId = toId(taskNameOrId)?.toString() ?: taskNameOrId.substringBefore('.')
        // tail 语义：读末尾内容（后端不传 offset/limit 时默认也是这个行为），
        // 并显式取到 truncate 标记，避免用户误以为看到的是完整日志
        return api.getCronLog(getAuthHeader(), cronId, tail = true)
            .unwrapTo("获取任务日志失败") { formatLogChunk(it) }
    }

    /**
     * 把分页日志块拼上必要的提示。
     * 后端单次最多返回 1MB，truncated=true 时必须告知用户，否则会静默丢内容。
     */
    private fun formatLogChunk(chunk: QlLogChunkResp): String {
        val body = chunk.data.orEmpty().ifBlank { "暂无运行日志输出" }
        if (chunk.truncated != true) return body
        val total = chunk.total?.let { "（共 ${formatBytes(it)}）" } ?: ""
        return "⚠ 日志过大，仅显示末尾部分$total。\n\n$body"
    }

    // ---------------------------------------------------------------- 2. 订阅

    override suspend fun getSubscriptions(query: String?): Result<List<UnifiedSubscription>> {
        ensureAuth()
        return api.getSubscriptions(getAuthHeader(), query)
            .unwrapTo("获取订阅失败") { env -> parseSubscriptionArray(env.data).map { it.toUnified() } }
    }

    private fun parseSubscriptionArray(data: com.google.gson.JsonElement?): List<QlSubscriptionItem> {
        if (data == null) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<QlSubscriptionItem>>() {}.type
        return when {
            data.isJsonArray -> com.google.gson.Gson().fromJson(data, type) ?: emptyList()
            data.isJsonObject && data.asJsonObject.get("data")?.isJsonArray == true ->
                com.google.gson.Gson().fromJson(data.asJsonObject.get("data"), type) ?: emptyList()
            else -> emptyList()
        }
    }

    private fun QlSubscriptionItem.toUnified(): UnifiedSubscription {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return UnifiedSubscription(
            id = cleanId(id),
            name = name ?: "未命名订阅",
            type = type ?: "public-repo",
            url = url.orEmpty(),
            branch = branch ?: "main",
            schedule = schedule ?: "0 0 * * *",
            whitelist = whitelist.orEmpty(),
            blacklist = blacklist.orEmpty(),
            dependences = dependences.orEmpty(),
            extensions = extensions.orEmpty(),
            alias = alias.orEmpty(),
            autoAddCron = autoAddCron == true || autoAddCron == 1,
            autoDelCron = autoDelCron == true || autoDelCron == 1,
            // SubscriptionStatus: 0=running, 1=idle, 2=disabled, 3=queued
            statusText = when (status) {
                0 -> "同步中"
                3 -> "排队中"
                2 -> "已禁用"
                else -> "就绪"
            },
            isRunning = status == 0,
            isDisabled = status == 2,
            lastRunTime = last_execution_time?.takeIf { it > 0 }
                ?.let { sdf.format(java.util.Date(it * 1000)) }
        )
    }

    override suspend fun createSubscription(sub: UnifiedSubscription): Result<Boolean> {
        ensureAuth()
        return api.createSubscription(
            getAuthHeader(),
            QlCreateSubscriptionReq(
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
        ).unwrap("创建订阅失败").map { true }
    }

    override suspend fun updateSubscription(sub: UnifiedSubscription): Result<Boolean> {
        ensureAuth()
        val id: Any = toId(sub.id) ?: sub.id
        return api.updateSubscription(
            getAuthHeader(),
            QlUpdateSubscriptionReq(
                id = id,
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
        ).unwrap("更新订阅失败").map { true }
    }

    override suspend fun deleteSubscription(subIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(subIds)
        if (ids.isEmpty()) return Result.failure(Exception("订阅 ID 无效"))
        return api.deleteSubscriptions(getAuthHeader(), ids).unwrap("删除订阅失败").map { true }
    }

    override suspend fun runSubscription(subIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(subIds)
        if (ids.isEmpty()) return Result.failure(Exception("订阅 ID 无效"))
        return api.runSubscriptions(getAuthHeader(), ids).unwrap("运行订阅失败").map { true }
    }

    override suspend fun stopSubscription(subIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(subIds)
        if (ids.isEmpty()) return Result.failure(Exception("订阅 ID 无效"))
        return api.stopSubscriptions(getAuthHeader(), ids).unwrap("停止订阅失败").map { true }
    }

    override suspend fun getSubscriptionLog(subId: String): Result<String> {
        ensureAuth()
        return api.getSubscriptionLog(getAuthHeader(), subId, tail = true)
            .unwrapTo("获取订阅日志失败") { formatLogChunk(it) }
    }

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
        if (env.name.isBlank()) {
            return Result.failure(Exception("变量名不能为空"))
        }
        val isNew = env.id.isEmpty() || toId(env.id) == null
        return (if (isNew) {
            api.createEnvs(getAuthHeader(), listOf(QlCreateEnvReq(env.name.trim(), env.value, env.remarks ?: "")))
                .unwrap("创建环境变量失败")
        } else {
            val id: Any = toId(env.id) ?: env.id
            api.updateEnv(getAuthHeader(), QlUpdateEnvReq(id, env.name.trim(), env.value, env.remarks ?: ""))
                .unwrap("更新环境变量失败")
        }).map { true }
    }

    override suspend fun toggleEnv(envId: String, enable: Boolean): Result<Boolean> {
        ensureAuth()
        val id = toId(envId) ?: return Result.failure(Exception("环境变量 ID 无效"))
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

    /**
     * 高保真恢复任务：创建时带 labels；第二轮按名称回查 ID，
     * 用批量接口恢复 isDisabled / isPinned。
     */
    override suspend fun restoreTasks(tasks: List<BackupTask>): Result<RestoreReport> {
        ensureAuth()
        val errors = mutableListOf<String>()
        var ok = 0
        var skipped = 0
        val wantDisabled = mutableListOf<String>()
        val wantPinned = mutableListOf<String>()

        for (t in tasks) {
            if (t.name.isBlank() || t.command.isBlank() || t.schedule.isBlank()) {
                skipped++
                continue
            }
            val res = api.createCron(
                getAuthHeader(),
                QlCreateCronReq(name = t.name, command = t.command, schedule = t.schedule, labels = t.labels)
            ).unwrap("创建任务失败")
            if (res.isFailure) {
                val errMsg = res.exceptionOrNull()?.message ?: ""
                // 任务已存在时跳过，而不是报错
                if ("已存在".contains(errMsg) || "exist".equals(errMsg, ignoreCase = true) ||
                    "duplicate".equals(errMsg, ignoreCase = true) || errMsg.contains("重复")) {
                    skipped++
                    continue
                }
                errors.add("${t.name}: ${res.exceptionOrNull()?.message}")
                continue
            }
            ok++
            if (t.isDisabled) wantDisabled.add(t.name)
            if (t.isPinned) wantPinned.add(t.name)
        }

        if (wantDisabled.isNotEmpty() || wantPinned.isNotEmpty()) {
            val current = api.getCrons(getAuthHeader()).unwrap("回查任务列表失败").getOrNull()?.data
                ?.let { parseCronArray(it) }.orEmpty()
            val byName = current.filter { it.name != null }.groupBy { it.name!! }
            fun idsOf(names: List<String>): List<Long> =
                names.mapNotNull { n -> byName[n]?.firstOrNull()?.id?.let { cleanId(it).toLongOrNull() } }
            val disabledIds = idsOf(wantDisabled)
            val pinnedIds = idsOf(wantPinned)
            if (disabledIds.isNotEmpty()) api.disableCrons(getAuthHeader(), disabledIds).unwrap("恢复禁用状态失败")
            if (pinnedIds.isNotEmpty()) api.pinCrons(getAuthHeader(), pinnedIds).unwrap("恢复置顶状态失败")
        }

        return Result.success(RestoreReport("任务", tasks.size, ok, skipped, errors))
    }

    /** 高保真恢复环境变量：一次 POST 数组，第二轮批量恢复禁用状态。重复变量自动跳过 */
    override suspend fun restoreEnvs(envs: List<BackupEnv>): Result<RestoreReport> {
        ensureAuth()
        val valid = envs.filter { it.name.isNotBlank() }
        val skipped = envs.size - valid.size
        if (valid.isEmpty()) return Result.success(RestoreReport("环境变量", envs.size, 0, skipped, emptyList()))

        val invalid = valid.filter { !ENV_NAME_REGEX.matches(it.name) }
        if (invalid.isNotEmpty()) {
            return Result.success(
                RestoreReport(
                    "环境变量", envs.size, 0, skipped + invalid.size,
                    listOf("以下变量名不合法：${invalid.joinToString("、") { it.name }}")
                )
            )
        }

        val created = api.createEnvs(getAuthHeader(), valid.map { QlCreateEnvReq(it.name, it.value, it.remarks) })
            .unwrap("导入环境变量失败")
        if (created.isFailure) {
            val errMsg = created.exceptionOrNull()?.message ?: ""
            // 部分变量已存在时，改用逐条创建模式
            if ("已存在".contains(errMsg) || "exist".equals(errMsg, ignoreCase = true) ||
                "duplicate".equals(errMsg, ignoreCase = true) || errMsg.contains("重复")) {
                var ok = 0
                var dupSkipped = 0
                val errors = mutableListOf<String>()
                for (e in valid) {
                    val res = api.createEnvs(getAuthHeader(), listOf(QlCreateEnvReq(e.name, e.value, e.remarks)))
                        .unwrap("导入环境变量失败")
                    if (res.isSuccess) {
                        ok++
                    } else {
                        val eMsg = res.exceptionOrNull()?.message ?: ""
                        if ("已存在".contains(eMsg) || "exist".equals(eMsg, ignoreCase = true) ||
                            "duplicate".equals(eMsg, ignoreCase = true) || eMsg.contains("重复")) {
                            dupSkipped++
                        } else {
                            errors.add("${e.name}: ${res.exceptionOrNull()?.message}")
                        }
                    }
                }
                // 恢复禁用状态
                val wantDisabled = valid.filter { !it.enabled }.map { it.name }
                if (wantDisabled.isNotEmpty()) {
                    val current = api.getEnvs(getAuthHeader()).unwrap("回查变量列表失败").getOrNull()?.data.orEmpty()
                    val ids = current.filter { wantDisabled.contains(it.name) }.mapNotNull { cleanId(it.id).toLongOrNull() }
                    if (ids.isNotEmpty()) api.disableEnvs(getAuthHeader(), ids).unwrap("恢复禁用状态失败")
                }
                return Result.success(RestoreReport("环境变量", envs.size, ok, skipped + dupSkipped, errors))
            }
            return Result.failure(created.exceptionOrNull() ?: Exception("导入失败"))
        }

        val wantDisabled = valid.filter { !it.enabled }.map { it.name }
        if (wantDisabled.isNotEmpty()) {
            val current = api.getEnvs(getAuthHeader()).unwrap("回查变量列表失败").getOrNull()?.data.orEmpty()
            val ids = current.filter { wantDisabled.contains(it.name) }.mapNotNull { cleanId(it.id).toLongOrNull() }
            if (ids.isNotEmpty()) api.disableEnvs(getAuthHeader(), ids).unwrap("恢复禁用状态失败")
        }

        return Result.success(RestoreReport("环境变量", envs.size, valid.size, skipped, emptyList()))
    }

    /**
     * 批量导入：POST /api/envs 本身就接收数组，一次请求搞定。
     * 提前校验变量名，避免拿到一个逐条都失败的模糊报错。
     */
    override suspend fun importEnvs(envs: List<UnifiedEnv>): Result<Int> {
        ensureAuth()
        if (envs.isEmpty()) return Result.failure(Exception("没有可导入的环境变量"))
        val invalid = envs.filter { !ENV_NAME_REGEX.matches(it.name) }
        if (invalid.isNotEmpty()) {
            return Result.failure(
                Exception(
                    "以下变量名不合法（仅限字母/数字/下划线，且不能以数字开头）：${invalid.joinToString("、") { it.name }}"
                )
            )
        }
        return api.createEnvs(getAuthHeader(), envs.map { QlCreateEnvReq(it.name, it.value, it.remarks) })
            .unwrap("导入环境变量失败").map { envs.size }
    }

    // ---------------------------------------------------------------- 4. 配置文件

    override suspend fun getConfigFiles(): Result<List<String>> {
        ensureAuth()
        // 后端已经排除了黑名单文件，这里不再用后缀二次过滤，
        // 否则 .txt / 无扩展名等合法配置文件会被静默隐藏
        return api.getConfigFiles(getAuthHeader())
            .unwrapTo("获取配置文件列表失败") { env ->
                env.data.orEmpty().map { it.value }
            }
    }

    override suspend fun readConfig(path: String): Result<String> {
        ensureAuth()
        return api.getConfigDetail(getAuthHeader(), path)
            .unwrapTo("读取配置文件失败") { it.data ?: "" }
    }

    override suspend fun saveConfig(path: String, content: String): Result<Boolean> {
        ensureAuth()
        return api.saveConfig(getAuthHeader(), QlSaveConfigReq(path, content))
            .unwrap("保存配置文件失败").map { true }
    }

    // ---------------------------------------------------------------- 5. 脚本

    private fun mapScriptNode(item: QlScriptNodeItem): ScriptNode {
        val nodeName = item.title ?: item.name ?: item.key?.substringAfterLast('/')
            ?: item.value?.substringAfterLast('/') ?: "未命名"
        val nodePath = item.key ?: item.value ?: item.title ?: nodeName
        val isDirectory = item.type?.equals("directory", ignoreCase = true)
            ?: (!item.children.isNullOrEmpty() || item.isLeaf == false)
        return ScriptNode(
            name = nodeName,
            path = nodePath,
            isDir = isDirectory,
            size = if (isDirectory) null else formatBytes(item.size),
            mtime = item.mtime,
            children = item.children?.map { mapScriptNode(it) }
        )
    }

    override suspend fun getScriptTree(): Result<List<ScriptNode>> {
        ensureAuth()
        return api.getScripts(getAuthHeader())
            .unwrapTo("获取青龙脚本列表失败") { env ->
                env.data.orEmpty().map { node ->
                    // 顶层目录不会带 children，需要按 path 再拉一层
                    val mapped = mapScriptNode(node)
                    if (mapped.isDir && mapped.children.isNullOrEmpty()) {
                        runCatching {
                            api.getScripts(getAuthHeader(), path = mapped.path)
                                .unwrap("获取脚本目录失败").getOrNull()?.data
                                ?.map { mapScriptNode(it) }
                        }.getOrNull()?.let { mapped.copy(children = it) } ?: mapped
                    } else mapped
                }
            }
    }

    override suspend fun readScript(path: String): Result<String> {
        ensureAuth()
        val normalized = path.replace('\\', '/')
        val fileName = normalized.substringAfterLast("/")
        val dirPath = normalized.substringBeforeLast("/").takeIf { normalized.contains("/") }

        // /scripts/{file} 在新版已返回 410 下线，只走 /scripts/detail
        val primary = api.getScriptDetail(getAuthHeader(), file = fileName, path = dirPath)
        if (primary.isSuccessful && primary.body()?.code.let { it == null || it == 200 }) {
            return primary.unwrapTo("读取脚本内容失败") { it.data ?: "" }
        }
        // 目录信息可能已包含在 path 里，再用完整路径试一次
        return api.getScriptDetail(getAuthHeader(), file = normalized, path = null)
            .unwrapTo("读取脚本内容失败") { it.data ?: "" }
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
        // 后端 POST /scripts 校验要求 filename 必填，directory 字段才会触发 mkdir 逻辑
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

    /**
     * 重命名脚本/目录：PUT /api/scripts/rename
     * 后端校验禁止跨目录，newPath 必须与原路径同目录。
     */
    override suspend fun renameScript(path: String, newPath: String): Result<Boolean> {
        ensureAuth()
        val fileName = path.substringAfterLast("/")
        val dirPath = if (path.contains("/")) path.substringBeforeLast("/") else ""
        val newFileName = newPath.substringAfterLast("/")
        if (newPath.substringBeforeLast("/").takeIf { newPath.contains("/") } != dirPath) {
            return Result.failure(Exception("重命名不允许跨目录，请先移动"))
        }
        return api.renameScript(getAuthHeader(), QlRenameScriptReq(fileName, dirPath, newFileName))
            .unwrap("重命名失败").map { true }
    }

    /** 调试运行脚本 */
    suspend fun runScript(path: String, content: String?): Result<Boolean> {
        ensureAuth()
        val fileName = path.substringAfterLast("/")
        val dirPath = path.substringBeforeLast("/").takeIf { path.contains("/") }
        return api.runScript(getAuthHeader(), QlRunScriptReq(fileName, content, dirPath))
            .unwrap("运行脚本失败").map { true }
    }

    // ---------------------------------------------------------------- 6. 依赖

    override suspend fun getDeps(query: String?): Result<List<UnifiedDep>> {
        ensureAuth()
        return api.getDependencies(getAuthHeader(), query)
            .unwrapTo("获取依赖失败") { env -> parseDepArray(env.data).map { it.toUnifiedDep() } }
    }

    private fun parseDepArray(data: com.google.gson.JsonElement?): List<QlDepItem> {
        if (data == null) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<QlDepItem>>() {}.type
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
        // DependenceStatus: 0=installing,1=installed,2=installFailed,3=removing,4=removed,5=removeFailed,6=queued,7=cancelled
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
        if (ids.isEmpty()) return Result.failure(Exception("依赖 ID 列表为空或无效"))
        val normal = api.deleteDependencies(getAuthHeader(), ids).unwrap("删除依赖失败")
        if (normal.isSuccess) return Result.success(true)
        // 普通删除失败时再试强制删除（记录残留场景）
        return api.forceDeleteDependencies(getAuthHeader(), ids)
            .unwrap("删除依赖失败").map { true }
    }

    override suspend fun forceDeleteDeps(depIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(depIds)
        if (ids.isEmpty()) return Result.failure(Exception("依赖 ID 列表为空或无效"))
        return api.forceDeleteDependencies(getAuthHeader(), ids)
            .unwrap("强制清除记录失败").map { true }
    }

    /** 重新安装依赖：PUT /api/dependencies/reinstall */
    override suspend fun reinstallDeps(depIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(depIds)
        if (ids.isEmpty()) return Result.failure(Exception("依赖 ID 无效"))
        return api.reinstallDependencies(getAuthHeader(), ids)
            .unwrap("重新安装依赖失败").map { true }
    }

    /** 取消正在进行的安装：PUT /api/dependencies/cancel */
    override suspend fun cancelDeps(depIds: List<String>): Result<Boolean> {
        ensureAuth()
        val ids = toIds(depIds)
        if (ids.isEmpty()) return Result.failure(Exception("依赖 ID 无效"))
        return api.cancelDependencies(getAuthHeader(), ids)
            .unwrap("取消安装失败").map { true }
    }

    override suspend fun getDepLog(depId: String): Result<String> {
        ensureAuth()
        return api.getDependencyDetail(getAuthHeader(), depId)
            .unwrapTo("获取依赖日志失败") { env ->
                env.data?.log?.joinToString("\n")?.takeIf { it.isNotBlank() }
                    ?: "暂无日志输出"
            }
    }

    // ---------------------------------------------------------------- 7. 日志流

    /** 轮询任务日志，任务结束后自动停止 */
    override fun streamLog(logId: String): Flow<String> = flow {
        var last = ""
        while (currentCoroutineContext().isActive) {
            val res = api.getCronLog(getAuthHeader(), logId, tail = true).unwrap("读取日志失败")
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

    // ---------------------------------------------------------------- 8. 监控

    /**
     * 这里必须整体 try/catch + 空安全取值。
     *
     * 白虎的 [BaihuPanelAdapter.getMetrics] 全程包在 try/catch 里，
     * 而这里原先既没包 try/catch，又直接对 `loadAvg` 数组元素调 `asDouble()` ——
     * 元素若是 JsonNull（青龙部分版本/降级响应会出现）会抛
     * UnsupportedOperationException，**不是 IOException**，
     * OkHttp 的 AsyncCall 会先回调 onFailure 再把它重抛到调度线程 → 进程崩溃。
     * 这是"青龙崩、白虎不崩"的典型差异点之一。
     */
    override suspend fun getMetrics(): Result<Pair<String, String>> {
        ensureAuth()
        return try {
            api.getDashboardSystem(getAuthHeader())
                .unwrapTo("获取监控数据失败") { env ->
                    val obj = env.data?.takeIf { it.isJsonObject }?.asJsonObject
                    if (obj == null) {
                        "--" to "--"
                    } else {
                        val ram = obj.strOrNull("memUsagePercent") ?: "--"
                        val load1 = obj.arrayFirstDouble("loadAvg") ?: 0.0
                        val cpus = obj.intOrNull("cpus") ?: 1
                        val cpu = String.format(java.util.Locale.US, "%.1f%%", (load1 / cpus.coerceAtLeast(1)) * 100.0)
                        cpu to ram
                    }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 仪表盘运行态：包含运行中的实例（instanceId + logPath）、排队任务、闲置任务。
     * 这是"停止指定实例"和"查看实时日志"的数据来源。
     */
    suspend fun getRuntimeOverview(): Result<com.google.gson.JsonElement?> {
        ensureAuth()
        return api.getDashboardRuntime(getAuthHeader())
            .unwrapTo("获取运行态失败") { it.data }
    }

    /** GET /api/dashboard/runtime 的 running[] 直接带 instanceId 与 logPath */
    override suspend fun getRunningTasks(): Result<List<RunningTaskInfo>> {
        ensureAuth()
        return try {
            api.getDashboardRuntime(getAuthHeader())
                .unwrapTo("获取运行中任务失败") { env ->
                    val obj = env.data?.takeIf { it.isJsonObject }?.asJsonObject
                    val running = obj?.get("running")?.takeIf { it.isJsonArray }?.asJsonArray
                    running?.mapNotNull { elem ->
                        if (!elem.isJsonObject) return@mapNotNull null
                        val item = elem.asJsonObject
                        val taskId = item.strOrNull("id")
                            ?: item.longOrNull("id")?.toString()
                            ?: return@mapNotNull null
                        RunningTaskInfo(
                            taskId = taskId,
                            name = item.strOrNull("name") ?: "任务 #$taskId",
                            instanceId = item.strOrNull("instanceId")
                                ?: item.longOrNull("instanceId")?.toString(),
                            pid = item.intOrNull("pid"),
                            elapsedSeconds = item.longOrNull("elapsed"),
                            logPath = item.strOrNull("logPath")
                        )
                    } ?: emptyList()
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 青龙支持按实例精确停止：POST /api/crons/{id}/instances/{instanceId}/stop */
    override suspend fun stopRunningTask(taskId: String, instanceId: String?): Result<Boolean> {
        ensureAuth()
        val instance = instanceId ?: return stopTask(listOf(taskId))
        val cronId = toId(taskId)?.toString() ?: taskId.substringBefore('.')
        return api.stopCronInstance(getAuthHeader(), cronId, instance)
            .unwrap("停止运行实例失败").map { true }
    }

    // ---------------------------------------------------------------- 9. 审计日志

    override suspend fun getLoginLogs(): Result<List<Map<String, Any>>> {
        ensureAuth()
        return api.getLoginLogs(getAuthHeader())
            .unwrapTo("获取登录日志失败") { env ->
                parseJsonArray(env.data).mapNotNull { elem ->
                    if (!elem.isJsonObject) return@mapNotNull null
                    elem.asJsonObject.entrySet().associate { (k, v) ->
                        k to (if (v.isJsonPrimitive) v.asString else v.toString())
                    }
                }
            }
    }

    /**
     * data 为 JsonNull 时必须降级成空数组。
     * 原先 `it.data ?: JsonArray()` 拦不住 JsonNull（它不是 null，是个对象实例），
     * 返回 JsonNull 后 UI 侧再调 `asJsonArray` 就会抛 UnsupportedOperationException 直接崩。
     */
    override suspend fun getLogsTree(): Result<com.google.gson.JsonElement> {
        ensureAuth()
        return try {
            api.getLogsTree(getAuthHeader())
                .unwrapTo("获取日志文件列表失败") { env ->
                    env.data?.takeIf { it.isJsonArray || it.isJsonObject } ?: com.google.gson.JsonArray()
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLogDetail(path: String, file: String): Result<String> {
        ensureAuth()
        return api.getLogDetail(
            auth = getAuthHeader(),
            file = file,
            path = path.ifEmpty { null },
            tail = true
        ).unwrapTo("读取日志失败") { formatLogChunk(it) }
    }

    // ---------------------------------------------------------------- 9.5 仪表盘

    /**
     * 聚合仪表盘数据。
     * 青龙的 overview/trend/top/labels 都是独立接口，这里并行拉取后合并；
     * 单个接口失败只丢掉那块数据，不让整页变空白。
     */
    override suspend fun getDashboard(): Result<PanelDashboard> = coroutineScope {
        ensureAuth()
        try {
                val overviewDef = async {
                    runCatching {
                        api.getDashboardOverview(getAuthHeader()).unwrap("获取概览失败").getOrNull()?.data
                    }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                }
                val trendDef = async {
                    runCatching {
                        api.getDashboardTrend(getAuthHeader(), days = 14).unwrap("获取趋势失败").getOrNull()?.data
                    }.getOrNull()
                }
                val topCountDef = async {
                    runCatching {
                        api.getDashboardTopCount(getAuthHeader()).unwrap("获取次数排行失败").getOrNull()?.data
                    }.getOrNull()
                }
                val topTimeDef = async {
                    runCatching {
                        api.getDashboardTopTime(getAuthHeader()).unwrap("获取耗时排行失败").getOrNull()?.data
                    }.getOrNull()
                }
                val labelsDef = async {
                    runCatching {
                        api.getDashboardLabels(getAuthHeader()).unwrap("获取标签统计失败").getOrNull()?.data
                    }.getOrNull()
                }
                val systemDef = async {
                    runCatching {
                        api.getDashboardSystem(getAuthHeader()).unwrap("获取系统信息失败").getOrNull()?.data
                    }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                }

                val overview = overviewDef.await()
                val trend = trendDef.await()
                val topCount = topCountDef.await()
                val topTime = topTimeDef.await()
                val labels = labelsDef.await()
                var system = systemDef.await()

            // 官方标准青龙回退：若 /api/dashboard/* 404（非标版本特性），自动通过 /api/system 与任务列表聚合真实数据
            var fallbackCrons: List<QlCronItem> = emptyList()
            if (overview == null || labels == null || topCount == null) {
                runCatching {
                    val cronsResp = api.getCrons(getAuthHeader()).unwrap("获取任务列表失败").getOrNull()
                    fallbackCrons = parseCronArray(cronsResp?.data)
                }
            }

            if (system == null) {
                runCatching {
                    system = api.getSystemInfo(getAuthHeader()).unwrap("获取系统信息失败").getOrNull()?.data
                        ?.takeIf { it.isJsonObject }?.asJsonObject
                }
            }

            fun com.google.gson.JsonObject?.intOf(key: String): Int? =
                this?.get(key)?.takeIf { it.isJsonPrimitive }?.asNumber?.toInt()

            fun com.google.gson.JsonObject?.longOf(key: String): Long? =
                this?.get(key)?.takeIf { it.isJsonPrimitive }?.asNumber?.toLong()

            fun com.google.gson.JsonObject?.strOf(key: String): String? =
                this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

            val totalTasks = overview.intOf("total") ?: if (fallbackCrons.isNotEmpty()) fallbackCrons.size else null
            val enabledTasks = overview.intOf("enabled") ?: if (fallbackCrons.isNotEmpty()) fallbackCrons.count { it.isDisabled == 0 } else null
            val disabledTasks = overview.intOf("disabled") ?: if (fallbackCrons.isNotEmpty()) fallbackCrons.count { it.isDisabled == 1 } else null
            val runningCount = if (fallbackCrons.isNotEmpty()) fallbackCrons.count { it.status == 0 } else 0

            val cpu = system?.let { s ->
                val load1 = s.getAsJsonArray("loadAvg")?.firstOrNull { it.isJsonPrimitive }
                    ?.let { runCatching { it.asNumber.toDouble() }.getOrNull() }
                    ?: s.get("cpu")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asNumber.toDouble() }.getOrNull() }
                    ?: 0.0
                val cpus = s.intOrNull("cpus") ?: 1
                if (load1 > 0) String.format(java.util.Locale.US, "%.1f%%", (load1 / cpus.coerceAtLeast(1)) * 100.0) else null
            }

            val fallbackLabelStats = if (labels == null && fallbackCrons.isNotEmpty()) {
                val lMap = mutableMapOf<String, Int>()
                fallbackCrons.forEach { c ->
                    c.labels?.forEach { l -> lMap[l] = (lMap[l] ?: 0) + 1 }
                }
                lMap.map { (k, v) -> LabelStat(k, v) }
            } else parseLabelArray(labels)

            val parsedTopCount = parseRankArray(topCount) { obj ->
                TaskRank(
                    rank = obj.get("rank")?.asInt ?: 0,
                    name = obj.get("name")?.asString ?: "未知任务",
                    value = "${obj.get("runCount")?.asInt ?: 0} 次",
                    detail = "均耗 ${obj.get("avgTime")?.asLong ?: 0}ms · 成功率 ${obj.get("successRate")?.asString ?: "-"}%"
                )
            }.ifEmpty {
                if (fallbackCrons.isNotEmpty()) {
                    fallbackCrons.filter { (it.last_execution_time ?: 0) > 0 }
                        .sortedByDescending { it.last_execution_time ?: 0 }
                        .take(5)
                        .mapIndexed { idx, c ->
                            TaskRank(
                                rank = idx + 1,
                                name = c.name ?: "未命名任务",
                                value = if ((c.last_running_time ?: 0) > 0) "${c.last_running_time}s" else "已调度",
                                detail = "规则: ${c.schedule ?: "-"}"
                            )
                        }
                } else emptyList()
            }

            Result.success(
                PanelDashboard(
                    totalTasks = totalTasks,
                    enabledTasks = enabledTasks,
                    disabledTasks = disabledTasks,
                    todayRuns = overview.longOf("todayRuns") ?: if (runningCount > 0) runningCount.toLong() else null,
                    todaySuccess = overview.longOf("todaySuccess"),
                    todayFail = overview.longOf("todayFail"),
                    successRate = overview.strOf("successRate"),
                    avgTimeMs = overview.longOf("avgTime"),
                    trend = parseTrendArray(trend),
                    topByCount = parsedTopCount,
                    topByTime = parseRankArray(topTime) { obj ->
                        TaskRank(
                            rank = obj.get("rank")?.asInt ?: 0,
                            name = obj.get("name")?.asString ?: "未知任务",
                            value = "${obj.get("avgTime")?.asLong ?: 0}ms",
                            detail = "峰值 ${obj.get("maxTime")?.asLong ?: 0}ms"
                        )
                    },
                    labelStats = fallbackLabelStats,
                    cpuUsage = cpu,
                    memUsage = system.strOf("memUsagePercent")?.let { "$it%" },
                    resourceDetail = buildMap {
                        system?.let { s ->
                            s.strOf("version")?.let { put("青龙内核", "v$it") }
                            s.strOf("platform")?.let { put("系统环境", it) }
                            s.intOf("cpus")?.let { put("CPU 核数", "$it") }
                            s.longOf("uptime")?.let { put("运行时长", formatSeconds(it)) }
                            s.strOf("memUsagePercent")?.let { put("内存占用", "$it%") }
                        }
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTrendArray(data: com.google.gson.JsonElement?): List<TrendPoint> {
        if (data?.isJsonArray != true) return emptyList()
        return data.asJsonArray.mapNotNull { elem ->
            if (!elem.isJsonObject) return@mapNotNull null
            val obj = elem.asJsonObject
            TrendPoint(
                date = obj.get("date")?.asString ?: "",
                total = obj.get("total")?.asInt ?: 0,
                success = obj.get("success")?.asInt ?: 0,
                fail = obj.get("fail")?.asInt ?: 0
            )
        }
    }

    private inline fun parseRankArray(
        data: com.google.gson.JsonElement?,
        map: (com.google.gson.JsonObject) -> TaskRank
    ): List<TaskRank> {
        if (data?.isJsonArray != true) return emptyList()
        return data.asJsonArray.mapNotNull { if (it.isJsonObject) map(it.asJsonObject) else null }
    }

    private fun parseLabelArray(data: com.google.gson.JsonElement?): List<LabelStat> {
        if (data?.isJsonArray != true) return emptyList()
        return data.asJsonArray.mapNotNull { elem ->
            if (!elem.isJsonObject) return@mapNotNull null
            val obj = elem.asJsonObject
            LabelStat(
                label = obj.get("label")?.asString ?: "未分类",
                count = obj.get("count")?.asInt ?: 0,
                todayRuns = obj.get("todayRuns")?.asInt ?: 0,
                successRate = obj.get("successRate")?.asString?.let { "$it%" },
                avgTimeMs = obj.get("avgTime")?.asLong
            )
        }
    }

    // ---------------------------------------------------------------- 10. 系统设置（青龙专有）

    /**
     * 读取系统配置里的常用项。
     * `GET /api/system/config` 返回 SystemInfo，字段随版本变化，
     * 这里只按名字取需要的键，缺失就返回 null，避免强绑定某个版本的结构。
     */
    suspend fun fetchSystemSettings(): Result<Map<String, String>> {
        ensureAuth()
        return try {
            val result = mutableMapOf<String, String>()

            fun extractEntries(target: com.google.gson.JsonObject, prefix: String = "") {
                target.entrySet().forEach { (k, v) ->
                    val fullKey = if (prefix.isEmpty()) k else "$prefix.$k"
                    if (v.isJsonPrimitive) {
                        result[fullKey] = v.asString
                        if (!result.containsKey(k)) {
                            result[k] = v.asString
                        }
                    } else if (v.isJsonObject) {
                        extractEntries(v.asJsonObject, fullKey)
                    }
                }
            }

            runCatching {
                val resp = api.getSystemConfig(getAuthHeader())
                val env = resp.unwrap("读取配置失败").getOrNull()
                val obj = env?.data?.takeIf { it.isJsonObject }?.asJsonObject
                if (obj != null) {
                    extractEntries(obj)
                }
            }

            if (!result.containsKey("logRemoveFrequency") || !result.containsKey("cronConcurrency")) {
                runCatching {
                    val sysResp = api.getSystemInfo(getAuthHeader())
                    val sysEnv = sysResp.unwrap("读取系统信息失败").getOrNull()
                    val sysObj = sysEnv?.data?.takeIf { it.isJsonObject }?.asJsonObject
                    if (sysObj != null) {
                        extractEntries(sysObj)
                    }
                }
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 日志保留天数，null 表示不自动清理 */
    suspend fun saveLogRemoveFrequency(days: Int?): Result<Boolean> {
        ensureAuth()
        return api.updateLogRemoveFrequency(getAuthHeader(), mapOf("logRemoveFrequency" to days))
            .unwrap("保存日志保留天数失败").map { true }
    }

    /** 任务并发数，null 表示不限制 */
    suspend fun saveCronConcurrency(count: Int?): Result<Boolean> {
        ensureAuth()
        return api.updateCronConcurrency(getAuthHeader(), mapOf("cronConcurrency" to count))
            .unwrap("保存任务并发数失败").map { true }
    }

    suspend fun sendTestNotify(title: String, content: String): Result<Boolean> {
        ensureAuth()
        return api.testNotify(getAuthHeader(), mapOf("title" to title, "content" to content))
            .unwrap("发送测试通知失败").map { true }
    }

    /** 修改配置后需重载才会对调度器生效 */
    suspend fun reloadSystem(type: String? = null): Result<Boolean> {
        ensureAuth()
        return api.reloadSystem(getAuthHeader(), mapOf("type" to type))
            .unwrap("重载配置失败").map { true }
    }

    // ---------------------------------------------------------------- 工具

    private fun parseJsonArray(data: com.google.gson.JsonElement?): List<com.google.gson.JsonElement> =
        when {
            data == null -> emptyList()
            data.isJsonArray -> data.asJsonArray.toList()
            data.isJsonObject && data.asJsonObject.get("data")?.isJsonArray == true ->
                data.asJsonObject.getAsJsonArray("data").toList()
            else -> emptyList()
        }

    private fun formatSeconds(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    // ---------- JsonObject 空安全取值 ----------
    // Gson 的 JsonNull 不是 null，而是单例对象；在它上面调 asString / asInt /
    // asLong / asDouble / asJsonArray 都会抛 UnsupportedOperationException。
    // 青龙部分版本与降级响应会出现字段为 null 的情况，必须先判 isJsonPrimitive。

    private fun com.google.gson.JsonObject?.strOrNull(key: String): String? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun com.google.gson.JsonObject?.intOrNull(key: String): Int? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asNumber.toInt() }.getOrNull() }

    private fun com.google.gson.JsonObject?.longOrNull(key: String): Long? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asNumber.toLong() }.getOrNull() }

    private fun com.google.gson.JsonObject?.arrayFirstDouble(key: String): Double? =
        this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.firstOrNull { it.isJsonPrimitive }
            ?.let { runCatching { it.asNumber.toDouble() }.getOrNull() }

    private fun formatBytes(bytes: Long?): String? {
        if (bytes == null || bytes <= 0) return null
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private companion object {
        /** 后端 Joi 约束：name 必须是合法 shell 变量名 */
        val ENV_NAME_REGEX = Regex("^[a-zA-Z_][0-9a-zA-Z_]*$")
    }
}
