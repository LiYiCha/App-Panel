package com.panel.app.data.remote

import com.google.gson.JsonParser
import retrofit2.Response

/**
 * 面板 HTTP 响应的统一信封。
 *
 * ### 为什么要这一层
 * 青龙与白虎的**业务错误都用 HTTP 200 返回**，真正的成败藏在响应体的 `code` 字段里。
 * 两个官方前端都必须读 `code` 才能判断成败，这就是最硬的证据：
 *
 * - 白虎 `web/src/api/index.ts`
 *   ```ts
 *   if (json.code !== 200) throw new Error(json.msg || '请求失败')
 *   ```
 * - 青龙 `src/utils/http.tsx`
 *   ```ts
 *   if (res.code !== 200) notification.error({ message: res.message || res.data })
 *   ```
 *
 * 因此**绝不能只依赖 retrofit 的 `isSuccessful()`**（它只看 HTTP 状态码）。
 * 只判断 `isSuccessful` 会把服务端返回的 "cron 表达式无效" 这类提示丢掉，
 * 最终在 UI 上表现为无意义的 `失败: HTTP 200`。
 *
 * ### 字段命名差异
 * 青龙用 `message`，白虎用 `msg`；青龙校验失败时还会额外带 `errors[]` 明细。
 */
interface ApiEnvelope {
    val code: Int?

    /** 青龙的错误提示字段 */
    val message: String?

    /** 白虎的错误提示字段 */
    val msg: String?
}

/** 取可用的服务端提示，优先青龙的 message，其次白虎的 msg */
val ApiEnvelope.errorMessage: String?
    get() = message?.takeIf { it.isNotBlank() } ?: msg?.takeIf { it.isNotBlank() }

/**
 * 面板业务异常。
 *
 * @param code 业务码（青龙/白虎响应体的 code），HTTP 层失败时填 HTTP 状态码
 * @param isAuthExpired 是否为登录态失效（401），调用方可据此触发重新登录
 */
class ApiError(
    val code: Int,
    override val message: String,
    val isAuthExpired: Boolean = false,
) : Exception(message)

/**
 * 面板要求两步验证（OTP）。
 * 白虎开启 OTP 后，登录接口返回 `{require_otp:true, otp_pending_token}` 且**不下发 Cookie**，
 * 必须再调一次 `/auth/login/otp` 才算登录成功。调用方捕获后应弹出验证码输入框。
 */
class OtpRequiredException(val pendingToken: String) :
    Exception("该账号已开启两步验证，请输入动态验证码")

private const val HTTP_UNAUTHORIZED = 401
private const val CODE_OK = 200

/**
 * 解包面板响应：HTTP 层 + 业务 code 层双重校验，并把服务端提示透传出去。
 *
 * @param fallbackMessage 服务端没给提示时的兜底文案，例如 "获取任务列表失败"
 */
fun <E : ApiEnvelope> Response<E>.unwrap(fallbackMessage: String): Result<E> {
    val httpCode = code()

    // 1. HTTP 层失败：青龙的 401 会走这里
    if (!isSuccessful) {
        val serverMsg = readErrorBody()
        return Result.failure(
            ApiError(
                code = httpCode,
                message = serverMsg?.let { "$fallbackMessage: $it" } ?: "$fallbackMessage (HTTP $httpCode)",
                isAuthExpired = httpCode == HTTP_UNAUTHORIZED,
            )
        )
    }

    val body = body() ?: return Result.failure(ApiError(httpCode, "$fallbackMessage: 响应为空"))

    // 2. HTTP 成功但空响应体

    // 3. 业务层失败：HTTP 200 但 code != 200（白虎全部错误、青龙大部分错误走这里）
    val bizCode = body.code ?: CODE_OK
    if (bizCode != CODE_OK) {
        val serverMsg = body.errorMessage
        return Result.failure(
            ApiError(
                code = bizCode,
                message = serverMsg?.let { "$fallbackMessage: $it" } ?: fallbackMessage,
                isAuthExpired = bizCode == HTTP_UNAUTHORIZED,
            )
        )
    }

    return Result.success(body)
}

/** 解包并直接映射为业务模型，避免每个调用点重复写 getOrElse */
inline fun <E : ApiEnvelope, T> Response<E>.unwrapTo(
    fallbackMessage: String,
    transform: (E) -> T,
): Result<T> = unwrap(fallbackMessage).map(transform)

/**
 * 从错误响应体里尽力提取服务端提示（message / msg / errors[].message），
 * 避免用户只看到一个 HTTP 状态码。
 */
private fun Response<*>.readErrorBody(): String? {
    val raw = try {
        errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (raw.isNullOrBlank()) return null

    return try {
        val obj = JsonParser.parseString(raw).asJsonObject
        val details = obj.getAsJsonArray("errors")
            ?.takeIf { it.size() > 0 }
            ?.joinToString("; ") { it.asJsonObject.get("message")?.asString.orEmpty() }
            ?.takeIf { it.isNotBlank() }

        listOfNotNull(obj.get("message")?.asString, obj.get("msg")?.asString, details)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
    } catch (_: Exception) {
        null
    }
}
