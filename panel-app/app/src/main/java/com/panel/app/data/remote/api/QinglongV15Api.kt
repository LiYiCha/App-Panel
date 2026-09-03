package com.panel.app.data.remote.api

import com.panel.app.data.remote.ApiEnvelope
import retrofit2.Response
import retrofit2.http.*

/**
 * 青龙面板 2.10+ API 定义。
 *
 * **契约来源**：`qinglong/back/api` 目录下的 express 路由 + `celebrate(Joi)` 校验规则。
 * Joi schema 就是参数白名单，比看前端页面更准确。
 *
 * 注意：青龙的业务错误同样用 HTTP 200 + `{"code":4xx,"message":"..."}` 返回
 *（官方前端 `src/utils/http.tsx` 里 `if (res.code !== 200) notification.error(res.message)`），
 * 因此所有响应都要经 [com.panel.app.data.remote.unwrap] 解包。
 */

// ---------------------------------------------------------------- 认证

data class QlTokenResp(
    override val code: Int?,
    override val message: String?,
    val data: QlTokenData?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlTokenData(
    val token: String?,
    val token_type: String?,
    val expiration: Long?
)

// ---------------------------------------------------------------- 任务

data class QlCronsResp(
    override val code: Int?,
    override val message: String?,
    val data: com.google.gson.JsonElement?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlCronItem(
    val id: Any?,
    val name: String?,
    val command: String?,
    val schedule: String?,
    val status: Int?,
    val isDisabled: Int?,
    val isPinned: Int? = null,
    val extra_schedules: Any? = null,
    val labels: List<String>? = null,
    val sub_id: Any? = null,
    val task_before: String? = null,
    val task_after: String? = null,
    val log_name: String? = null,
    val work_dir: String? = null,
    val allow_multiple_instances: Int? = null,
    val last_running_time: Long? = null,
    val last_execution_time: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val pid: Int? = null,
    val log_path: String? = null
)

/**
 * 创建任务请求。字段对齐 `back/validation/schedule.ts` 的 commonCronSchema。
 * 只有 command 与 schedule 是必填，其余留空时由 Gson 正常序列化为 null。
 */
data class QlCreateCronReq(
    val name: String? = null,
    val command: String,
    val schedule: String,
    val labels: List<String>? = null,
    val sub_id: Any? = null,
    val extra_schedules: List<Any>? = null,
    val task_before: String? = null,
    val task_after: String? = null,
    val log_name: String? = null,
    val work_dir: String? = null,
    val allow_multiple_instances: Int? = null
)

/** 更新任务请求：commonCronSchema + id */
data class QlUpdateCronReq(
    val id: Any,
    val name: String? = null,
    val command: String,
    val schedule: String,
    val labels: List<String>? = null,
    val sub_id: Any? = null,
    val extra_schedules: List<Any>? = null,
    val task_before: String? = null,
    val task_after: String? = null,
    val log_name: String? = null,
    val work_dir: String? = null,
    val allow_multiple_instances: Int? = null
)

data class QlLabelBatchReq(
    val ids: List<Long>,
    val labels: List<String>
)

data class QlCronStatusReq(
    val ids: List<Long>,
    val status: String,
    val pid: String? = null,
    val log_path: String? = null,
    val last_running_time: Long? = null,
    val last_execution_time: Long? = null,
    val exit_code: Int? = null
)

// ---------------------------------------------------------------- 运行实例 / 日志

data class QlCronInstanceItem(
    val id: Any?,
    val cron_id: Any?,
    val log_path: String?,
    val started_at: Long?,
    val finished_at: Long?,
    val created_at: String?,
    val updated_at: String?,
    val duration: Long?,
    val status: Int?,
    val exit_code: Int?,
    val pid: Int? = null
)

data class QlCronInstancesResp(
    override val code: Int?,
    override val message: String?,
    val data: List<QlCronInstanceItem>?
) : ApiEnvelope {
    override val msg: String? get() = null
}

/**
 * 分页日志响应。
 * 后端返回 `{code, data:内容, logStatus, offset, nextOffset, total, truncated}`：
 * - `logStatus == "running"` 表示任务仍在输出，UI 可据此显示"跟随中"
 * - `truncated == true` 表示内容被截断，必须提示用户，否则会误以为看到了完整日志
 * - 未传 offset/limit 时后端默认读**末尾 256KB**（tail 语义）
 */
data class QlLogChunkResp(
    override val code: Int?,
    override val message: String?,
    val data: String?,
    val logStatus: String? = null,
    val offset: Long? = null,
    val nextOffset: Long? = null,
    val total: Long? = null,
    val truncated: Boolean? = null
) : ApiEnvelope {
    override val msg: String? get() = null
}

// ---------------------------------------------------------------- 订阅

data class QlSubscriptionsResp(
    override val code: Int?,
    override val message: String?,
    val data: com.google.gson.JsonElement?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlSubscriptionItem(
    val id: Any?,
    val name: String?,
    val type: String?,
    val url: String?,
    val branch: String?,
    val schedule: String?,
    val schedule_type: String? = null,
    val interval_schedule: com.google.gson.JsonElement? = null,
    val whitelist: String?,
    val blacklist: String?,
    val dependences: String?,
    val extensions: String?,
    val alias: String?,
    val autoAddCron: Any?,
    val autoDelCron: Any?,
    val status: Int?,
    val pull_type: String? = null,
    val proxy: String? = null,
    val sub_before: String? = null,
    val sub_after: String? = null,
    val last_running_time: Long? = null,
    val last_execution_time: Long? = null
)

/** 字段对齐 `back/api/subscription.ts` 的 POST 校验规则 */
data class QlCreateSubscriptionReq(
    val type: String = "public-repo",
    val url: String,
    val schedule: String? = null,
    val interval_schedule: com.google.gson.JsonElement? = null,
    val name: String? = null,
    val whitelist: String? = null,
    val blacklist: String? = null,
    val branch: String? = null,
    val dependences: String? = null,
    val pull_type: String? = null,
    val pull_option: com.google.gson.JsonElement? = null,
    val extensions: String? = null,
    val sub_before: String? = null,
    val sub_after: String? = null,
    val schedule_type: String = "crontab",
    val alias: String,
    val proxy: String? = null,
    val autoAddCron: Boolean? = null,
    val autoDelCron: Boolean? = null
)

data class QlUpdateSubscriptionReq(
    val id: Any,
    val type: String = "public-repo",
    val url: String,
    val schedule: String? = null,
    val interval_schedule: com.google.gson.JsonElement? = null,
    val name: String? = null,
    val whitelist: String? = null,
    val blacklist: String? = null,
    val branch: String? = null,
    val dependences: String? = null,
    val pull_type: String? = null,
    val pull_option: com.google.gson.JsonElement? = null,
    val schedule_type: String? = null,
    val extensions: String? = null,
    val sub_before: String? = null,
    val sub_after: String? = null,
    val alias: String,
    val proxy: String? = null,
    val autoAddCron: Boolean? = null,
    val autoDelCron: Boolean? = null
)

// ---------------------------------------------------------------- 环境变量

data class QlEnvsResp(
    override val code: Int?,
    override val message: String?,
    val data: List<QlEnvItem>?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlEnvItem(
    val id: Any?,
    val name: String,
    val value: String,
    val remarks: String?,
    val status: Int?,
    val labels: List<String>? = null,
    val isPinned: Int? = null  // 1=pinned, 0=normal
)

/**
 * 创建环境变量。
 * 注意后端对 name 有正则约束 `^[a-zA-Z_][0-9a-zA-Z_]*$`
 * （见 `back/api/env.ts`），不符合时接口直接返回 400，App 侧应提前校验。
 */
data class QlCreateEnvReq(
    val name: String,
    val value: String,
    val remarks: String? = null,
    val labels: List<String>? = null
)

data class QlUpdateEnvReq(
    val id: Any,
    val name: String,
    val value: String,
    val remarks: String? = null,
    val labels: List<String>? = null
)

data class QlEnvNameBatchReq(
    val ids: List<Long>,
    val name: String
)

data class QlEnvMoveReq(
    val fromIndex: Long,
    val toIndex: Long
)

// ---------------------------------------------------------------- 依赖

data class QlDepItem(
    val id: Any?,
    val name: String,
    val type: Any?,
    val status: Int?,
    val remark: String?,
    val log: List<String>? = null
)

data class QlDepsResp(
    override val code: Int?,
    override val message: String?,
    val data: com.google.gson.JsonElement?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlCreateDepReq(
    val name: String,
    val type: Int, // 0: nodejs, 1: python3, 2: linux
    val remark: String? = null
)

// ---------------------------------------------------------------- 配置文件

data class QlConfigFileItem(val title: String, val value: String)

data class QlConfigFilesResp(
    override val code: Int?,
    override val message: String?,
    val data: List<QlConfigFileItem>?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlConfigDetailResp(
    override val code: Int?,
    override val message: String?,
    val data: String?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlSaveConfigReq(val name: String, val content: String)

// ---------------------------------------------------------------- 脚本

data class QlScriptNodeItem(
    val title: String? = null,
    val name: String? = null,
    val value: String? = null,
    val key: String? = null,
    val type: String? = null,
    val parent: String? = null,
    val size: Long? = null,
    val mtime: Long? = null,
    val disabled: Boolean? = false,
    val isLeaf: Boolean? = null,
    val children: List<QlScriptNodeItem>? = null
)

data class QlScriptsResp(
    override val code: Int?,
    override val message: String?,
    val data: List<QlScriptNodeItem>?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlScriptContentResp(
    override val code: Int?,
    override val message: String?,
    val data: String?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlCreateScriptReq(
    val filename: String,
    val content: String = "",
    val path: String = "",
    val directory: String? = null,
    val originFilename: String? = null
)

data class QlSaveScriptReq(
    val filename: String,
    val content: String,
    val path: String = ""
)

data class QlDeleteScriptReq(
    val filename: String,
    val path: String = ""
)

data class QlRenameScriptReq(
    val filename: String,
    val path: String = "",
    val newFilename: String
)

data class QlRunScriptReq(
    val filename: String,
    val content: String? = null,
    val path: String? = null
)

data class QlStopScriptReq(
    val filename: String,
    val path: String? = null,
    val pid: Int? = null
)

// ---------------------------------------------------------------- 通用

data class QlCommonResp(
    override val code: Int?,
    override val message: String?,
    val data: Any? = null
) : ApiEnvelope {
    override val msg: String? get() = null
}

/**
 * 返回体结构不固定（或需要自行解析）的响应统一用这个信封，
 * 这样依然能走 unwrap 做业务码校验。
 */
data class QlRawResp(
    override val code: Int?,
    override val message: String?,
    val data: com.google.gson.JsonElement? = null
) : ApiEnvelope {
    override val msg: String? get() = null
}

interface QinglongV15Api {

    @GET("open/auth/token")
    suspend fun getToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String
    ): Response<QlTokenResp>

    @POST("api/user/login")
    suspend fun login(@Body req: Map<String, String>): Response<QlTokenResp>

    @POST("api/user/logout")
    suspend fun logout(@Header("Authorization") auth: String): Response<QlCommonResp>

    @PUT("api/user")
    suspend fun updateCredential(
        @Header("Authorization") auth: String,
        @Body req: Map<String, String>
    ): Response<QlCommonResp>

    // ================= 1. 定时任务 =================

    @GET("api/crons")
    suspend fun getCrons(
        @Header("Authorization") auth: String,
        @Query("searchValue") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
        @Query("filters") filters: String? = null,
        @Query("sorter") sorter: String? = null,
        @Query("queryString") queryString: String? = null
    ): Response<QlCronsResp>

    @GET("api/crons/detail")
    suspend fun getCronDetail(
        @Header("Authorization") auth: String,
        @Query("id") id: String
    ): Response<QlRawResp>

    @POST("api/crons")
    suspend fun createCron(
        @Header("Authorization") auth: String,
        @Body req: QlCreateCronReq
    ): Response<QlCommonResp>

    @PUT("api/crons")
    suspend fun updateCron(
        @Header("Authorization") auth: String,
        @Body req: QlUpdateCronReq
    ): Response<QlCommonResp>

    @PUT("api/crons/run")
    suspend fun runCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/crons/stop")
    suspend fun stopCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/crons/enable")
    suspend fun enableCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/crons/disable")
    suspend fun disableCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/crons", hasBody = true)
    suspend fun deleteCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/crons/pin")
    suspend fun pinCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/crons/unpin")
    suspend fun unpinCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @POST("api/crons/labels")
    suspend fun addCronLabels(
        @Header("Authorization") auth: String,
        @Body req: QlLabelBatchReq
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/crons/labels", hasBody = true)
    suspend fun removeCronLabels(
        @Header("Authorization") auth: String,
        @Body req: QlLabelBatchReq
    ): Response<QlCommonResp>

    @PUT("api/crons/status")
    suspend fun updateCronStatus(
        @Header("Authorization") auth: String,
        @Body req: QlCronStatusReq
    ): Response<QlCommonResp>

    // ---- 任务分组视图 ----
    @GET("api/crons/views")
    suspend fun getCronViews(@Header("Authorization") auth: String): Response<QlRawResp>

    // ---- 运行实例 ----
    @GET("api/crons/{id}/instances")
    suspend fun getCronInstances(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlCronInstancesResp>

    @POST("api/crons/{id}/instances/{instanceId}/stop")
    suspend fun stopCronInstance(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Path("instanceId") instanceId: String
    ): Response<QlCommonResp>

    @GET("api/crons/{id}/logs")
    suspend fun getCronHistoryLogs(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlRawResp>

    /**
     * 读取任务日志。
     * 不传 offset/limit 时后端默认返回**末尾 256KB**（tail 语义）。
     * 响应里的 truncated / logStatus 必须交给上层处理。
     */
    @GET("api/crons/{id}/log")
    suspend fun getCronLog(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("tail") tail: Boolean? = null
    ): Response<QlLogChunkResp>

    // ================= 2. 订阅管理 =================

    @GET("api/subscriptions")
    suspend fun getSubscriptions(
        @Header("Authorization") auth: String,
        @Query("searchValue") search: String? = null
    ): Response<QlSubscriptionsResp>

    @POST("api/subscriptions")
    suspend fun createSubscription(
        @Header("Authorization") auth: String,
        @Body req: QlCreateSubscriptionReq
    ): Response<QlCommonResp>

    @PUT("api/subscriptions")
    suspend fun updateSubscription(
        @Header("Authorization") auth: String,
        @Body req: QlUpdateSubscriptionReq
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/subscriptions", hasBody = true)
    suspend fun deleteSubscriptions(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>,
        @Query("force") force: Boolean? = null
    ): Response<QlCommonResp>

    @PUT("api/subscriptions/run")
    suspend fun runSubscriptions(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/subscriptions/stop")
    suspend fun stopSubscriptions(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/subscriptions/enable")
    suspend fun enableSubscriptions(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/subscriptions/disable")
    suspend fun disableSubscriptions(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @GET("api/subscriptions/{id}/log")
    suspend fun getSubscriptionLog(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("tail") tail: Boolean? = null
    ): Response<QlLogChunkResp>

    @GET("api/subscriptions/{id}/logs")
    suspend fun getSubscriptionHistoryLogs(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlRawResp>

    // ================= 3. 环境变量 =================

    @GET("api/envs")
    suspend fun getEnvs(
        @Header("Authorization") auth: String,
        @Query("searchValue") search: String? = null
    ): Response<QlEnvsResp>

    @POST("api/envs")
    suspend fun createEnvs(
        @Header("Authorization") auth: String,
        @Body envs: List<QlCreateEnvReq>
    ): Response<QlCommonResp>

    @PUT("api/envs")
    suspend fun updateEnv(
        @Header("Authorization") auth: String,
        @Body env: QlUpdateEnvReq
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/envs", hasBody = true)
    suspend fun deleteEnvs(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/envs/enable")
    suspend fun enableEnvs(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/envs/disable")
    suspend fun disableEnvs(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/envs/name")
    suspend fun updateEnvNames(
        @Header("Authorization") auth: String,
        @Body req: QlEnvNameBatchReq
    ): Response<QlCommonResp>

    @PUT("api/envs/{id}/move")
    suspend fun moveEnv(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body req: QlEnvMoveReq
    ): Response<QlCommonResp>

    @PUT("api/envs/pin")
    suspend fun pinEnvs(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/envs/unpin")
    suspend fun unpinEnvs(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @POST("api/envs/labels")
    suspend fun addEnvLabels(
        @Header("Authorization") auth: String,
        @Body req: QlLabelBatchReq
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/envs/labels", hasBody = true)
    suspend fun removeEnvLabels(
        @Header("Authorization") auth: String,
        @Body req: QlLabelBatchReq
    ): Response<QlCommonResp>

    // ================= 4. 依赖管理 =================

    @GET("api/dependencies")
    suspend fun getDependencies(
        @Header("Authorization") auth: String,
        @Query("searchValue") search: String? = null,
        @Query("type") type: String? = null,
        @Query("status") status: String? = null
    ): Response<QlDepsResp>

    @POST("api/dependencies")
    suspend fun installDependencies(
        @Header("Authorization") auth: String,
        @Body req: List<QlCreateDepReq>
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/dependencies", hasBody = true)
    suspend fun deleteDependencies(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/dependencies/force", hasBody = true)
    suspend fun forceDeleteDependencies(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/dependencies/reinstall")
    suspend fun reinstallDependencies(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    @PUT("api/dependencies/cancel")
    suspend fun cancelDependencies(
        @Header("Authorization") auth: String,
        @Body ids: List<Long>
    ): Response<QlCommonResp>

    // ================= 5. 配置文件 =================

    @GET("api/configs/files")
    suspend fun getConfigFiles(@Header("Authorization") auth: String): Response<QlConfigFilesResp>

    @GET("api/configs/detail")
    suspend fun getConfigDetail(
        @Header("Authorization") auth: String,
        @Query("path") path: String
    ): Response<QlConfigDetailResp>

    @POST("api/configs/save")
    suspend fun saveConfig(
        @Header("Authorization") auth: String,
        @Body req: QlSaveConfigReq
    ): Response<QlCommonResp>

    // ================= 6. 脚本管理 =================

    @GET("api/scripts")
    suspend fun getScripts(
        @Header("Authorization") auth: String,
        @Query("path") path: String? = null
    ): Response<QlScriptsResp>

    @GET("api/scripts/detail")
    suspend fun getScriptDetail(
        @Header("Authorization") auth: String,
        @Query("file") file: String,
        @Query("path") path: String? = null
    ): Response<QlScriptContentResp>

    @POST("api/scripts")
    suspend fun createScript(
        @Header("Authorization") auth: String,
        @Body req: QlCreateScriptReq
    ): Response<QlCommonResp>

    @PUT("api/scripts")
    suspend fun saveScript(
        @Header("Authorization") auth: String,
        @Body req: QlSaveScriptReq
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/scripts", hasBody = true)
    suspend fun deleteScript(
        @Header("Authorization") auth: String,
        @Body req: QlDeleteScriptReq
    ): Response<QlCommonResp>

    @PUT("api/scripts/rename")
    suspend fun renameScript(
        @Header("Authorization") auth: String,
        @Body req: QlRenameScriptReq
    ): Response<QlCommonResp>

    /** 调试运行脚本（写到 .swap 文件后执行） */
    @PUT("api/scripts/run")
    suspend fun runScript(
        @Header("Authorization") auth: String,
        @Body req: QlRunScriptReq
    ): Response<QlCommonResp>

    @PUT("api/scripts/stop")
    suspend fun stopScript(
        @Header("Authorization") auth: String,
        @Body req: QlStopScriptReq
    ): Response<QlCommonResp>

    // ================= 7. 日志（系统日志目录） =================

    @GET("api/logs")
    suspend fun getLogsTree(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/logs/detail")
    suspend fun getLogDetail(
        @Header("Authorization") auth: String,
        @Query("file") file: String,
        @Query("path") path: String? = null,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("tail") tail: Boolean? = null
    ): Response<QlLogChunkResp>

    // ================= 8. 系统信息 =================

    @GET("api/system")
    suspend fun getSystemInfo(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/system/config")
    suspend fun getSystemConfig(@Header("Authorization") auth: String): Response<QlRawResp>

    @PUT("api/system/config/log-remove-frequency")
    suspend fun updateLogRemoveFrequency(
        @Header("Authorization") auth: String,
        @Body body: @JvmSuppressWildcards Map<String, Any?>
    ): Response<QlCommonResp>

    /** 任务并发数，null 表示不限制 */
    @PUT("api/system/config/cron-concurrency")
    suspend fun updateCronConcurrency(
        @Header("Authorization") auth: String,
        @Body body: @JvmSuppressWildcards Map<String, Any?>
    ): Response<QlCommonResp>

    /** 修改面板配置后需要重载才会生效 */
    @PUT("api/system/reload")
    suspend fun reloadSystem(
        @Header("Authorization") auth: String,
        @Body body: @JvmSuppressWildcards Map<String, Any?> = emptyMap()
    ): Response<QlCommonResp>

    /** 系统运行日志（区别于任务日志） */
    @GET("api/system/log")
    suspend fun getSystemLog(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int? = null
    ): Response<QlRawResp>

    @DELETE("api/system/log")
    suspend fun deleteSystemLog(@Header("Authorization") auth: String): Response<QlCommonResp>

    // ================= 8.5 存储清理 (Storage Retention) =================

    /** POST /api/system/storage-retention/cleanup，需 body.confirmation == "CLEAN" */
    @POST("api/system/storage-retention/cleanup")
    suspend fun cleanupStorageRetention(
        @Header("Authorization") auth: String,
        @Body body: @JvmSuppressWildcards Map<String, Any?>
    ): Response<QlRawResp>

    @PUT("api/system/notify")
    suspend fun testNotify(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String>
    ): Response<QlCommonResp>

    // ================= 9. 仪表盘 =================

    @GET("api/dashboard/system")
    suspend fun getDashboardSystem(@Header("Authorization") auth: String): Response<QlRawResp>

    /** 总任务数 / 启用 / 禁用 / 今日运行 / 成功率 */
    @GET("api/dashboard/overview")
    suspend fun getDashboardOverview(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/dashboard/trend")
    suspend fun getDashboardTrend(
        @Header("Authorization") auth: String,
        @Query("days") days: Int? = null
    ): Response<QlRawResp>

    /**
     * 运行中的实例 + 排队任务 + 闲置任务。
     * running[] 里带 instanceId 与 logPath，是"停止指定实例"与"查看实时日志"的数据来源。
     */
    @GET("api/dashboard/runtime")
    suspend fun getDashboardRuntime(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/dashboard/top-time")
    suspend fun getDashboardTopTime(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/dashboard/top-count")
    suspend fun getDashboardTopCount(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/dashboard/labels")
    suspend fun getDashboardLabels(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/user/login-log")
    suspend fun getLoginLogs(@Header("Authorization") auth: String): Response<QlRawResp>

    @GET("api/apps")
    suspend fun getApps(@Header("Authorization") auth: String): Response<QlRawResp>
}
