package com.panel.app.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkClient {

    // 内存 CookieJar，用于自动维系白虎面板 BHToken 凭据与青龙会话
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    /**
     * 解决局域网自签名 SSL、明文 HTTP 以及自动维系 Session Cookie (DESIGN.md Section 2.3)
     */
    val unsafeOkHttpClient: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .cookieJar(object : CookieJar {
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        val hostCookies = cookieStore.computeIfAbsent(url.host) { mutableListOf() }
                        hostCookies.removeAll { c -> cookies.any { it.name == c.name } }
                        hostCookies.addAll(cookies)
                    }

                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        return cookieStore[url.host] ?: emptyList()
                    }
                })
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val req = chain.request()
                    if (!com.panel.app.data.logger.AppLogger.isDevModeEnabled) {
                        return@addInterceptor chain.proceed(req)
                    }
                    val start = System.currentTimeMillis()
                    var reqBodyStr: String? = null
                    try {
                        val body = req.body
                        if (body != null && body.contentLength() in 1..8192) {
                            val buffer = okio.Buffer()
                            body.writeTo(buffer)
                            reqBodyStr = buffer.readUtf8()
                        }
                    } catch (_: Exception) {}

                    try {
                        val resp = chain.proceed(req)
                        val duration = System.currentTimeMillis() - start
                        val respSnippet = try {
                            resp.peekBody(2048).string()
                        } catch (_: Exception) { null }

                        com.panel.app.data.logger.AppLogger.httpDetailed(
                            method = req.method,
                            url = req.url.toString(),
                            code = resp.code,
                            durationMs = duration,
                            requestBody = reqBodyStr,
                            responseBody = respSnippet
                        )
                        resp
                    } catch (e: Exception) {
                        val duration = System.currentTimeMillis() - start
                        com.panel.app.data.logger.AppLogger.httpDetailed(
                            method = req.method,
                            url = req.url.toString(),
                            code = 0,
                            durationMs = duration,
                            requestBody = reqBodyStr,
                            error = e.message ?: e.javaClass.simpleName
                        )
                        throw e
                    }
                }
                .addInterceptor { chain ->
                    val originalReq = chain.request()
                    var response: okhttp3.Response? = null
                    var lastException: java.io.IOException? = null
                    for (attempt in 0..2) {
                        try {
                            val requestBuilder = originalReq.newBuilder()
                            if (originalReq.header("Connection") == null) {
                                requestBuilder.header("Connection", "keep-alive")
                            }
                            response = chain.proceed(requestBuilder.build())
                            break
                        } catch (e: java.io.IOException) {
                            lastException = e
                            val msg = e.message?.lowercase() ?: ""
                            val isStaleConnection = msg.contains("unexpected end of stream") ||
                                    msg.contains("connection reset") ||
                                    msg.contains("broken pipe") ||
                                    e is java.net.SocketException
                            if (isStaleConnection && attempt < 2) {
                                com.panel.app.data.logger.AppLogger.log(
                                    level = com.panel.app.data.logger.LogLevel.WARN,
                                    tag = "NET_RETRY",
                                    message = "检测到连接断开 [${e.message}]，正在自动重试 (${attempt + 1}/2)... [${originalReq.method} ${originalReq.url.encodedPath}]"
                                )
                                try { Thread.sleep(150) } catch (_: InterruptedException) {}
                                continue
                            }
                            throw e
                        }
                    }
                    response ?: throw (lastException ?: java.io.IOException("Request failed"))
                }
                .addInterceptor { chain ->
                    val req = chain.request()
                    val resp = chain.proceed(req)
                    val body = resp.body
                    if (body != null) {
                        val contentType = body.contentType()?.toString()?.lowercase() ?: ""
                        if (!contentType.contains("application/json")) {
                            val isHtmlHeader = contentType.contains("text/html")
                            val peek = try {
                                resp.peekBody(512).string().trim()
                            } catch (_: Exception) { "" }
                            val isHtmlContent = peek.startsWith("<!DOCTYPE", ignoreCase = true) ||
                                    peek.startsWith("<html", ignoreCase = true) ||
                                    peek.startsWith("<?xml", ignoreCase = true)

                            if (isHtmlHeader || isHtmlContent) {
                                val code = if (resp.code in 200..299) 500 else resp.code
                                val errorMsg = when (resp.code) {
                                    404 -> "接口不存在 (404)，请检查面板地址或接口路径"
                                    502, 503, 504 -> "网关/代理异常 (${resp.code})，面板后端未正常响应"
                                    401, 403 -> "鉴权异常 (${resp.code})，请重新登录"
                                    else -> "服务端返回了 HTML 页面而非预期 JSON (HTTP ${resp.code})，请检查面板地址与端口"
                                }
                                val syntheticJson = """{"code":$code,"message":"$errorMsg","msg":"$errorMsg"}"""
                                val newBody = syntheticJson.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull())
                                return@addInterceptor resp.newBuilder()
                                    .header("Content-Type", "application/json; charset=utf-8")
                                    .body(newBody)
                                    .build()
                            }
                        }
                    }
                    resp
                }
                .retryOnConnectionFailure(true)
                .connectionPool(okhttp3.ConnectionPool(5, 3, TimeUnit.SECONDS))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder().build()
        }
    }

    val gson: com.google.gson.Gson = com.google.gson.GsonBuilder()
        .setLenient()
        .create()

    fun buildRetrofit(baseUrl: String): Retrofit {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(unsafeOkHttpClient)
            // 必须放在转换器之前：把所有网络/解析异常收敛成失败响应，避免异常冒到主线程闪退
            .addCallAdapterFactory(SafeCallAdapterFactory())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun injectCookie(host: String, name: String, value: String) {
        val list = cookieStore.computeIfAbsent(host) { mutableListOf() }
        list.removeAll { it.name == name }
        list.add(
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(host)
                .path("/")
                .build()
        )
    }

    fun getCookie(host: String, name: String): String? {
        return cookieStore[host]?.firstOrNull { it.name == name }?.value
    }
}
