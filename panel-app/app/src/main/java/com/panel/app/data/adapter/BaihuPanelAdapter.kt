package com.panel.app.data.adapter

import com.panel.app.data.backup.*
import com.panel.app.data.model.*
import com.panel.app.data.remote.NetworkClient
import com.panel.app.data.remote.OtpRequiredException
import com.panel.app.data.remote.api.*
import com.panel.app.data.remote.unwrap
import com.panel.app.data.remote.unwrapTo
import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 白虎面板适配器。
 *
 * ### 鉴权（与青龙不同，务必注意）
 * 白虎的 `Authorization: Bearer` 头**只用于跨面板互联 Token**，
 * 普通用户走的是 Cookie `BHToken`（见 `internal/middleware/auth.go`）。
 * 官方前端也是用 `credentials: 'include'` 而非 Bearer。
 * 因此这里不再拼 Authorization 头，统一由 [NetworkClient] 的 CookieJar 携带凭据。
 *
 * ### 错误处理
 * 白虎所有错误都是 HTTP 200 + `{"code":4xx,"msg":"..."}`，
 * 因此每个请求都必须经 [unwrap] 解包，不能用 `isSuccessful` 判断。
 *
 * 契约来源：`baihu-panel/web/src/api/index.ts`
 */
class BaihuPanelAdapter(
    override val instance: PanelInstance
) : IPanelAdapter {

    private val api: BaihuApi = NetworkClient.buildRetrofit(instance.baseUrl).create(BaihuApi::class.java)
    private var currentToken: String? = instance.token

    /** 两步验证中间态：登录返回 require_otp 后暂存，待用户输入验证码 */
    private var otpPendingToken: String? = null

    private val cookieHost: String
        get() = try {
            java.net.URI(instance.baseUrl).host ?: ""
        } catch (_: Exception) {
            ""
        }

    private fun injectCookie(token: String) {
        val host = cookieHost
        if (host.isNotEmpty()) NetworkClient.injectCookie(host, "BHToken", token)
    }

    private suspend fun ensureAuth(): Boolean {
        val existing = currentToken
        if (!existing.isNullOrEmpty()) {
            injectCookie(existing)
            return true
        }
        if (!instance.username.isNullOrEmpty() && !instance.password.isNullOrEmpty()) {
            return authenticate().isSuccess
        }
        return false
    }

    // ---------------------------------------------------------------- 认证

    override suspend fun authenticate(): Result<String> {
        val user = instance.username
        val pwd = instance.password
        if (user.isNullOrEmpty() || pwd.isNullOrEmpty()) {
            // 无账号密码时用已保存的 token 继续（CookieJar 会带上）
            val saved = instance.token
            if (!saved.isNullOrEmpty()) {
                currentToken = saved
                injectCookie(saved)
                return Result.success(saved)
            }
            return Result.failure(Exception("请先登录面板账号"))
        }

        val resp = try {
            api.login(BaihuLoginReq(user, pwd))
        } catch (e: Exception) {
            return Result.failure(Exception("连接失败: ${e.message ?: "网络超时，请检查面板地址"}"))
        }

        val envelope = resp.unwrap("登录失败").getOrElse { return Result.failure(it) }

        // 两步验证：此时后端尚未下发 Cookie，不能再往下走
        val data = envelope.data
        if (data?.require_otp == true) {
            val pending = data.otp_pending_token
            if (!pending.isNullOrEmpty()) {
                otpPendingToken = pending
                return Result.failure(OtpRequiredException(pending))
            }
            return Result.failure(Exception("该账号已开启两步验证，但未返回临时凭证"))
        }

        return captureToken(resp, "登录失败")
    }

    /** 两步验证第二步：用验证码换取正式 Cookie */
    suspend fun submitOtp(code: String): Result<String> {
        val pending = otpPendingToken
            ?: return Result.failure(Exception("验证会话已失效，请重新登录"))

        val resp = try {
            api.loginOtp(BaihuOtpLoginReq(pending, code))
        } catch (e: Exception) {
            return Result.failure(Exception("连接失败: ${e.message ?: "网络超时"}"))
        }

        return resp.unwrap("验证码校验失败").let {
            if (it.isFailure) return Result.failure(it.exceptionOrNull() ?: Exception("未知失败"))
            captureToken(resp, "验证码校验失败").also { otpPendingToken = null }
        }
    }

    /**
     * 从 Set-Cookie 里取 BHToken。
     * 白虎不在响应体返回 token，写死兜底值会导致后续所有请求 401 且无提示，
     * 所以这里取不到就直接报错，绝不伪造。
     */
    private fun captureToken(resp: retrofit2.Response<*>, fallback: String): Result<String> {
        val fromHeader = resp.headers().values("Set-Cookie")
            .firstOrNull { it.contains("BHToken=") }
            ?.substringAfter("BHToken=")
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val token = fromHeader
            ?: cookieHost.takeIf { it.isNotEmpty() }?.let { NetworkClient.getCookie(it, "BHToken") }

        if (token.isNullOrEmpty()) {
            return Result.failure(
                Exception("$fallback: 服务端未下发登录凭据，请检查面板地址与账号密码")
            )
        }

        currentToken = token
        injectCookie(token)
        return Result.success(token)
    }

    // ---------------------------------------------------------------- 1. 任务

    override suspend fun getTasks(query: String?): Result<List<UnifiedTask>> {
        ensureAuth()
        return try {
            // 明确传 type="task"：对齐白虎官方 web 的 TASK_TYPE.NORMAL，避免与仓库同步任务重复展示
            api.getTasks(name = query?.takeIf { it.isNotBlank() }, type = "task", pageSize = 200)
                .unwrapTo("获取白虎任务失败") { env ->
                    env.data?.data.orEmpty().map { it.toUnifiedTask() }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun BaihuTaskItem.toUnifiedTask(): UnifiedTask {
        val isRunning = running_status == "running"
        val tagsList = this.tags?.split(",", "，")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val resolvedName = when {
            !name.isNullOrBlank() && name != "未知任务" && name != "null" -> name
            !remark.isNullOrBlank() -> remark
            !command.isNullOrBlank() -> {
                val parts = command.trim().split("\\s+".toRegex())
                parts.lastOrNull { it.contains(".") } ?: command.trim()
            }
            type == "repo" -> "仓库任务 #${id.take(8)}"
            else -> "任务 #${id.take(8)}"
        }

        val langList = mutableListOf<String>()
        languages?.let { elem ->
            runCatching {
                if (elem.isJsonArray) {
                    elem.asJsonArray.forEach { item ->
                        when {
                            item.isJsonPrimitive -> langList.add(item.asString)
                            item.isJsonObject -> {
                                val n = item.asJsonObject.get("name")?.asString ?: ""
                                val v = item.asJsonObject.get("version")?.asString ?: ""
                                if (n.isNotEmpty()) langList.add(if (v.isNotEmpty()) "$n:$v" else n)
                            }
                        }
                    }
                }
            }
        }

        return UnifiedTask(
            id = id,
            name = resolvedName,
            command = command.orEmpty(),
            schedule = schedule.orEmpty(),
            statusText = when {
                isRunning -> "运行中"
                enabled == false -> "已禁用"
                else -> "已启用"
            },
            isRunning = isRunning,
            isDisabled = enabled == false,
            isPinned = pin_type == "top",
            labels = if (remark.isNullOrBlank()) tagsList else tagsList + remark,
            timeout = timeout ?: 30,
            createdAt = created_at,
            updatedAt = updated_at,
            preCommand = pre_command,
            postCommand = post_command,
            workDir = work_dir,
            agentId = agent_id,
            retryCount = retry_count ?: 0,
            retryInterval = retry_interval ?: 0,
            randomRange = random_range ?: 0,
            languages = langList,
            lastRunTime = last_run,
            nextRunTime = next_run,
            cleanConfig = clean_config,
            taskType = type
        )
    }

    override suspend fun createTask(name: String, command: String, schedule: String): Result<Boolean> {
        ensureAuth()
        return try {
            api.createTask(BaihuCreateTaskReq(name = name, command = command, schedule = schedule))
                .unwrap("创建白虎任务失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTask(task: UnifiedTask): Result<Boolean> {
        ensureAuth()
        return try {
            api.updateTask(
                task.id,
                BaihuUpdateTaskReq(
                    name = task.name,
                    command = task.command,
                    schedule = task.schedule,
                    timeout = task.timeout,
                    enabled = !task.isDisabled,
                    pin_type = if (task.isPinned) "top" else "none"
                )
            ).unwrap("更新白虎任务失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun runTask(taskIds: List<String>): Result<Boolean> {
        ensureAuth()
        val errors = mutableListOf<String>()
        for (id in taskIds) {
            api.runTask(id).unwrap("运行任务失败")
                .onFailure { errors.add(it.message ?: "运行任务失败") }
        }
        return BaihuApiHelpers.summarize(errors)
    }

    /**
     * 停止任务。
     * 后端路径是 `/tasks/stop/{logID}`，**logID 是运行日志 ID 而非任务 ID**，
     * 所以必须先把 taskId 解析成正在运行的日志 ID。
     */
    override suspend fun stopTask(taskIds: List<String>): Result<Boolean> {
        ensureAuth()
        val errors = mutableListOf<String>()
        for (taskId in taskIds) {
            val logId = findRunningLogId(taskId)
            if (logId == null) {
                errors.add("任务当前没有运行中的实例")
                continue
            }
            api.stopTask(logId).unwrap("停止任务失败")
                .onFailure { errors.add(it.message ?: "停止任务失败") }
        }
        return BaihuApiHelpers.summarize(errors)
    }

    /** 任务日志状态取值：running / success / failed */
    private suspend fun findRunningLogId(taskId: String): String? {
        api.getLogs(taskId = taskId, status = "running", pageSize = 1)
            .unwrap("查询运行中的任务失败")
            .getOrNull()
            ?.data?.data
            ?.firstOrNull()?.id
            ?.let { return it }

        // 兜底：监控接口的 worker 明细也带 task_id，可确认是否真的在跑
        val workers = api.getMonitor().unwrap("查询监控失败").getOrNull()
            ?.data?.scheduler?.workers
        return if (workers?.any { it.task_id == taskId } == true) {
            api.getLogs(taskId = taskId, pageSize = 1).unwrap("查询任务日志失败")
                .getOrNull()?.data?.data?.firstOrNull()?.id
        } else null
    }

    override suspend fun toggleTask(taskId: String, enable: Boolean): Result<Boolean> {
        ensureAuth()
        return try {
            api.updateTask(taskId, BaihuUpdateTaskReq(enabled = enable))
                .unwrap("切换任务状态失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(taskIds: List<String>): Result<Boolean> {
        ensureAuth()
        return try {
            if (taskIds.size > 1) {
                api.batchDeleteTasks(BaihuBatchDeleteTasksReq(taskIds))
                    .unwrap("批量删除白虎任务失败").map { true }
            } else if (taskIds.isNotEmpty()) {
                api.deleteTask(taskIds.first())
                    .unwrap("删除白虎任务失败").map { true }
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 按查询条件批量删除任务。
     * 对齐前端 API：DELETE /api/v1/tasks/batch-by-query?name=&agent_id=&tags=&type=
     */
    suspend fun batchDeleteTaskByQuery(
        name: String? = null,
        agentId: String? = null,
        tags: String? = null,
        type: String? = null
    ): Result<Boolean> {
        ensureAuth()
        return try {
            api.batchDeleteTaskByQuery(
                name = name,
                agentId = agentId,
                tags = tags,
                type = type
            ).unwrap("批量按条件删除任务失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pinTask(taskIds: List<String>, pin: Boolean): Result<Boolean> {
        ensureAuth()
        val errors = mutableListOf<String>()
        for (id in taskIds) {
            api.updateTask(id, BaihuUpdateTaskReq(pin_type = if (pin) "top" else "none"))
                .unwrap("置顶操作失败")
                .onFailure { errors.add(it.message ?: "置顶操作失败") }
        }
        return BaihuApiHelpers.summarize(errors)
    }

    override suspend fun getTaskInstances(taskId: String): Result<List<TaskInstanceRecord>> {
        ensureAuth()
        return try {
            api.getLogs(taskId = taskId.takeIf { it.isNotBlank() }, pageSize = 100)
                .unwrapTo("获取执行记录失败") { env ->
                    env.data?.data.orEmpty().map { log ->
                        TaskInstanceRecord(
                            id = log.id,
                            taskName = log.task_name ?: "任务 #${log.id}",
                            startTime = log.start_time ?: "--",
                            endTime = log.end_time,
                            duration = log.duration?.let { BaihuApiHelpers.formatDuration(it) } ?: "--",
                            exitCode = log.exit_code ?: 0,
                            statusText = when (log.status) {
                                "running" -> "运行中"
                                "success" -> "成功"
                                "failed" -> "失败"
                                else -> log.status ?: "完成"
                            }
                        )
                    }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTaskLog(taskNameOrId: String): Result<String> {
        ensureAuth()
        return try {
            // 参数可能是日志 ID，也可能是任务 ID
            val direct = api.getLogDetail(taskNameOrId).unwrap("获取日志详情失败")
            if (direct.isSuccess) {
                val d = direct.getOrNull()?.data
                return Result.success(if (d != null) { val out = d.output; val err = d.error; if (!out.isNullOrBlank()) out else if (!err.isNullOrBlank()) err else "暂无日志内容" } else "暂无日志内容")
            }
            val latest = api.getLogs(taskId = taskNameOrId, pageSize = 1)
                .unwrap("获取任务日志失败").getOrNull()
                ?.data?.data?.firstOrNull()
                ?: return Result.success("暂无任务执行日志记录")

            api.getLogDetail(latest.id)
                .unwrapTo("获取日志详情失败") { d ->
                    if (d != null) { val out = d.data?.output ?: ""; val err = d.data?.error ?: ""; if (!out.isNullOrBlank()) out else if (!err.isNullOrBlank()) err else "暂无日志内容" } else "暂无日志内容"
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- 2. 订阅（白虎对应仓库任务）

    override suspend fun getSubscriptions(query: String?): Result<List<UnifiedSubscription>> {
        ensureAuth()
        return try {
            api.getTasks(name = query?.takeIf { it.isNotBlank() }, type = "repo", pageSize = 100)
                .unwrapTo("获取仓库任务失败") { env ->
                    env.data?.data.orEmpty().map { it.toUnifiedSubscription() }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun BaihuTaskItem.toUnifiedSubscription(): UnifiedSubscription {
        var repoUrl = ""
        var branch = "main"
        var whitelist = ""
        var blacklist = ""
        var autoAddCron = true
        var targetPath = ""
        var sparsePath = ""
        var singleFile = false
        var proxy = "none"
        var proxyUrl = ""
        var authToken = ""
        var repoDirName = ""
        var commentToTask = false

        config?.let { raw ->
            runCatching {
                val obj = if (raw.trim().startsWith("{")) {
                    com.google.gson.JsonParser.parseString(raw).asJsonObject
                } else null
                obj?.let {
                    repoUrl = it.get("repo_url")?.takeIf { !it.isJsonNull }?.asString ?: it.get("repourl")?.takeIf { !it.isJsonNull }?.asString ?: it.get("source_url")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    branch = it.get("branch")?.takeIf { !it.isJsonNull }?.asString ?: "main"
                    whitelist = it.get("whitelist_paths")?.takeIf { !it.isJsonNull }?.asString ?: it.get("whitelist")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    blacklist = it.get("blacklist")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    autoAddCron = it.get("auto_add_cron")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
                    targetPath = it.get("target_path")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    sparsePath = it.get("sparse_path")?.takeIf { !it.isJsonNull }?.asString ?: it.get("path")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    singleFile = it.get("single_file")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                    proxy = it.get("proxy")?.takeIf { !it.isJsonNull }?.asString ?: "none"
                    proxyUrl = it.get("proxy_url")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    authToken = it.get("auth_token")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    repoDirName = it.get("repo_dir_name")?.takeIf { !it.isJsonNull }?.asString ?: it.get("repo_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    commentToTask = it.get("commenttotask")?.takeIf { !it.isJsonNull }?.asString == "true" || it.get("auto_add_cron")?.takeIf { !it.isJsonNull }?.asBoolean == true
                }
            }
        }

        if (repoUrl.isEmpty()) {
            repoUrl = command?.let {
                Regex("""(?:clone|pull)\s+([^\s]+)""").find(it)?.groupValues?.getOrNull(1)
            } ?: command ?: ""
        }

        val langList = mutableListOf<String>()
        languages?.let { elem ->
            runCatching {
                if (elem.isJsonArray) {
                    elem.asJsonArray.forEach { item ->
                        when {
                            item.isJsonPrimitive -> langList.add(item.asString)
                            item.isJsonObject -> {
                                val n = item.asJsonObject.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                                val v = item.asJsonObject.get("version")?.takeIf { !it.isJsonNull }?.asString ?: ""
                                if (n.isNotEmpty()) langList.add(if (v.isNotEmpty()) "$n:$v" else n)
                            }
                        }
                    }
                }
            }
        }

        return UnifiedSubscription(
            id = id,
            name = name ?: (remark?.takeIf { it.isNotBlank() } ?: "仓库订阅 #${id.take(8)}"),
            type = "public-repo",
            url = repoUrl,
            branch = branch,
            schedule = schedule ?: "0 0 * * *",
            whitelist = whitelist,
            blacklist = blacklist,
            autoAddCron = autoAddCron,
            statusText = when {
                running_status == "running" -> "同步中"
                enabled == false -> "已禁用"
                else -> "就绪"
            },
            isRunning = running_status == "running",
            isDisabled = enabled == false,
            lastRunTime = last_run,
            nextRunTime = next_run,
            languages = langList,
            location = if (agent_id.isNullOrEmpty()) "本地" else "节点: $agent_id",
            targetPath = targetPath,
            sparsePath = sparsePath,
            singleFile = singleFile,
            proxy = proxy,
            proxyUrl = proxyUrl,
            authToken = authToken,
            repoDirName = repoDirName,
            commentToTask = commentToTask
        )
    }

    override suspend fun createSubscription(sub: UnifiedSubscription): Result<Boolean> {
        ensureAuth()
        return try {
            val config = com.google.gson.JsonObject().apply {
                addProperty("source_type", "git")
                addProperty("source_url", sub.url)
                addProperty("repo_url", sub.url)
                addProperty("branch", sub.branch.ifEmpty { "main" })
                addProperty("target_path", sub.targetPath ?: "")
                addProperty("sparse_path", sub.sparsePath ?: "")
                addProperty("single_file", sub.singleFile)
                addProperty("proxy", sub.proxy ?: "none")
                addProperty("proxy_url", sub.proxyUrl ?: "")
                addProperty("auth_token", sub.authToken ?: "")
                addProperty("whitelist_paths", sub.whitelist)
                addProperty("blacklist", sub.blacklist)
                addProperty("dependence", sub.dependences)
                addProperty("extensions", sub.extensions)
                addProperty("auto_add_cron", sub.autoAddCron)
                addProperty("commenttotask", if (sub.autoAddCron || sub.commentToTask) "true" else "false")
                addProperty("repo_dir_name", sub.repoDirName ?: "")
            }
            val cmd = "[git] ${sub.url}"
            api.createTask(
                BaihuCreateTaskReq(
                    name = sub.name,
                    type = "repo",
                    command = cmd,
                    config = config.toString(),
                    schedule = sub.schedule
                )
            ).unwrap("创建仓库同步任务失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSubscription(sub: UnifiedSubscription): Result<Boolean> {
        ensureAuth()
        return try {
            val config = com.google.gson.JsonObject().apply {
                addProperty("source_type", "git")
                addProperty("source_url", sub.url)
                addProperty("repo_url", sub.url)
                addProperty("branch", sub.branch.ifEmpty { "main" })
                addProperty("target_path", sub.targetPath ?: "")
                addProperty("sparse_path", sub.sparsePath ?: "")
                addProperty("single_file", sub.singleFile)
                addProperty("proxy", sub.proxy ?: "none")
                addProperty("proxy_url", sub.proxyUrl ?: "")
                addProperty("auth_token", sub.authToken ?: "")
                addProperty("whitelist_paths", sub.whitelist)
                addProperty("blacklist", sub.blacklist)
                addProperty("dependence", sub.dependences)
                addProperty("extensions", sub.extensions)
                addProperty("auto_add_cron", sub.autoAddCron)
                addProperty("commenttotask", if (sub.autoAddCron || sub.commentToTask) "true" else "false")
                addProperty("repo_dir_name", sub.repoDirName ?: "")
            }
            val cmd = "[git] ${sub.url}"
            api.updateTask(
                sub.id,
                BaihuUpdateTaskReq(
                    name = sub.name,
                    command = cmd,
                    schedule = sub.schedule,
                    enabled = !sub.isDisabled,
                    config = config.toString()
                )
            ).unwrap("更新仓库同步任务失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSubscription(subIds: List<String>): Result<Boolean> = deleteTask(subIds)
    override suspend fun runSubscription(subIds: List<String>): Result<Boolean> = runTask(subIds)
    override suspend fun stopSubscription(subIds: List<String>): Result<Boolean> = stopTask(subIds)
    override suspend fun getSubscriptionLog(subId: String): Result<String> = getTaskLog(subId)

    // ---------------------------------------------------------------- 3. 环境变量

    override suspend fun getEnvs(query: String?): Result<List<UnifiedEnv>> {
        ensureAuth()
        return try {
            api.getEnvs(name = query?.takeIf { it.isNotBlank() }, pageSize = 200)
                .unwrapTo("获取白虎环境变量失败") { env ->
                    env.data?.data.orEmpty().map { item ->
                        UnifiedEnv(
                            id = item.id,
                            name = item.name,
                            value = item.value,
                            remarks = item.remark,
                            enabled = item.enabled ?: true,
                            labels = item.tags?.split(",").orEmpty().filter { it.isNotBlank() },
                            isPinned = false
                        )
                    }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveEnv(env: UnifiedEnv): Result<Boolean> {
        ensureAuth()
        if (env.name.isBlank()) {
            return Result.failure(Exception("变量名不能为空"))
        }
        return try {
            val isNew = env.id.isEmpty() || env.id.startsWith("new_") || env.id.startsWith("tmp_")
            val req = BaihuCreateEnvReq(
                name = env.name.trim(),
                value = env.value,
                remark = env.remarks ?: "",
                enabled = env.enabled
            )
            val result = if (isNew) {
                api.createEnv(req).unwrap("创建白虎环境变量失败")
            } else {
                // 局部更新：这里只提交用户实际改动的字段，避免把已有值覆盖成空
                api.updateEnv(env.id, req).unwrap("更新白虎环境变量失败")
            }
            result.map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleEnv(envId: String, enable: Boolean): Result<Boolean> {
        ensureAuth()
        return try {
            api.updateEnv(envId, BaihuCreateEnvReq(name = "", value = "", enabled = enable))
                .unwrap("切换变量状态失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEnv(envIds: List<String>): Result<Boolean> {
        ensureAuth()
        val errors = mutableListOf<String>()
        for (id in envIds) {
            api.deleteEnv(id).unwrap("删除环境变量失败")
                .onFailure { errors.add(it.message ?: "删除环境变量失败") }
        }
        return BaihuApiHelpers.summarize(errors)
    }

    // ---------------------------------------------------------------- 环境变量辅助方法

    override suspend fun batchEnableEnvs(envIds: List<String>): Result<Boolean> {
        return batchToggleEnvs(envIds, enable = true)
    }

    override suspend fun batchDisableEnvs(envIds: List<String>): Result<Boolean> {
        return batchToggleEnvs(envIds, enable = false)
    }

    /** 逐条调用 PUT /env/{id} 更新 enabled 状态（保留原 name/value 不覆盖） */
    private suspend fun batchToggleEnvs(envIds: List<String>, enable: Boolean): Result<Boolean> {
        ensureAuth()
        // 先拉取全量 env 列表，保留每条 env 的 name/value，只替换 enabled
        val all = getEnvs(null).getOrNull() ?: return Result.failure(Exception("无法获取环境变量列表，批量操作取消"))
        val errors = mutableListOf<String>()
        for (id in envIds) {
            val existing = all.find { it.id == id }
            if (existing == null) {
                errors.add("$id: 未找到该变量")
                continue
            }
            api.updateEnv(id, BaihuCreateEnvReq(
                name = existing.name,
                value = existing.value,
                remark = existing.remarks,
                enabled = enable
            ))
                .unwrap("切换变量状态失败")
                .onFailure { errors.add("$id: ${it.message}") }
        }
        return if (errors.isEmpty()) Result.success(true)
        else Result.failure(Exception("部分失败（${errors.size}/${envIds.size}）：${errors.take(2).joinToString("; ")}"))
    }

    override suspend fun exportEnvsJson(envIds: List<String>): Result<String> {
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

    /** 批量导入：白虎的 /env/bulk_save 按 name 做 upsert，一次请求 */
    override suspend fun importEnvs(envs: List<UnifiedEnv>): Result<Int> {
        if (envs.isEmpty()) return Result.failure(Exception("没有可导入的环境变量"))
        val invalid = envs.filter { it.name.isBlank() }
        if (invalid.isNotEmpty()) {
            return Result.failure(Exception("存在名称为空的环境变量，无法导入"))
        }
        return bulkSaveEnvs(
            envs.map {
                BaihuBulkEnvReq(
                    name = it.name,
                    value = it.value,
                    remark = it.remarks,
                    enabled = it.enabled
                )
            }
        ).map { envs.size }
    }

    // ---------------------------------------------------------------- 4. 配置文件（走文件接口）

    override suspend fun getConfigFiles(): Result<List<String>> {
        val tree = getScriptTree().getOrNull() ?: return Result.success(emptyList())
        val files = mutableListOf<String>()
        fun collect(nodes: List<ScriptNode>) {
            for (node in nodes) {
                if (node.isDir) {
                    node.children?.let { collect(it) }
                } else {
                    val lower = node.name.lowercase()
                    if (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                        || lower.endsWith(".sh") || lower.endsWith(".env")
                        || lower.endsWith(".conf") || lower.endsWith(".toml")
                    ) {
                        files.add(node.path)
                    }
                }
            }
        }
        collect(tree)
        return Result.success(files)
    }

    override suspend fun readConfig(path: String): Result<String> {
        ensureAuth()
        return try {
            api.getFileContent(path)
                .unwrapTo("读取配置文件失败") { it.data?.content ?: "" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveConfig(path: String, content: String): Result<Boolean> {
        ensureAuth()
        return try {
            api.saveFileContent(BaihuFileContentReq(path, content))
                .unwrap("保存配置文件失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- 5. 脚本文件

    private fun mapFileNode(node: BaihuFileNode): ScriptNode = ScriptNode(
        name = node.name,
        path = node.path,
        isDir = node.isDir,
        size = if (node.isDir) null else "-",
        mtime = node.modTime,
        children = node.children?.map { mapFileNode(it) }
    )

    override suspend fun getScriptTree(): Result<List<ScriptNode>> {
        ensureAuth()
        return try {
            api.getFileTree()
                .unwrapTo("获取脚本文件树失败") { env ->
                    env.data.orEmpty().map { mapFileNode(it) }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readScript(path: String): Result<String> = readConfig(path)
    override suspend fun saveScript(path: String, content: String): Result<Boolean> = saveConfig(path, content)

    override suspend fun createScript(path: String, content: String): Result<Boolean> {
        ensureAuth()
        return try {
            api.createFile(BaihuFileCreateReq(path, isDir = false))
                .unwrap("创建脚本失败")
                .also { result ->
                    // 文件建好后再写内容，否则保存会覆盖一个不存在的文件
                    if (result.isSuccess && content.isNotEmpty()) {
                        api.saveFileContent(BaihuFileContentReq(path, content))
                            .unwrap("写入脚本内容失败")
                    }
                }
                .map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(path: String): Result<Boolean> {
        ensureAuth()
        return try {
            api.createFile(BaihuFileCreateReq(path, isDir = true))
                .unwrap("创建文件夹失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteScript(path: String): Result<Boolean> {
        ensureAuth()
        return try {
            api.deleteFile(BaihuFileDeleteReq(path))
                .unwrap("删除失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 重命名与移动都走 /files/rename（白虎的 rename 就是 rename，跨目录由 /files/move 处理）。
     * 同目录 → rename；跨目录 → move。
     */
    override suspend fun renameScript(path: String, newPath: String): Result<Boolean> {
        ensureAuth()
        val oldParent = if (path.contains('/')) path.substringBeforeLast('/') else ""
        val newParent = if (newPath.contains('/')) newPath.substringBeforeLast('/') else ""
        val sameDir = oldParent == newParent
        return try {
            if (sameDir) {
                api.renameFile(BaihuFileRenameReq(path, newPath))
            } else {
                api.moveFile(BaihuFileMoveReq(path, newPath))
            }.unwrap("重命名失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 复制文件：POST /files/copy */
    override suspend fun copyScript(path: String, newPath: String): Result<Boolean> {
        ensureAuth()
        return try {
            api.copyFile(BaihuFileCopyReq(path, newPath))
                .unwrap("复制失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- 6. 依赖管理

    override suspend fun getDeps(query: String?): Result<List<UnifiedDep>> {
        ensureAuth()
        return try {
            // 后端 /deps 只支持 language 过滤，没有名称搜索。
            // 之前把搜索框内容当 language 传，输入任意文本都会查不到任何东西，
            // 这里改为拉全量后在本地按名称/备注过滤。
            api.getDependencies()
                .unwrapTo("获取依赖失败") { env ->
                    env.data.orEmpty()
                        .filter { d ->
                            val q = query?.trim().orEmpty()
                            if (q.isEmpty()) true
                            else d.name.contains(q, ignoreCase = true)
                                || d.remark?.contains(q, ignoreCase = true) == true
                        }
                        .map { d ->
                            UnifiedDep(
                                id = d.id,
                                name = d.name,
                                version = d.version ?: "",
                                type = d.language,
                                remarks = d.remark,
                                // 后端 DependencyVO 没有 status 字段，不伪造状态
                                status = 1,
                                log = d.log
                            )
                        }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun installDep(name: String, version: String, type: String, remark: String): Result<Boolean> {
        ensureAuth()
        return try {
            // 必须是 /deps/install；/deps 只登记记录，不会真正执行安装
            api.installDependency(
                BaihuCreateDepReq(
                    name = name,
                    version = version.trim().takeIf { it.isNotEmpty() },
                    language = normalizeLanguage(type),
                    remark = remark.trim().takeIf { it.isNotEmpty() }
                )
            ).unwrap("安装依赖失败").map { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 兼容青龙侧流传过来的语言名 */
    private fun normalizeLanguage(type: String): String = when (type.lowercase()) {
        "nodejs", "node", "npm" -> "node"
        "python3", "python", "pip", "pip3" -> "python"
        else -> type.lowercase()
    }

    override suspend fun deleteDep(depId: String, type: String): Result<Boolean> {
        ensureAuth()
        return try {
            // 先卸载，失败时强制删除记录
            val uninstall = api.uninstallDependency(depId).unwrap("卸载依赖失败")
            if (uninstall.isSuccess) {
                api.deleteDependency(depId).unwrap("清除依赖记录失败").map { true }
            } else {
                api.uninstallDependency(depId, force = true)
                    .unwrap("卸载依赖失败")
                    .map { true }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun batchDeleteDeps(depIds: List<String>): Result<Boolean> {
        ensureAuth()
        val errors = mutableListOf<String>()
        for (id in depIds) {
            val uninstall = api.uninstallDependency(id).unwrap("卸载依赖失败")
            if (uninstall.isFailure) {
                api.uninstallDependency(id, force = true)
                    .unwrap("卸载依赖失败")
                    .onFailure { errors.add(it.message ?: "卸载依赖失败") }
            }
            api.deleteDependency(id).unwrap("清除依赖记录失败")
                .onFailure { errors.add(it.message ?: "清除依赖记录失败") }
        }
        return BaihuApiHelpers.summarize(errors)
    }

    override suspend fun forceDeleteDeps(depIds: List<String>): Result<Boolean> {
        ensureAuth()
        val errors = mutableListOf<String>()
        for (id in depIds) {
            api.deleteDependency(id).unwrap("强制清除依赖记录失败")
                .onFailure { errors.add(it.message ?: "强制清除依赖记录失败") }
        }
        return BaihuApiHelpers.summarize(errors)
    }

    /** 重新安装依赖：POST /deps/reinstall/{id} */
    override suspend fun reinstallDeps(depIds: List<String>): Result<Boolean> {
        ensureAuth()
        val errors = mutableListOf<String>()
        for (id in depIds) {
            api.reinstallDependency(id).unwrap("重新安装依赖失败")
                .onFailure { errors.add(it.message ?: "重新安装依赖失败") }
        }
        return BaihuApiHelpers.summarize(errors)
    }

    /** 白虎没有「取消安装」接口，明确告知而不是静默返回成功 */
    override suspend fun cancelDeps(depIds: List<String>): Result<Boolean> =
        Result.failure(Exception("白虎面板不支持取消依赖安装"))

    /** POST /api/v1/auth/logout，面板侧吊销该会话 */
    override suspend fun logout(): Result<Boolean> {
        return api.logout().unwrap("退出登录失败").map { true }
    }

    /** 批量导入/保存环境变量：POST /env/bulk_save（按 id 或 name 做 upsert） */
    suspend fun bulkSaveEnvs(envs: List<BaihuBulkEnvReq>): Result<Boolean> {
        ensureAuth()
        if (envs.isEmpty()) return Result.failure(Exception("没有可导入的环境变量"))
        return api.bulkSaveEnvs(envs).unwrap("批量保存环境变量失败").map { true }
    }

    override suspend fun deleteTaskInstance(instanceId: String): Result<Boolean> {
        ensureAuth()
        return api.deleteLog(instanceId)
            .unwrap("删除实例失败").map { true }
    }

    override suspend fun batchDeleteTaskInstances(instanceIds: List<String>): Result<Boolean> {
        ensureAuth()
        if (instanceIds.isEmpty()) return Result.success(true)
        // 白虎 API 暂无批量删除接口，逐条调用 DELETE /api/v1/logs/{id}
        val errors = mutableListOf<String>()
        for (id in instanceIds) {
            api.deleteLog(id)
                .unwrap("删除实例失败")
                .onFailure { errors.add("$id: ${it.message}") }
        }
        return if (errors.isEmpty()) Result.success(true)
        else Result.failure(Exception("部分删除失败（${errors.size}/${instanceIds.size}）：${errors.take(3).joinToString("; ")}"))
    }

    // ---------------------------------------------------------------- 备份恢复

    /** 高保真恢复任务：创建带 remark；第二轮按名称回查 id，恢复 enabled / pin_type */
    override suspend fun restoreTasks(tasks: List<BackupTask>): Result<RestoreReport> {
        ensureAuth()
        val errors = mutableListOf<String>()
        var ok = 0
        var skipped = 0
        for (t in tasks) {
            if (t.name.isBlank() || t.command.isBlank() || t.schedule.isBlank()) {
                skipped++
                continue
            }
            val res = api.createTask(
                BaihuCreateTaskReq(
                    name = t.name,
                    command = t.command,
                    schedule = t.schedule,
                    remark = t.remark,
                    tags = t.labels?.joinToString(",")?.takeIf { it.isNotEmpty() }
                )
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
        }

        val needState = tasks.filter { it.isDisabled || it.isPinned }.map { it.name }
        if (needState.isNotEmpty()) {
            val list = api.getTasks(name = null, type = null, pageSize = 500)
                .unwrap("回查任务失败").getOrNull()?.data?.data.orEmpty()
            for (name in needState) {
                val origin = tasks.firstOrNull { it.name == name } ?: continue
                val created = list.firstOrNull { it.name == name } ?: continue
                api.updateTask(
                    created.id,
                    BaihuUpdateTaskReq(
                        enabled = !origin.isDisabled,
                        pin_type = if (origin.isPinned) "top" else "none"
                    )
                ).unwrap("恢复任务状态失败")
            }
        }

        return Result.success(RestoreReport("任务", tasks.size, ok, skipped, errors))
    }

    /** 白虎的 bulk_save 直接支持 enabled，一次请求即可完成高保真恢复。重复变量自动跳过 */
    override suspend fun restoreEnvs(envs: List<BackupEnv>): Result<RestoreReport> {
        ensureAuth()
        val valid = envs.filter { it.name.isNotBlank() }
        val skipped = envs.size - valid.size
        if (valid.isEmpty()) return Result.success(RestoreReport("环境变量", envs.size, 0, skipped, emptyList()))
        val res = api.bulkSaveEnvs(
            valid.map {
                BaihuBulkEnvReq(
                    name = it.name,
                    value = it.value,
                    remark = it.remarks,
                    enabled = it.enabled
                )
            }
        ).unwrap("导入环境变量失败")
        if (res.isSuccess) {
            return Result.success(RestoreReport("环境变量", envs.size, valid.size, skipped, emptyList()))
        }
        val errMsg = res.exceptionOrNull()?.message ?: ""
        // 部分变量已存在时，降级为逐条创建
        if ("已存在".contains(errMsg) || "exist".equals(errMsg, ignoreCase = true) ||
            "duplicate".equals(errMsg, ignoreCase = true) || errMsg.contains("重复")) {
            var ok = 0
            var dupSkipped = 0
            val errors = mutableListOf<String>()
            for (e in valid) {
                val singleRes = api.bulkSaveEnvs(listOf(BaihuBulkEnvReq(name = e.name, value = e.value, remark = e.remarks, enabled = e.enabled)))
                    .unwrap("导入环境变量失败")
                if (singleRes.isSuccess) {
                    ok++
                } else {
                    val eMsg = singleRes.exceptionOrNull()?.message ?: ""
                    if ("已存在".contains(eMsg) || "exist".equals(eMsg, ignoreCase = true) ||
                        "duplicate".equals(eMsg, ignoreCase = true) || eMsg.contains("重复")) {
                        dupSkipped++
                    } else {
                        errors.add("${e.name}: ${singleRes.exceptionOrNull()?.message}")
                    }
                }
            }
            return Result.success(RestoreReport("环境变量", envs.size, ok, skipped + dupSkipped, errors))
        }
        return Result.failure(res.exceptionOrNull() ?: Exception("导入失败"))
    }

    override suspend fun getDepLog(depId: String): Result<String> {
        ensureAuth()
        return try {
            // 依赖安装日志挂在依赖记录的 log 字段上，没有独立接口
            api.getDependencies()
                .unwrapTo("获取依赖日志失败") { env ->
                    val dep = env.data?.firstOrNull { it.id == depId }
                    dep?.log?.takeIf { it.isNotBlank() }
                        ?: "依赖 [${dep?.name ?: depId}] 暂无安装日志"
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- 7. 日志流

    /**
     * 通过白虎 SSE 接口流式读取实时日志。
     * 端点：GET /api/v1/logs/sse?log_id=xxx
     * 响应类型：application/x-ndjson，每行是一条 JSON，含 timestamp/type/message 字段。
     * 使用 OkHttp Call.enqueue() 异步流式读取，避免阻塞协程调度线程。
     */
    override fun streamLog(logId: String): Flow<String> = callbackFlow {
        val ctx = currentCoroutineContext()
        val baseUrl = instance.baseUrl.removeSuffix("/")
        val url = "$baseUrl/api/v1/logs/sse?log_id=${URLEncoder.encode(logId, StandardCharsets.UTF_8.toString())}"
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Accept", "application/x-ndjson")
            .build()

        var reader: BufferedReader? = null
        try {
            NetworkClient.unsafeOkHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    trySend("[ERROR] ${e.message ?: "连接失败"}")
                    close()
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!ctx.isActive) { response.close(); return }
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()?.trim()
                        trySend("[ERROR] HTTP ${response.code}: $errBody")
                        close()
                        return
                    }
                    val body = response.body
                        ?: run { trySend("[ERROR] 空响应体"); close(); return }
                    reader = BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (!ctx.isActive) break
                        line?.trim()?.takeIf { it.isNotEmpty() }?.let { rawLine ->
                            try {
                                val json = JsonParser.parseString(rawLine).asJsonObject
                                val msg = json["message"]?.takeIf { !it.isJsonNull }?.asString?.trim()
                                    ?: json["content"]?.takeIf { !it.isJsonNull }?.asString?.trim()
                                    ?: rawLine
                                if (msg.isNotEmpty()) trySend(msg)
                            } catch (_: Exception) {
                                // 非 JSON 行直接透传
                                trySend(rawLine)
                            }
                        }
                    }
                    close()
                }
            })
        } catch (e: Exception) {
            trySend("[ERROR] ${e.message}")
            close()
        }
    }

    // ---------------------------------------------------------------- 8. 监控

    override suspend fun getMetrics(): Result<Pair<String, String>> {
        ensureAuth()
        return try {
            api.getMonitor()
                .unwrapTo("获取监控数据失败") { env ->
                    val host = env.data?.host
                    val cpu = host?.cpu_percent?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "--"
                    val mem = host?.mem_percent?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "--"
                    cpu to mem
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 额外暴露：调度器状态（运行中任务数 + worker 明细，含 task_id） */
    suspend fun getSchedulerStatus(): Result<BaihuMonitorScheduler?> {
        ensureAuth()
        return api.getMonitor().unwrapTo("获取监控数据失败") { it.data?.scheduler }
    }

    /**
     * 白虎没有"运行实例"概念，只有 `/monitor` 的 worker 明细（带 task_id）。
     * 没有 instanceId，停止时需自行把 task_id 解析成运行日志 ID。
     */
    override suspend fun getRunningTasks(): Result<List<RunningTaskInfo>> {
        ensureAuth()
        return api.getMonitor()
            .unwrapTo("获取运行中任务失败") { env ->
                env.data?.scheduler?.workers
                    ?.filter { it.status == "running" && !it.task_id.isNullOrEmpty() }
                    ?.map { w ->
                        RunningTaskInfo(
                            taskId = w.task_id ?: "",
                            name = w.task_name ?: "任务 #${w.task_id}",
                            instanceId = null,
                            pid = null,
                            elapsedSeconds = w.start_time?.let { (System.currentTimeMillis() / 1000) - it }
                                ?: w.duration
                        )
                    }
                    ?: emptyList()
            }
    }

    /**
     * 白虎停止需要的是日志 ID（非任务 ID）。
     * 优先使用 instanceId 作为 logID 直接停止；否则调用 findRunningLogId 解析。
     */
    override suspend fun stopRunningTask(taskId: String, instanceId: String?): Result<Boolean> {
        ensureAuth()
        if (!instanceId.isNullOrBlank()) {
            // 有明确实例 ID，直接传日志 ID 停止（白虎的 logID = 日志记录 ID）
            return api.stopTask(instanceId)
                .unwrap("停止运行实例失败").map { true }
        }
        return stopTask(listOf(taskId))
    }

    // ---------------------------------------------------------------- 仪表盘

    /**
     * 白虎的统计分散在 /stats、/sendstats、/taskstats、/monitor 四个接口，
     * 这里并行拉取后合并；单个接口失败只丢那块数据。
     */
    override suspend fun getDashboard(): Result<PanelDashboard> {
        ensureAuth()
        return try {
            val stats = runCatching {
                api.getStats().unwrap("获取统计失败").getOrNull()?.data
            }.getOrNull()

            val trend = runCatching {
                api.getSendStats(days = 14).unwrap("获取趋势失败").getOrNull()?.data
            }.getOrNull()

            val taskStats = runCatching {
                api.getTaskStats(days = 30).unwrap("获取任务排行失败").getOrNull()?.data
            }.getOrNull()

            val monitor = runCatching {
                api.getMonitor().unwrap("获取监控失败").getOrNull()?.data
            }.getOrNull()

            val host = monitor?.host
            val sched = monitor?.scheduler

            fun fmtPct(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.1f%%", it) }

            Result.success(
                PanelDashboard(
                    totalTasks = stats?.tasks?.let { if (it > Int.MAX_VALUE) null else it.toInt() },
                    todayRuns = stats?.today_execs,
                    totalEnvs = stats?.envs,
                    totalLogs = stats?.logs,
                    scheduledCount = stats?.scheduled ?: sched?.scheduled,
                    runningCount = stats?.running ?: sched?.running,
                    trend = trend.orEmpty().map {
                        TrendPoint(
                            date = it.day ?: "",
                            total = it.total ?: 0,
                            success = it.success ?: 0,
                            fail = it.failed ?: 0
                        )
                    },
                    // 白虎没有成功/失败拆分，只给总次数
                    topByCount = taskStats.orEmpty()
                        .sortedByDescending { it.count ?: 0 }
                        .take(5)
                        .mapIndexed { idx, item ->
                            val resolvedName = when {
                                !item.task_name.isNullOrBlank() && item.task_name != "未知任务" && item.task_name != "null" -> item.task_name
                                !item.name.isNullOrBlank() && item.name != "未知任务" && item.name != "null" -> item.name
                                !item.title.isNullOrBlank() -> item.title
                                !item.command.isNullOrBlank() -> {
                                    val parts = item.command.trim().split("\\s+".toRegex())
                                    parts.lastOrNull { it.contains(".") } ?: item.command.trim()
                                }
                                !item.task_id.isNullOrBlank() -> "任务 #${item.task_id.take(8)}"
                                !item.id.isNullOrBlank() -> "任务 #${item.id.take(8)}"
                                else -> "调度任务 #${idx + 1}"
                            }
                            TaskRank(
                                rank = idx + 1,
                                name = resolvedName,
                                value = "${item.count ?: 0} 次"
                            )
                        },
                    cpuUsage = fmtPct(host?.cpu_percent),
                    memUsage = fmtPct(host?.mem_percent),
                    resourceDetail = buildMap {
                        host?.platform?.let { put("系统", it) }
                        host?.uptime?.let { put("运行时长", BaihuDashboardHelpers.formatUptime(it)) }
                        fmtPct(host?.disk_percent)?.let { put("磁盘占用", it) }
                        sched?.worker_count?.let { put("Worker 数", "$it") }
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- 9. 审计日志

    override suspend fun getLoginLogs(): Result<List<Map<String, Any>>> {
        ensureAuth()
        return try {
            api.getLoginLogsHelper()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLogsTree(): Result<com.google.gson.JsonElement> {
        ensureAuth()
        return try {
            api.getLogsTreeHelper()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLogDetail(path: String, file: String): Result<String> {
        ensureAuth()
        val logId = file.ifEmpty { path }
        if (logId.isEmpty()) return Result.failure(Exception("日志 ID 为空"))
        return try {
            api.getLogDetail(logId)
                .unwrapTo("获取日志详情失败") { d: BaihuLogDetailResp ->
                    val out = d.data?.output ?: ""
                    val err = d.data?.error ?: ""
                    if (!out.isNullOrBlank()) out else if (!err.isNullOrBlank()) err else "暂无日志内容"
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- 工具

}
