package com.panel.app.data.remote.api

import com.panel.app.data.remote.ApiEnvelope
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 白虎面板 API 定义。
 *
 * **契约来源**：`baihu-panel/web/src/api/index.ts`（官方前端，唯一权威来源）
 * 后端路由：`baihu-panel/internal/router/api_routes.go`
 *
 * 注意：白虎的业务错误用 HTTP 200 + body.code 返回，
 * 所有响应都通过 [com.panel.app.data.remote.unwrap] 解包，不要直接用 isSuccessful 判断。
 */

data class BaihuLoginReq(val username: String, val password: String)
data class BaihuOtpLoginReq(val otp_pending_token: String, val code: String)

/** 登录响应：token 只通过 Set-Cookie: BHToken 下发，body 里没有 */
data class BaihuLoginData(
    val user: String? = null,
    val require_otp: Boolean? = null,
    val otp_pending_token: String? = null
)

data class BaihuLoginResp(
    override val code: Int?,
    override val msg: String?,
    override val message: String? = null,
    val data: BaihuLoginData? = null
) : ApiEnvelope

data class BaihuMeResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuMeData? = null
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuMeData(val username: String?, val role: String?)

data class BaihuCommonResp(
    override val code: Int?,
    override val msg: String?,
    override val message: String? = null,
    val data: Any? = null
) : ApiEnvelope

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
    val name: String? = null,
    val command: String? = null,
    val pre_command: String? = null,
    val post_command: String? = null,
    val schedule: String? = null,
    val remark: String? = null,
    val tags: String? = null,
    val type: String? = "task",
    val trigger_type: String? = null,
    val config: String? = null,
    val timeout: Int? = 30,
    val work_dir: String? = null,
    val envs: String? = null,
    val languages: com.google.gson.JsonElement? = null,
    val agent_id: String? = null,
    val retry_count: Int? = null,
    val retry_interval: Int? = null,
    val random_range: Int? = null,
    val clean_config: String? = null,
    val enabled: Boolean? = true,
    val pin_type: String? = "none",
    val running_status: String? = null,
    val last_run: String? = null,
    val next_run: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class BaihuTasksResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuPaginationData<BaihuTaskItem>?
) : ApiEnvelope {
    override val message: String? get() = null
}

/**
 * 创建任务请求。字段对齐 `vo.TaskCreateReq`（internal/models/vo/task_vo.go）。
 * 注意：仓库任务 type="repo" 时 command 可为空，但必须传 config。
 */
data class BaihuCreateTaskReq(
    val name: String,
    val remark: String? = null,
    val command: String? = null,
    val preCommand: String? = null,
    val postCommand: String? = null,
    val tags: String? = null,
    val type: String = "task",
    val config: String? = null,
    val schedule: String,
    val timeout: Int = 30,
    val workDir: String? = null,
    val envs: String? = null,
    val languages: com.google.gson.JsonElement? = null,
    val agentId: String? = null,
    val triggerType: String? = null,
    val retryCount: Int? = null,
    val retryInterval: Int? = null,
    val randomRange: Int? = null,
    val pinType: String? = null
)

/** 字段对齐 `vo.TaskUpdateReq` */
data class BaihuUpdateTaskReq(
    val name: String? = null,
    val remark: String? = null,
    val command: String? = null,
    val pre_command: String? = null,
    val post_command: String? = null,
    val tags: String? = null,
    val type: String? = null,
    val config: String? = null,
    val schedule: String? = null,
    val timeout: Int? = null,
    val work_dir: String? = null,
    val envs: String? = null,
    val enabled: Boolean? = null,
    val languages: com.google.gson.JsonElement? = null,
    val agent_id: String? = null,
    val trigger_type: String? = null,
    val retry_count: Int? = null,
    val retry_interval: Int? = null,
    val random_range: Int? = null,
    val pin_type: String? = null
)

data class BaihuBatchDeleteTasksReq(val ids: List<String>)

data class BaihuBatchDeleteTasksByQueryReq(
    val name: String? = null,
    val agent_id: String? = null,
    val tags: String? = null,
    val type: String? = null
)

data class BaihuEnvItem(
    val id: String,
    val name: String,
    val value: String,
    val remark: String? = null,
    val type: String? = "normal",
    val tags: String? = null,
    val enabled: Boolean? = null
)

data class BaihuEnvsResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuPaginationData<BaihuEnvItem>?
) : ApiEnvelope {
    override val message: String? get() = null
}

/** 对齐 `vo.EnvCreateReq` */
data class BaihuCreateEnvReq(
    val name: String,
    val value: String,
    val remark: String? = null,
    val type: String = "normal",
    val tags: String? = null,
    val enabled: Boolean = true
)

/**
 * 依赖记录。对齐 `vo.DependencyVO`（internal/models/vo/dep_vo.go）。
 * 注意：后端**没有 status 字段**，不要伪造安装状态。
 */
data class BaihuDepItem(
    val id: String,
    val name: String,
    val version: String? = null,
    val language: String,
    val lang_version: String? = null,
    val remark: String? = null,
    val log: String? = null
)

data class BaihuDepsResp(
    override val code: Int?,
    override val msg: String?,
    val data: List<BaihuDepItem>?
) : ApiEnvelope {
    override val message: String? get() = null
}

/** 依赖安装/创建请求，对齐 dependency_controller 的接收结构 */
data class BaihuCreateDepReq(
    val name: String,
    val version: String? = null,
    val language: String,
    val langVersion: String? = null,
    val remark: String? = null
)

/** POST /deps/import 的响应：解析出的依赖 + 合并后的安装命令 */
data class BaihuDepImportResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuDepImportData? = null
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuDepImportData(
    val dependencies: List<BaihuDepItem>? = null,
    val command: String? = null
)

data class BaihuDepImportReq(
    val language: String,
    val lang_version: String? = null,
    val content: String,
    val import_db: Boolean? = true
)

data class BaihuCommandResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuCommandData? = null
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuCommandData(val command: String? = null)

data class BaihuFileNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val modTime: Long? = null,
    val children: List<BaihuFileNode>? = null
)

data class BaihuFileTreeResp(
    override val code: Int?,
    override val msg: String?,
    val data: List<BaihuFileNode>?
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuFileContentReq(val path: String, val content: String)
data class BaihuFileCreateReq(val path: String, val isDir: Boolean = false)
data class BaihuFileDeleteReq(val path: String)
data class BaihuFileRenameReq(val oldPath: String, val newPath: String)
data class BaihuFileMoveReq(val oldPath: String, val newPath: String)
data class BaihuFileCopyReq(val sourcePath: String, val targetPath: String)

data class BaihuFileContentData(
    val path: String? = null,
    val content: String? = null,
    val isBinary: Boolean? = null
)

data class BaihuFileContentResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuFileContentData?
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuLogItem(
    val id: String,
    val task_id: String? = null,
    val task_name: String? = null,
    val task_type: String? = null,
    val agent_id: String? = null,
    val command: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val duration: Long? = null,
    val status: String? = null,
    val exit_code: Int? = null,
    val created_at: String? = null
)

data class BaihuLogsResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuPaginationData<BaihuLogItem>?
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuLogDetail(
    val id: String? = null,
    val task_id: String? = null,
    val task_name: String? = null,
    val command: String? = null,
    val output: String? = null,
    val error: String? = null,
    val status: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val duration: Long? = null,
    val exit_code: Int? = null
)

data class BaihuLogDetailResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuLogDetail?
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuTagsResp(
    override val code: Int?,
    override val msg: String?,
    val data: List<String>?
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuMonitorHost(
    val cpu_percent: Double? = null,
    val mem_percent: Double? = null,
    val mem_total: Long? = null,
    val mem_used: Long? = null,
    val disk_total: Long? = null,
    val disk_used: Long? = null,
    val disk_percent: Double? = null,
    val uptime: Long? = null,
    val platform: String? = null
)

data class BaihuMonitorWorker(
    val id: Int? = null,
    val status: String? = null,
    val task_id: String? = null,
    val task_name: String? = null,
    val start_time: Long? = null,
    val duration: Long? = null
)

/** 调度器状态：running 数量与 worker 明细（含 task_id，可用于停止任务） */
data class BaihuMonitorScheduler(
    val scheduled: Int? = null,
    val running: Int? = null,
    val queue_size: Int? = null,
    val worker_count: Int? = null,
    val workers: List<BaihuMonitorWorker>? = null
)

data class BaihuMonitorData(
    val host: BaihuMonitorHost? = null,
    val scheduler: BaihuMonitorScheduler? = null,
    val env: com.google.gson.JsonElement? = null
)

data class BaihuMonitorResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuMonitorData?
) : ApiEnvelope {
    override val message: String? get() = null
}

interface BaihuApi {
    // ---------------- 认证 ----------------
    @POST("api/v1/auth/login")
    suspend fun login(@Body req: BaihuLoginReq): Response<BaihuLoginResp>

    /** 两步验证登录：登录返回 require_otp=true 时必须走这一步才能拿到 Cookie */
    @POST("api/v1/auth/login/otp")
    suspend fun loginOtp(@Body req: BaihuOtpLoginReq): Response<BaihuLoginResp>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<BaihuCommonResp>

    @GET("api/v1/auth/me")
    suspend fun checkAuth(): Response<BaihuMeResp>

    // ---------------- 1. 定时任务 ----------------
    @GET("api/v1/tasks")
    suspend fun getTasks(
        @Query("name") name: String? = null,
        @Query("type") type: String? = null,
        @Query("tags") tags: String? = null,
        @Query("agent_id") agentId: String? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("order") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100
    ): Response<BaihuTasksResp>

    @POST("api/v1/tasks")
    suspend fun createTask(@Body task: BaihuCreateTaskReq): Response<BaihuCommonResp>

    @PUT("api/v1/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body req: BaihuUpdateTaskReq
    ): Response<BaihuCommonResp>

    @POST("api/v1/execute/task/{id}")
    suspend fun runTask(@Path("id") id: String): Response<BaihuCommonResp>

    /**
     * 停止任务。
     * **注意路径变量是 logID（运行日志 ID），不是任务 ID。**
     * 需要先从 /logs?task_id=&status=running 或 /monitor 的 scheduler.workers 取到。
     */
    @POST("api/v1/tasks/stop/{logID}")
    suspend fun stopTask(@Path("logID") logID: String): Response<BaihuCommonResp>

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(
        @Path("id") id: String,
        @Query("delete_files") deleteFiles: Boolean? = null
    ): Response<BaihuCommonResp>

    @POST("api/v1/tasks/batch-delete")
    suspend fun batchDeleteTasks(@Body req: BaihuBatchDeleteTasksReq): Response<BaihuCommonResp>

    /** 按查询条件批量删除任务（name/agent_id/tags/type 均可选，至少传一个） */
    @DELETE("api/v1/tasks/batch-by-query")
    suspend fun batchDeleteTaskByQuery(
        @Query("name") name: String? = null,
        @Query("agent_id") agentId: String? = null,
        @Query("tags") tags: String? = null,
        @Query("type") type: String? = null
    ): Response<BaihuCommonResp>

    @GET("api/v1/tasks/tags")
    suspend fun getTaskTags(): Response<BaihuTagsResp>

    @POST("api/v1/execute/command")
    suspend fun executeCommand(@Body req: Map<String, String>): Response<BaihuCommonResp>

    // ---------------- 2. 环境变量 ----------------
    @GET("api/v1/env")
    suspend fun getEnvs(
        @Query("name") name: String? = null,
        @Query("type") type: String? = null,
        @Query("tags") tags: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100
    ): Response<BaihuEnvsResp>

    @GET("api/v1/env/all")
    suspend fun getAllEnvs(): Response<BaihuEnvsResp>

    @POST("api/v1/env")
    suspend fun createEnv(@Body env: BaihuCreateEnvReq): Response<BaihuCommonResp>

    @PUT("api/v1/env/{id}")
    suspend fun updateEnv(
        @Path("id") id: String,
        @Body env: BaihuCreateEnvReq
    ): Response<BaihuCommonResp>

    /**
     * 删除环境变量。被任务引用时后端返回 **code 409** 并在 data 里带回关联任务列表，
     * 此时可带 force=true 强制删除（前端 DeleteEnvDialog 就是这么做的）。
     */
    @DELETE("api/v1/env/{id}")
    suspend fun deleteEnv(
        @Path("id") id: String,
        @Query("force") force: Boolean? = null
    ): Response<BaihuCommonResp>

    @GET("api/v1/env/tags")
    suspend fun getEnvTags(): Response<BaihuTagsResp>

    /** 批量保存/导入环境变量（按 id 或 name 匹配做 upsert） */
    @POST("api/v1/env/bulk_save")
    suspend fun bulkSaveEnvs(@Body reqs: List<BaihuBulkEnvReq>): Response<BaihuCommonResp>

    // ---------------- 3. 依赖管理 ----------------
    @GET("api/v1/deps")
    suspend fun getDependencies(
        @Query("language") language: String? = null,
        @Query("lang_version") langVersion: String? = null
    ): Response<BaihuDepsResp>

    /** 只登记依赖记录，**不会真正安装** */
    @POST("api/v1/deps")
    suspend fun createDependency(@Body dep: BaihuCreateDepReq): Response<BaihuCommonResp>

    /** 真正执行安装的接口 */
    @POST("api/v1/deps/install")
    suspend fun installDependency(@Body dep: BaihuCreateDepReq): Response<BaihuCommonResp>

    @POST("api/v1/deps/install-cmd")
    suspend fun getInstallCommand(@Body dep: BaihuCreateDepReq): Response<BaihuCommandResp>

    @DELETE("api/v1/deps/{id}")
    suspend fun deleteDependency(@Path("id") id: String): Response<BaihuCommonResp>

    @POST("api/v1/deps/uninstall/{id}")
    suspend fun uninstallDependency(
        @Path("id") id: String,
        @Query("force") force: Boolean? = null
    ): Response<BaihuCommonResp>

    @POST("api/v1/deps/reinstall/{id}")
    suspend fun reinstallDependency(@Path("id") id: String): Response<BaihuCommonResp>

    @POST("api/v1/deps/reinstall-all")
    suspend fun reinstallAllDependencies(
        @Query("language") language: String,
        @Query("lang_version") langVersion: String? = null
    ): Response<BaihuCommonResp>

    @POST("api/v1/deps/import")
    suspend fun importDependencies(@Body req: BaihuDepImportReq): Response<BaihuDepImportResp>

    @GET("api/v1/deps/installed")
    suspend fun getInstalledDependencies(
        @Query("language") language: String,
        @Query("lang_version") langVersion: String? = null
    ): Response<BaihuDepsResp>

    // ---------------- 4. 文件与配置 ----------------
    @GET("api/v1/files/tree")
    suspend fun getFileTree(): Response<BaihuFileTreeResp>

    @GET("api/v1/files/content")
    suspend fun getFileContent(@Query("path") path: String): Response<BaihuFileContentResp>

    @POST("api/v1/files/content")
    suspend fun saveFileContent(@Body req: BaihuFileContentReq): Response<BaihuCommonResp>

    @POST("api/v1/files/create")
    suspend fun createFile(@Body req: BaihuFileCreateReq): Response<BaihuCommonResp>

    @POST("api/v1/files/delete")
    suspend fun deleteFile(@Body req: BaihuFileDeleteReq): Response<BaihuCommonResp>

    @POST("api/v1/files/rename")
    suspend fun renameFile(@Body req: BaihuFileRenameReq): Response<BaihuCommonResp>

    @POST("api/v1/files/move")
    suspend fun moveFile(@Body req: BaihuFileMoveReq): Response<BaihuCommonResp>

    @POST("api/v1/files/copy")
    suspend fun copyFile(@Body req: BaihuFileCopyReq): Response<BaihuCommonResp>

    // ---------------- 5. 日志查询 ----------------
    @GET("api/v1/logs")
    suspend fun getLogs(
        @Query("task_id") taskId: String? = null,
        @Query("task_name") taskName: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): Response<BaihuLogsResp>

    @GET("api/v1/logs/{id}")
    suspend fun getLogDetail(@Path("id") id: String): Response<BaihuLogDetailResp>

    /**
     * SSE 流式实时日志接口。
     * 返回 application/x-ndjson 流，每行是一条 JSON 事件。
     * UI 层通过 OkHttp EventSource 消费此流。
     */
    @GET("api/v1/logs/sse")
    suspend fun getLogSSE(
        @Query("log_id") logId: String,
        @Query("tail") tail: Int = 0
    ): Response<ResponseBody>

    @DELETE("api/v1/logs/{id}")
    suspend fun deleteLog(@Path("id") id: String): Response<BaihuCommonResp>

    @POST("api/v1/logs/clear")
    suspend fun clearLogs(@Body req: BaihuClearLogsReq): Response<BaihuCommonResp>

    // ---------------- 6. 监控与统计 ----------------
    @GET("api/v1/monitor")
    suspend fun getMonitor(): Response<BaihuMonitorResp>

    /** 首页概览：任务数 / 今日执行 / 变量数 / 日志数 / 调度数 / 运行数 */
    @GET("api/v1/stats")
    suspend fun getStats(): Response<BaihuStatsResp>

    /** 每日执行趋势（默认 30 天，最多 90） */
    @GET("api/v1/sendstats")
    suspend fun getSendStats(@Query("days") days: Int = 30): Response<BaihuSendStatsResp>

    /** 任务执行次数排行（默认 30 天） */
    @GET("api/v1/taskstats")
    suspend fun getTaskStats(@Query("days") days: Int = 30): Response<BaihuTaskStatsResp>

    // ---------------- 7. 脚本管理 ----------------
    @GET("api/v1/scripts")
    suspend fun getScripts(): Response<BaihuScriptsResp>

    @POST("api/v1/scripts")
    suspend fun createScript(@Body req: BaihuScriptReq): Response<BaihuCommonResp>

    @PUT("api/v1/scripts/{id}")
    suspend fun updateScript(
        @Path("id") id: String,
        @Body req: BaihuScriptReq
    ): Response<BaihuCommonResp>

    @DELETE("api/v1/scripts/{id}")
    suspend fun deleteScript(@Path("id") id: String): Response<BaihuCommonResp>

    // ---------------- 8. 登录审计日志 ----------------
    @GET("api/v1/settings/loginlogs")
    suspend fun getLoginLogs(
        @Query("username") username: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50
    ): Response<BaihuLoginLogsResp>
}

data class BaihuClearLogsReq(val taskId: String? = null)

data class BaihuScriptItem(
    val id: String,
    val name: String,
    val content: String? = null,
    val remark: String? = null,
    val type: String? = null
)

data class BaihuScriptsResp(
    override val code: Int?,
    override val msg: String?,
    val data: List<BaihuScriptItem>?
) : ApiEnvelope {
    override val message: String? get() = null
}

data class BaihuScriptReq(
    val name: String,
    val content: String? = null,
    val remark: String? = null,
    val type: String? = null
)

data class BaihuLoginLogItem(
    val id: String? = null,
    val username: String? = null,
    val ip: String? = null,
    val status: String? = null,
    val message: String? = null,
    val user_agent: String? = null,
    val created_at: String? = null
)

// ---------------- 统计（对齐 internal/controllers/dashboard_controller.go） ----------------

/** GET /stats 响应体 */
data class BaihuStatsData(
    val tasks: Long? = null,
    val today_execs: Long? = null,
    val envs: Long? = null,
    val logs: Long? = null,
    val scheduled: Int? = null,
    val running: Int? = null
)

data class BaihuStatsResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuStatsData?
) : ApiEnvelope {
    override val message: String? get() = null
}

/** GET /sendstats 单日聚合 */
data class BaihuDailyStats(
    val day: String? = null,
    val total: Int? = null,
    val success: Int? = null,
    val failed: Int? = null
)

data class BaihuSendStatsResp(
    override val code: Int?,
    override val msg: String?,
    val data: List<BaihuDailyStats>?
) : ApiEnvelope {
    override val message: String? get() = null
}

/** GET /taskstats 单任务执行次数 */
data class BaihuTaskStatsItem(
    val task_id: String? = null,
    val id: String? = null,
    val task_name: String? = null,
    val name: String? = null,
    val title: String? = null,
    val command: String? = null,
    val count: Int? = null
)

data class BaihuTaskStatsResp(
    override val code: Int?,
    override val msg: String?,
    val data: List<BaihuTaskStatsItem>?
) : ApiEnvelope {
    override val message: String? get() = null
}

/** POST /env/bulk_save 请求体（也用于批量导入） */
data class BaihuBulkEnvReq(
    val id: String? = null,
    val name: String,
    val value: String,
    val remark: String? = null,
    val type: String? = null,
    val hidden: Boolean? = null,
    val enabled: Boolean? = null
)

data class BaihuLoginLogsResp(
    override val code: Int?,
    override val msg: String?,
    val data: BaihuPaginationData<BaihuLoginLogItem>?
) : ApiEnvelope {
    override val message: String? get() = null
}
