package com.panel.app.data.remote.api

import retrofit2.Response
import retrofit2.http.*

data class QlV10LoginReq(val username: String, val password: String)
data class QlV10LoginResp(val code: Int?, val data: QlV10AuthData?)
data class QlV10AuthData(val token: String?)

interface QinglongV10Api {
    @POST("api/user/login")
    suspend fun login(@Body req: QlV10LoginReq): Response<QlV10LoginResp>

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

    @DELETE("api/crons")
    suspend fun deleteCrons(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>

    @GET("api/crons/{id}/log")
    suspend fun getCronLog(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<QlCronLogResp>

    // 2. 环境变量 (Envs)
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

    @DELETE("api/envs")
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

    // 3. 配置文件 (Configs)
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

    // 4. 脚本文件 (Scripts)
    @GET("api/scripts")
    suspend fun getScripts(
        @Header("Authorization") auth: String
    ): Response<QlScriptsResp>

    @GET("api/scripts/{file}")
    suspend fun getScriptContent(
        @Header("Authorization") auth: String,
        @Path("file") file: String
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

    @DELETE("api/scripts")
    suspend fun deleteScript(
        @Header("Authorization") auth: String,
        @Body req: QlDeleteScriptReq
    ): Response<QlCommonResp>

    // 5. 依赖包管理 (Dependencies)
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

    @DELETE("api/dependencies")
    suspend fun deleteDependencies(
        @Header("Authorization") auth: String,
        @Body ids: List<Any>
    ): Response<QlCommonResp>
}
