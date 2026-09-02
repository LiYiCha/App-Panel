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
                    } catch (t: Throwable) {
                        val duration = System.currentTimeMillis() - start
                        com.panel.app.data.logger.AppLogger.httpDetailed(
                            method = req.method,
                            url = req.url.toString(),
                            code = 0,
                            durationMs = duration,
                            requestBody = reqBodyStr,
                            error = t.message ?: t.javaClass.simpleName
                        )
                        throw t
                    }
                }
                // 只补 keep-alive 头，**绝不在拦截器里重放请求**。
                //
                // 这里原来有一段"连接断开自动重试"逻辑：在 catch 里对同一个 chain
                // 再次调用 proceed()。问题在于 java.net.ConnectException 继承自
                // java.net.SocketException，会被那段判定命中并触发重放，于是出现
                // 控制台里那条 "失败去连接 /118.x.x.x" 之后立刻闪退的现象：
                //  1. OkHttp 的拦截器链不允许对同一个 chain 多次 proceed()；
                //  2. 重放抛出的 IllegalStateException 不是 IOException，
                //     AsyncCall 会先回调 onFailure（UI 侧只看到一次"请求失败"），
                //     再把该异常原样抛到 OkHttp 的调度线程 —— 那里没有 catch，
                //     直接走 Thread 的 UncaughtExceptionHandler，进程崩溃。
                // 断网时抛的是 UnknownHostException（不是 SocketException），
                // 不会触发重放，所以"断网不闪退、连不上就闪退"完全对得上。
                //
                // 连接复用池里的陈旧连接由 OkHttp 自己的 RetryAndFollowUpInterceptor
                // 处理，下面 retryOnConnectionFailure(true) 已开启，无需手写重试。
                .addInterceptor { chain ->
                    val originalReq = chain.request()
                    val requestBuilder = originalReq.newBuilder()
                    if (originalReq.header("Connection") == null) {
                        requestBuilder.header("Connection", "keep-alive")
                    }
                    chain.proceed(requestBuilder.build())
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
