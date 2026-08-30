package com.panel.app.data.remote.api

import retrofit2.Response
import retrofit2.http.*

data class BaihuLoginReq(val username: String, val password: String)
data class BaihuLoginResp(val code: Int?, val msg: String?, val message: String?, val data: Any?)

data class BaihuCommonResp(val code: Int?, val msg: String?, val message: String?)

data class BaihuPaginationData<T>(
    val data: List<T>?,
    val total: Long?,
    val page: Int?,
    val page_size: Int?
)

data class BaihuRepoConfig(
    val repo_url: String? = null,
    val branch: String? = null,
    val sparse_path: String? = null,
    val whitelist_paths: String? = null,
    val blacklist: String? = null,
    val auto_add_cron: Boolean? = true
)

data class BaihuTaskItem(
    val id: String,
    val name: String,
    val command: String? = null,
    val schedule: String? = null,
    val running_status: String? = null,
    val enabled: Boolean? = true,
    val timeout: Int? = 30,
    val remark: String? = null,
    val type: String? = "task",
    val config: com.google.gson.JsonElement? = null,
    val languages: com.google.gson.JsonElement? = null,
    val agent_id: String? = null,
    val last_run: String? = null,
    val next_run: String? = null,
    val pin_type: String? = "none"
)

data class BaihuTasksResp(
    val code: Int?,
    val msg: String?,
    val data: BaihuPaginationData<BaihuTaskItem>?
)

data class BaihuCreateTaskReq(
    val name: String,
    val command: String? = null,
    val schedule: String,
    val timeout: Int = 30,
    val type: String = "task",
    val config: String? = null
)

data class BaihuUpdateTaskReq(
    val name: String? = null,
    val command: String? = null,
    val schedule: String? = null,
    val enabled: Boolean? = null,
    val pin_type: String? = null
)

data class BaihuBatchDeleteTasksReq(
    val ids: List<String>
)

data class BaihuEnvItem(
    val id: String,
    val name: String,
    val value: String,
    val remark: String?,
    val enabled: Boolean?
)

data class BaihuEnvsResp(
    val code: Int?,
    val msg: String?,
    val data: BaihuPaginationData<BaihuEnvItem>?
)

data class BaihuCreateEnvReq(
    val name: String,
    val value: String,
    val remark: String? = null,
    val type: String = "normal",
    val enabled: Boolean = true
)

data class BaihuDepItem(
    val id: String,
    val name: String,
    val version: String?,
    val language: String,
    val remark: String?,
    val status: Int? = 1,
    val log: String? = null
)

data class BaihuDepsResp(
    val code: Int?,
    val msg: String?,
    val data: List<BaihuDepItem>?
)

data class BaihuCreateDepReq(
    val name: String,
    val version: String? = null,
    val language: String,
    val remark: String? = null
)

data class BaihuFileNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val children: List<BaihuFileNode>? = null,
    val modTime: Long? = null
)
data class BaihuFileTreeResp(val code: Int?, val msg: String?, val data: List<BaihuFileNode>?)
data class BaihuFileContentReq(val path: String, val content: String)
data class BaihuFileCreateReq(val path: String, val isDir: Boolean = false)
data class BaihuFileDeleteReq(val path: String)
data class BaihuFileContentData(val path: String?, val content: String?)
data class BaihuFileContentResp(val code: Int?, val msg: String?, val data: BaihuFileContentData?)

data class BaihuLogItem(
    val id: String,
    val task_id: String,
    val task_name: String,
    val command: String,
    val start_time: String?,
    val end_time: String?,
    val duration: String?,
    val status: String?,
    val exit_code: Int?
)

data class BaihuLogsResp(
    val code: Int?,
    val msg: String?,
    val data: BaihuPaginationData<BaihuLogItem>?
)

data class BaihuLogDetail(
    val id: String?,
    val task_id: String?,
    val command: String?,
    val output: String?,
    val error: String?,
    val status: String?
)

data class BaihuLogDetailResp(
    val code: Int?,
    val msg: String?,
    val data: BaihuLogDetail?
)

data class BaihuCreateScriptReq(
    val name: String,
    val content: String
)

data class BaihuMonitorHost(
    val cpu_percent: Double?,
    val mem_percent: Double?,
    val mem_total: Long?,
    val mem_used: Long?
)

data class BaihuMonitorData(
    val host: BaihuMonitorHost?
)

data class BaihuMonitorResp(
    val code: Int?,
    val msg: String?,
    val data: BaihuMonitorData?
)

interface BaihuApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body req: BaihuLoginReq): Response<BaihuLoginResp>

    @GET("api/v1/auth/me")
    suspend fun checkAuth(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null
    ): Response<Any>

    // 1. 定时任务 (Tasks)
    @GET("api/v1/tasks")
    suspend fun getTasks(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Query("name") name: String? = null,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100
    ): Response<BaihuTasksResp>

    @POST("api/v1/tasks")
    suspend fun createTask(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body task: BaihuCreateTaskReq
    ): Response<BaihuCommonResp>

    @PUT("api/v1/tasks/{id}")
    suspend fun updateTask(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String,
        @Body req: BaihuUpdateTaskReq
    ): Response<BaihuCommonResp>

    @POST("api/v1/execute/task/{id}")
    suspend fun runTask(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String
    ): Response<BaihuCommonResp>

    @POST("api/v1/tasks/stop/{logID}")
    suspend fun stopTask(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("logID") logID: String
    ): Response<BaihuCommonResp>

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String
    ): Response<BaihuCommonResp>

    @POST("api/v1/tasks/batch-delete")
    suspend fun batchDeleteTasks(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body req: BaihuBatchDeleteTasksReq
    ): Response<BaihuCommonResp>

    // 2. 环境变量 (Env)
    @GET("api/v1/env")
    suspend fun getEnvs(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Query("name") name: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100
    ): Response<BaihuEnvsResp>

    @POST("api/v1/env")
    suspend fun createEnv(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body env: BaihuCreateEnvReq
    ): Response<BaihuCommonResp>

    @PUT("api/v1/env/{id}")
    suspend fun updateEnv(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String,
        @Body env: BaihuCreateEnvReq
    ): Response<BaihuCommonResp>

    @DELETE("api/v1/env/{id}")
    suspend fun deleteEnv(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String
    ): Response<BaihuCommonResp>

    // 3. 依赖管理 (真实后端路径为 /api/v1/deps)
    @GET("api/v1/deps")
    suspend fun getDependencies(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Query("language") language: String? = null
    ): Response<BaihuDepsResp>

    @POST("api/v1/deps")
    suspend fun installDependency(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body dep: BaihuCreateDepReq
    ): Response<BaihuCommonResp>

    @POST("api/v1/deps/uninstall/{id}")
    suspend fun uninstallDependency(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String
    ): Response<BaihuCommonResp>

    @DELETE("api/v1/deps/{id}")
    suspend fun deleteDependency(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String
    ): Response<BaihuCommonResp>

    // 4. 文件与配置 (Files)
    @GET("api/v1/files/tree")
    suspend fun getFileTree(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null
    ): Response<BaihuFileTreeResp>

    @GET("api/v1/files/content")
    suspend fun getFileContent(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Query("path") path: String
    ): Response<BaihuFileContentResp>

    @POST("api/v1/files/content")
    suspend fun saveFileContent(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body req: BaihuFileContentReq
    ): Response<BaihuCommonResp>

    @POST("api/v1/files/create")
    suspend fun createFile(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body req: BaihuFileCreateReq
    ): Response<BaihuCommonResp>

    @POST("api/v1/files/delete")
    suspend fun deleteFile(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body req: BaihuFileDeleteReq
    ): Response<BaihuCommonResp>

    // 5. 日志查询 (Logs)
    @GET("api/v1/logs")
    suspend fun getLogs(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Query("task_id") taskId: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): Response<BaihuLogsResp>

    @GET("api/v1/logs/{id}")
    suspend fun getLogDetail(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Path("id") id: String
    ): Response<BaihuLogDetailResp>

    // 6. 监控指标 (Monitor)
    @GET("api/v1/monitor")
    suspend fun getMonitor(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null
    ): Response<BaihuMonitorResp>

    // 7. 脚本管理
    @POST("api/v1/scripts")
    suspend fun createScript(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Body req: BaihuCreateScriptReq
    ): Response<BaihuCommonResp>

    // 8. 登录审计日志 (Settings)
    @GET("api/v1/settings/loginlogs")
    suspend fun getLoginLogs(
        @Header("Authorization") auth: String,
        @Header("Cookie") cookie: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50
    ): Response<com.google.gson.JsonElement>
}
