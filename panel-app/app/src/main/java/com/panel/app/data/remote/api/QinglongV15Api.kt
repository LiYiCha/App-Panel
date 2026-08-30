package com.panel.app.data.remote.api

import retrofit2.Response
import retrofit2.http.*

data class QlTokenResp(val code: Int?, val message: String?, val data: QlTokenData?)
data class QlTokenData(val token: String?, val token_type: String?, val expiration: Long?)

data class QlCronsResp(val code: Int?, val message: String?, val data: com.google.gson.JsonElement?)
data class QlCronItem(
    val id: Any?,
    val name: String,
    val command: String,
    val schedule: String,
    val status: Int?,
    val isDisabled: Int?,
    val isPinned: Int? = null,
    val extra_schedules: Any? = null,
    val labels: List<String>? = null,
    val last_running_time: Long? = null,
    val last_execution_time: Long? = null
)

data class QlCreateCronReq(
    val name: String,
    val command: String,
    val schedule: String
)

data class QlUpdateCronReq(
    val id: Any,
    val name: String,
    val command: String,
    val schedule: String
)

data class QlEnvsResp(val code: Int?, val message: String?, val data: List<QlEnvItem>?)
data class QlEnvItem(
    val id: Any?,
    val name: String,
    val value: String,
    val remarks: String?,
    val status: Int?
)

data class QlCreateEnvReq(
    val name: String,
    val value: String,
    val remarks: String? = null
)

data class QlUpdateEnvReq(
    val id: Any,
    val name: String,
    val value: String,
    val remarks: String? = null
)

data class QlDepItem(
    val id: Any?,
    val name: String,
    val type: Any?,
    val status: Int?,
    val remark: String?,
    val log: List<String>? = null
)

data class QlDepsResp(
    val code: Int?,
    val message: String?,
    val data: com.google.gson.JsonElement?
)

data class QlDepDetailResp(
    val code: Int?,
    val message: String?,
    val data: QlDepItem?
)

data class QlCreateDepReq(
    val name: String,
    val type: Int, // 0: nodejs, 1: python3, 2: linux
    val remark: String? = null
)

// 官方青龙订阅数据模型
data class QlSubscriptionItem(
    val id: Any?,
    val name: String?,
    val type: String,
    val url: String,
    val branch: String?,
    val schedule: String?,
    val whitelist: String?,
    val blacklist: String?,
    val dependences: String?,
    val extensions: String?,
    val alias: String?,
    val autoAddCron: Any?,
    val autoDelCron: Any?,
    val status: Int?,
    val last_running_time: Long?,
    val last_execution_time: Long?
)

data class QlSubscriptionsResp(
    val code: Int?,
    val message: String?,
    val data: com.google.gson.JsonElement?
)

data class QlCreateSubscriptionReq(
    val name: String?,
    val type: String = "public-repo",
    val url: String,
    val branch: String? = "main",
    val schedule_type: String = "crontab",
    val schedule: String? = "0 0 * * *",
    val whitelist: String? = null,
    val blacklist: String? = null,
    val dependences: String? = null,
    val extensions: String? = null,
    val alias: String,
    val autoAddCron: Boolean = true,
    val autoDelCron: Boolean = true
)

data class QlUpdateSubscriptionReq(
    val id: Any,
    val name: String?,
    val type: String = "public-repo",
    val url: String,
    val branch: String? = "main",
    val schedule_type: String = "crontab",
    val schedule: String? = "0 0 * * *",
    val whitelist: String? = null,
    val blacklist: String? = null,
    val dependences: String? = null,
    val extensions: String? = null,
    val alias: String,
    val autoAddCron: Boolean = true,
    val autoDelCron: Boolean = true
)

// 官方青龙配置文件数据模型
data class QlConfigFileItem(val title: String, val value: String)
data class QlConfigFilesResp(val code: Int?, val message: String?, val data: List<QlConfigFileItem>?)
data class QlConfigDetailResp(val code: Int?, val message: String?, val data: String?)
data class QlSaveConfigReq(val name: String, val content: String)

// 任务实例记录
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
    val exit_code: Int?
)
data class QlCronInstancesResp(val code: Int?, val message: String?, val data: List<QlCronInstanceItem>?)
data class QlCronLogResp(val code: Int?, val message: String?, val data: com.google.gson.JsonElement?)

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
data class QlScriptsResp(val code: Int?, val message: String?, val data: List<QlScriptNodeItem>?)
data class QlScriptContentResp(val code: Int?, val message: String?, val data: String?)

data class QlCreateScriptReq(
    val filename: String? = null,
    val content: String = "",
    val path: String = "",
    val directory: String? = null
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

data class QlCommonResp(val code: Int?, val message: String?)

interface QinglongV15Api {
    @GET("open/auth/token")
    suspend fun getToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String
    ): Response<QlTokenResp>

    @POST("api/user/login")
    suspend fun login(@Body req: Map<String, String>): Response<QlTokenResp>

    // 1. 定时任务 (Crons)
    @GET("api/crons")
    suspend fun getCrons(
        @Header("Authorization") auth: String,
        @Query("searchValue") search: String? = null
    ): Response<QlCronsResp>

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
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/crons/stop")
    suspend fun stopCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/crons/enable")
    suspend fun enableCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/crons/disable")
    suspend fun disableCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/crons", hasBody = true)
    suspend fun deleteCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/crons/pin")
    suspend fun pinCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/crons/unpin")
    suspend fun unpinCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @GET("api/crons/{id}/instances")
    suspend fun getCronInstances(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlCronInstancesResp>

    @GET("api/crons/{id}/logs")
    suspend fun getCronHistoryLogs(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<com.google.gson.JsonElement>

    @GET("api/crons/{id}/log")
    suspend fun getCronLog(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlCronLogResp>

    // 2. 订阅管理 (Subscriptions)
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
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/subscriptions/run")
    suspend fun runSubscriptions(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/subscriptions/stop")
    suspend fun stopSubscriptions(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @GET("api/subscriptions/{id}/log")
    suspend fun getSubscriptionLog(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlCronLogResp>

    // 3. 环境变量 (Envs)
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
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/envs/enable")
    suspend fun enableEnvs(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @PUT("api/envs/disable")
    suspend fun disableEnvs(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    // 4. 依赖管理 (Dependencies)
    @GET("api/dependencies")
    suspend fun getDependencies(
        @Header("Authorization") auth: String,
        @Query("searchValue") search: String? = null
    ): Response<QlDepsResp>

    @GET("api/dependencies/{id}")
    suspend fun getDependencyDetail(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlDepDetailResp>

    @POST("api/dependencies")
    suspend fun installDependencies(
        @Header("Authorization") auth: String,
        @Body req: List<QlCreateDepReq>
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/dependencies", hasBody = true)
    suspend fun deleteDependencies(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @HTTP(method = "DELETE", path = "api/dependencies/force", hasBody = true)
    suspend fun forceDeleteDependencies(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    // 5. 配置文件 (Configs)
    @GET("api/configs/files")
    suspend fun getConfigFiles(
        @Header("Authorization") auth: String
    ): Response<QlConfigFilesResp>

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

    // 6. 脚本管理 (Scripts)
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
    ): Response<com.google.gson.JsonElement>

    @GET("api/scripts/{file}")
    suspend fun getLegacyScriptContent(
        @Header("Authorization") auth: String,
        @Path("file", encoded = true) file: String,
        @Query("path") path: String? = null
    ): Response<com.google.gson.JsonElement>

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

    // 7. 系统与审计设置 (System & Logs)
    @GET("api/dashboard/system")
    suspend fun getDashboardSystem(
        @Header("Authorization") auth: String
    ): Response<com.google.gson.JsonElement>

    @GET("api/user/login-log")
    suspend fun getLoginLogs(
        @Header("Authorization") auth: String
    ): Response<com.google.gson.JsonElement>

    @GET("api/logs")
    suspend fun getLogsTree(
        @Header("Authorization") auth: String
    ): Response<com.google.gson.JsonElement>

    @GET("api/logs/detail")
    suspend fun getLogDetail(
        @Header("Authorization") auth: String,
        @Query("file") file: String,
        @Query("path") path: String? = null
    ): Response<com.google.gson.JsonElement>

    @GET("api/logs/{file}")
    suspend fun getLegacyLogDetail(
        @Header("Authorization") auth: String,
        @Path("file", encoded = true) file: String,
        @Query("path") path: String? = null
    ): Response<com.google.gson.JsonElement>

    @GET("api/system/config")
    suspend fun getSystemConfig(
        @Header("Authorization") auth: String
    ): Response<com.google.gson.JsonElement>

    @PUT("api/system/config/log-remove-frequency")
    suspend fun updateLogRemoveFrequency(
        @Header("Authorization") auth: String,
        @Body body: Map<String, Any?>
    ): Response<com.google.gson.JsonElement>

    @PUT("api/system/notify")
    suspend fun testNotify(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String>
    ): Response<com.google.gson.JsonElement>

    @GET("api/apps")
    suspend fun getApps(
        @Header("Authorization") auth: String
    ): Response<com.google.gson.JsonElement>
}
