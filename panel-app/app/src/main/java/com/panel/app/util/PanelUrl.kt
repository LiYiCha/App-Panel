package com.panel.app.util

import android.net.Uri
import java.util.Locale
import androidx.core.net.toUri

/**
 * 面板服务地址的唯一校验 / 规范化入口。
 *
 * ### 为什么必须有这一层
 * Retrofit 的 `baseUrl()` 遇到不可解析的地址会直接抛 `IllegalArgumentException`。
 * 而适配器是在**构造阶段**就调用它的：
 * ```kotlin
 * private val api = NetworkClient.buildRetrofit(instance.baseUrl).create(BaihuApi::class.java)
 * ```
 * 这一步不在任何 try/catch 内，业务层（`BaihuPanelAdapter.authenticate()` 等）的
 * `catch (e: Exception)` 完全拦不住，异常会一路冒到主线程造成闪退。
 * 最常见的触发场景就是用户漏写协议头：填 `192.168.1.100:5700` 而非 `http://192.168.1.100:5700`。
 *
 * 处理原则：
 * - 能修则修：补 `http://`、去掉空白字符与末尾斜杠；
 * - 修不了就给可直接展示的中文提示，绝不把非法地址继续往下传。
 */
object PanelUrl {

    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")
    private val WHITESPACE = Regex("\\s+")

    /** 仅在输入完全无法解析时使用的兜底地址，保证 Retrofit 一定能构造成功 */
    private const val FALLBACK_URL = "http://127.0.0.1"

    /**
     * 校验并规范化地址，供 UI 与 ViewModel 在提交前调用。
     *
     * @return 规范化后的 `http(s)://host[:port][/path]`，失败时携带可直接展示给用户的提示
     */
    fun normalize(raw: String): Result<String> {
        val trimmed = raw.replace(WHITESPACE, "")
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("请输入面板服务地址"))
        }

        val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "http://$trimmed"
        // 去掉末尾斜杠：NetworkClient.buildRetrofit 会自行补 '/'，否则会拼出双斜杠
        val candidate = withScheme.trimEnd('/')

        val uri = runCatching { candidate.toUri() }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.US)
        if (uri == null || scheme.isNullOrEmpty()) {
            return Result.failure(
                IllegalArgumentException("面板地址格式不正确，示例：http://192.168.1.100:5700")
            )
        }
        if (scheme != "http" && scheme != "https") {
            return Result.failure(
                IllegalArgumentException("面板地址仅支持 http / https，当前为 $scheme://")
            )
        }
        if (uri.host.isNullOrBlank()) {
            return Result.failure(
                IllegalArgumentException("面板地址缺少主机名，示例：http://192.168.1.100:5700")
            )
        }
        val port = uri.port
        if (port != -1 && port !in 1..65535) {
            return Result.failure(IllegalArgumentException("面板地址端口不合法：$port"))
        }

        return Result.success(candidate)
    }

    /** 快速校验，供输入框实时提示使用 */
    fun isValid(raw: String): Boolean = normalize(raw).isSuccess

    /**
     * 兜底清洗：无论输入多脏，都返回一个 Retrofit **可解析**的绝对地址（不保证可连通）。
     *
     * 只用于 `Result` 表达不了失败、但抛异常就会闪退的调用点
     * （即 `PanelRepository.getAdapter()` 构造适配器的那一步）。
     * 正常流程下输入已由 [normalize] 校验过，这里几乎不会走到兜底分支。
     */
    fun sanitize(raw: String): String = normalize(raw).getOrElse {
        val trimmed = raw.replace(WHITESPACE, "").trimEnd('/')
        val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "http://$trimmed"
        if (isValid(withScheme)) withScheme else FALLBACK_URL
    }
}
