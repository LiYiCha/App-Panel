package com.panel.app.data.remote.api

import com.panel.app.data.remote.ApiEnvelope
import retrofit2.Response
import retrofit2.http.*

/**
 * 青龙面板 2.10 ~ 2.14（旧版）API。
 *
 * 与 2.15+ 的差异：
 * - 配置文件走 `/api/configs/{file}` 而非 `/api/configs/detail`
 * - 脚本内容走 `/api/scripts/{file}` 而非 `/api/scripts/detail`
 * - 无订阅模块、无运行实例接口
 *
 * 这些旧路径在新版青龙里**已返回 410 下线**，所以只能用于真正的旧版面板。
 */
data class QlV10LoginReq(val username: String, val password: String)

data class QlV10LoginResp(
    override val code: Int?,
    override val message: String? = null,
    val data: QlV10AuthData?
) : ApiEnvelope {
    override val msg: String? get() = null
}

data class QlV10AuthData(val token: String?, val token_type: String? = null, val expiration: Long? = null)

interface QinglongV10Api {
    @POST("api/user/login")
    suspend fun login(@Body req: QlV10LoginReq): Response<QlV10LoginResp>

    // 1. 定时任务
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

    @GET("api/crons/{id}/log")
    suspend fun getCronLog(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlLogChunkResp>

    // 2. 环境变量
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

    // 3. 配置文件（旧版路径，新版已 410 下线）
    @GET("api/configs/{file}")
    suspend fun getConfig(
        @Header("Authorization") auth: String,
        @Path("file") file: String
    ): Response<QlConfigDetailResp>

    @POST("api/configs/save")
    suspend fun saveConfig(
        @Header("Authorization") auth: String,
        @Body req: QlSaveConfigReq
    ): Response<QlCommonResp>

    // 4. 脚本文件（旧版路径，新版已 410 下线）
    @GET("api/scripts")
    suspend fun getScripts(@Header("Authorization") auth: String): Response<QlScriptsResp>

    @GET("api/scripts/{file}")
    suspend fun getScriptContent(
        @Header("Authorization") auth: String,
        @Path("file", encoded = true) file: String
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

    // 5. 依赖包管理
    @GET("api/dependencies")
    suspend fun getDependencies(
        @Header("Authorization") auth: String,
        @Query("searchValue") search: String? = null
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

    // 6. 任务置顶（2.10+ 支持）
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

    // 7. 用户登录日志
    @GET("api/user/login-log")
    suspend fun getLoginLogs(
        @Header("Authorization") auth: String
    ): Response<QlRawResp>

    // 8. 仪表盘（系统监控）
    @GET("api/dashboard/system")
    suspend fun getDashboardSystem(
        @Header("Authorization") auth: String
    ): Response<QlRawResp>

    // 9. 系统日志目录
    @GET("api/logs")
    suspend fun getLogsTree(
        @Header("Authorization") auth: String
    ): Response<QlRawResp>

    @GET("api/logs/detail")
    suspend fun getLogDetail(
        @Header("Authorization") auth: String,
        @Query("file") file: String,
        @Query("path") path: String? = null,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("tail") tail: Boolean? = null
    ): Response<QlLogChunkResp>
}
