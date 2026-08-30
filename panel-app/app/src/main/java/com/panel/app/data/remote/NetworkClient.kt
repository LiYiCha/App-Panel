package com.panel.app.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
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
                .retryOnConnectionFailure(true)
                .connectionPool(okhttp3.ConnectionPool(8, 15, TimeUnit.SECONDS))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder().build()
        }
    }

    fun buildRetrofit(baseUrl: String): Retrofit {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(unsafeOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
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
