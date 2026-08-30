package com.panel.app.data.adapter

import com.panel.app.data.model.*
import com.panel.app.data.remote.NetworkClient
import com.panel.app.data.remote.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class QinglongV10Adapter(
    override val instance: PanelInstance
) : IPanelAdapter {

    private val api: QinglongV10Api = NetworkClient.buildRetrofit(instance.baseUrl).create(QinglongV10Api::class.java)
    private var currentToken: String? = instance.token

    private fun getAuthHeader(): String {
        val t = currentToken ?: instance.token ?: ""
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    override suspend fun authenticate(): Result<String> {
        return try {
            if (!instance.token.isNullOrEmpty()) {
                currentToken = instance.token
                return Result.success(instance.token)
            }
            val username = instance.username ?: "admin"
            val password = instance.password ?: ""
            val resp = api.login(QlV10LoginReq(username, password))
            if (resp.isSuccessful && resp.body()?.data?.token != null) {
                val token = resp.body()!!.data!!.token!!
                currentToken = token
                Result.success(token)
            } else {
                Result.failure(Exception("青龙 v2.10 登录认证失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 1. 定时任务 (Tasks)
    override suspend fun getTasks(query: String?): Result<List<UnifiedTask>> {
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
                    UnifiedTask(
                        id = item.id?.toString() ?: "",
                        name = item.name,
                        command = item.command,
                        schedule = item.schedule,
                        statusText = if (isRun) "运行中" else "就绪",
                        isRunning = isRun,
                        isDisabled = item.isDisabled == 1
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("获取任务失败: HTTP ${resp.code()}"))
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
            val resp = api.updateCron(getAuthHeader(), QlUpdateCronReq(task.id, task.name, task.command, task.schedule))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("更新任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun runTask(taskIds: List<String>): Result<Boolean> {
        return try {
            val resp = api.runCrons(getAuthHeader(), taskIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("运行任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stopTask(taskIds: List<String>): Result<Boolean> {
        return try {
            val resp = api.stopCrons(getAuthHeader(), taskIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("停止任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleTask(taskId: String, enable: Boolean): Result<Boolean> {
        return try {
            val resp = if (enable) api.enableCrons(getAuthHeader(), listOf(taskId)) else api.disableCrons(getAuthHeader(), listOf(taskId))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("切换任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(taskIds: List<String>): Result<Boolean> {
        return try {
            val resp = api.deleteCrons(getAuthHeader(), taskIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("删除任务失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pinTask(taskIds: List<String>, pin: Boolean): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun getTaskInstances(taskId: String): Result<List<TaskInstanceRecord>> = Result.success(emptyList())

    override suspend fun getTaskLog(taskNameOrId: String): Result<String> {
        return try {
            val resp = api.getCronLog(getAuthHeader(), taskNameOrId)
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
                Result.failure(Exception("读取日志失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. 订阅管理 (Qinglong v2.10 旧版无独立订阅模块)
    override suspend fun getSubscriptions(query: String?): Result<List<UnifiedSubscription>> = Result.success(emptyList())
    override suspend fun createSubscription(sub: UnifiedSubscription): Result<Boolean> = Result.success(true)
    override suspend fun updateSubscription(sub: UnifiedSubscription): Result<Boolean> = Result.success(true)
    override suspend fun deleteSubscription(subIds: List<String>): Result<Boolean> = Result.success(true)
    override suspend fun runSubscription(subIds: List<String>): Result<Boolean> = Result.success(true)
    override suspend fun stopSubscription(subIds: List<String>): Result<Boolean> = Result.success(true)
    override suspend fun getSubscriptionLog(subId: String): Result<String> = Result.success("青龙旧版不支持单独订阅日志")

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
            val resp = if (env.id.isNotEmpty() && env.id.length > 5) {
                api.updateEnv(getAuthHeader(), QlUpdateEnvReq(env.id, env.name, env.value, env.remarks))
            } else {
                api.createEnvs(getAuthHeader(), listOf(QlCreateEnvReq(env.name, env.value, env.remarks)))
            }
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("保存环境变量失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleEnv(envId: String, enable: Boolean): Result<Boolean> {
        return try {
            val resp = if (enable) api.enableEnvs(getAuthHeader(), listOf(envId)) else api.disableEnvs(getAuthHeader(), listOf(envId))
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("切换变量状态失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEnv(envIds: List<String>): Result<Boolean> {
        return try {
            val resp = api.deleteEnvs(getAuthHeader(), envIds)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("删除环境变量失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. 配置文件 (Configs)
    override suspend fun getConfigFiles(): Result<List<String>> = Result.success(listOf("config.sh"))

    override suspend fun readConfig(path: String): Result<String> {
        return try {
            val resp = api.getConfig(getAuthHeader(), path)
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
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("保存配置失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 5. 脚本文件 (Scripts)
    private fun mapQlScriptNode(item: QlScriptNodeItem): ScriptNode {
        val nodeName = item.title ?: item.name ?: item.value ?: item.key ?: "未命名"
        val nodePath = item.value ?: item.key ?: item.title ?: nodeName
        val scriptExtensions = setOf("js", "py", "sh", "ts", "json", "txt", "yml", "yaml", "sql", "conf", "env")
        val ext = nodeName.substringAfterLast('.', "").lowercase()
        val isFileByExt = ext.isNotEmpty() && ext in scriptExtensions
        val isDirectory = if (isFileByExt) {
            false
        } else {
            !item.children.isNullOrEmpty() || (item.isLeaf != null && item.isLeaf == false) || (!nodeName.contains(".") && item.children != null)
        }
        return ScriptNode(
            name = nodeName,
            path = nodePath,
            isDir = isDirectory,
            size = if (isDirectory) null else "-",
            children = item.children?.map { mapQlScriptNode(it) }
        )
    }

    override suspend fun getScriptTree(): Result<List<ScriptNode>> {
        return try {
            val resp = api.getScripts(getAuthHeader())
            if (resp.isSuccessful && resp.body()?.data != null) {
                val list = resp.body()!!.data!!.map { mapQlScriptNode(it) }
                Result.success(list)
            } else {
                Result.failure(Exception("获取脚本文件树失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readScript(path: String): Result<String> {
        return try {
            val resp = api.getScriptContent(getAuthHeader(), path)
            if (resp.isSuccessful && resp.body()?.data != null) {
                Result.success(resp.body()!!.data!!)
            } else {
                Result.failure(Exception("读取脚本失败: HTTP ${resp.code()}"))
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

    override suspend fun createDirectory(path: String): Result<Boolean> = Result.success(true)
    override suspend fun deleteScript(path: String): Result<Boolean> = Result.success(true)

    // 6. 依赖包管理 (Dependencies)
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
                    UnifiedDep(
                        id = d.id?.toString() ?: "",
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

    override suspend fun deleteDep(depId: String, type: String): Result<Boolean> {
        return try {
            val idList = listOf(depId.toIntOrNull() ?: depId)
            val resp = api.deleteDependencies(getAuthHeader(), idList)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("删除依赖失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun batchDeleteDeps(depIds: List<String>): Result<Boolean> {
        return try {
            val idList = depIds.map { it.toIntOrNull() ?: it }
            val resp = api.deleteDependencies(getAuthHeader(), idList)
            if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("批量删除依赖失败: HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forceDeleteDeps(depIds: List<String>): Result<Boolean> = batchDeleteDeps(depIds)

    override suspend fun getDepLog(depId: String): Result<String> = Result.success("安装日志暂无")

    override fun streamLog(logId: String): Flow<String> = flow {
        emit("[INFO] 正在连接青龙 v2.10 日志服务...\n")
    }

    override suspend fun getMetrics(): Result<Pair<String, String>> = Result.success(Pair("--", "--"))

    override suspend fun getLoginLogs(): Result<List<Map<String, Any>>> = Result.success(emptyList())

    override suspend fun getLogsTree(): Result<com.google.gson.JsonElement> = Result.success(com.google.gson.JsonArray())

    override suspend fun getLogDetail(path: String, file: String): Result<String> = Result.success("暂无日志输出")
}
