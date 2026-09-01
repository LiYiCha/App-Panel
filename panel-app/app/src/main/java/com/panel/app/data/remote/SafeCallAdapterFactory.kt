package com.panel.app.data.remote

import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 把 Retrofit 调用过程中的**所有**异常，统一翻译成一个 HTTP 599 的失败响应。
 *
 * ### 为什么需要这一层
 * `IPanelAdapter` 的每个方法都声明返回 `Result<T>`，但三个适配器里绝大多数方法
 * （`runTask` / `stopTask` / `pinTask` / `logout` / 各类批量操作等）并没有 try/catch，
 * 而是直接 `api.runTask(id).unwrap(...)`。
 * 只要 OkHttp 抛 IOException（连接超时、域名解析失败、连接被拒、响应体被截断），
 * 或 Gson 解析响应体失败，异常就会一路冒到 `viewModelScope` 的主线程上——闪退。
 *
 * 给 100+ 个方法逐个补 try/catch 既不现实也必然漏改，
 * 所以收敛到 Retrofit 自身的边界：包住 `Call`，把失败翻译成 `Response.error(...)`。
 * 之后 `unwrap()` 会把它变成一个普通的失败 `Result`，UI 只弹提示，不会崩。
 *
 * ### 与「全局 CoroutineExceptionHandler」的区别
 * 全局兜底会把崩溃变成"点了没反应"，用户看不到任何失败原因；
 * 这里异常仍然沿正常链路返回，只是换成了可读的错误提示，
 * 且 `Result` 契约依旧成立（适配器方法仍可被认为"不抛异常"）。
 *
 * ### 与 OkHttp 拦截器的区别
 * 拦截器只能捕获 `chain.proceed()` 阶段的异常，
 * **抓不到"响应体读取 / JSON 转换"阶段的异常**（那发生在拦截器返回之后）。
 * 放在 CallAdapter 层才能覆盖整条调用链。
 */
class SafeCallAdapterFactory : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        // 只接管 suspend 函数：Retrofit 会用 Call<T> 作为适配器类型向工厂询价
        if (getRawType(returnType) != Call::class.java) return null
        if (returnType !is ParameterizedType) return null

        val responseType = getParameterUpperBound(0, returnType)

        // 交给 Retrofit 自带的 Call 适配器拿到真实 Call，本工厂只做外包。
        // 拿不到委托就返回 null：Retrofit 会继续问下一个工厂，
        // 保证最坏情况下退回原有行为，而不是让所有请求一起失效。
        val delegate = try {
            retrofit.nextCallAdapter(this, returnType, annotations)
        } catch (_: Exception) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return SafeCallAdapter(responseType, delegate as CallAdapter<Any?, Call<Any?>>)
    }

    private class SafeCallAdapter(
        private val responseType: Type,
        private val delegate: CallAdapter<Any?, Call<Any?>>
    ) : CallAdapter<Any?, Call<Any?>> {
        override fun responseType(): Type = responseType
        override fun adapt(call: Call<Any?>): Call<Any?> = SafeCall(delegate.adapt(call))
    }

    private class SafeCall(private val delegate: Call<Any?>) : Call<Any?> {

        override fun execute(): Response<Any?> = try {
            delegate.execute()
        } catch (e: Exception) {
            errorResponse(e)
        }

        override fun enqueue(callback: Callback<Any?>) {
            delegate.enqueue(object : Callback<Any?> {
                override fun onResponse(call: Call<Any?>, response: Response<Any?>) {
                    callback.onResponse(call, response)
                }

                override fun onFailure(call: Call<Any?>, t: Throwable) {
                    // 协程取消时 OkHttp 抛的是 IOException("Canceled")，
                    // 转成失败响应即可（resume 会被已取消的 continuation 丢弃），不影响结构化并发
                    if (t is Exception) {
                        callback.onResponse(call, errorResponse(t))
                    } else {
                        callback.onFailure(call, t)
                    }
                }
            })
        }

        override fun clone(): Call<Any?> = SafeCall(delegate.clone())
        override fun request(): okhttp3.Request = delegate.request()
        override fun timeout(): okio.Timeout = delegate.timeout()
        override fun isExecuted(): Boolean = delegate.isExecuted
        override fun isCanceled(): Boolean = delegate.isCanceled
        override fun cancel() = delegate.cancel()
    }

    companion object {
        /**
         * 非标准业务码，仅用于把异常伪装成"服务端返回失败"，从而被 [unwrap] 统一处理。
         * 取值 >= 400 才能走 `Response.error()`；避开 401/403 以免被误判成登录态失效。
         */
        const val ERROR_CODE = 599

        private fun errorResponse(e: Exception): Response<Any?> {
            val message = "网络请求失败：${describe(e)}"
            // 带上 code/message/msg，青龙、白虎、unwrap() 三种读法都能取到提示
            val jsonBody = JsonObject().apply {
                addProperty("code", ERROR_CODE)
                addProperty("message", message)
                addProperty("msg", message)
            }.toString()

            return Response.error(
                ERROR_CODE,
                jsonBody.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull())
            )
        }

        private fun describe(e: Exception): String = when (e) {
            is java.net.SocketTimeoutException -> "连接超时，请检查面板地址与网络"
            is java.net.UnknownHostException -> "域名无法解析，请检查面板地址是否正确"
            is java.net.ConnectException -> "连接被拒绝（${e.message ?: "端口可能未开放"}）"
            is javax.net.ssl.SSLException -> "SSL 握手失败（${e.message ?: "证书异常"}）"
            else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        }
    }
}
