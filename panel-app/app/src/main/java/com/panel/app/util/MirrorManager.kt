package com.panel.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 下载加速镜像的统一管理中心（简化版：源列表 + 切换 + 自定义增删）。
 *
 * 管理两类镜像源：
 *  - [Type.UPDATE]：应用内更新 APK 下载（GitHub Release 直链的镜像前缀加速）
 *  - [Type.PYPI]  ：Python 依赖 wheel 下载（files.pythonhosted.org 的域名替换加速），
 *                    由 :core-script 的 PyDepManager 通过共享 SharedPreferences 读取
 *
 * 存储使用固定的 SP 文件名与 key；core-script 侧（standard 变体不含本类）以相同 key 约定读取，
 * 两处修改任一处的 key 时必须同步另一处。
 */
object MirrorManager {

    enum class Type { UPDATE, PYPI }

    /**
     * @param url 语义随类型不同：
     *  - UPDATE：镜像前缀（直接拼接在原始 GitHub URL 之前），空串表示 GitHub 直连
     *  - PYPI  ：替换 files.pythonhosted.org 的源站地址（如 https://pypi.tuna.tsinghua.edu.cn）
     */
    data class MirrorSource(
        val id: String,
        val name: String,
        val type: Type,
        val url: String,
        val isPreset: Boolean,
    )

    // ── 预设源 ─────────────────────────────────────────────────────────────

    val PRESET_UPDATE_DIRECT = MirrorSource("preset_update_direct", "GitHub 直连", Type.UPDATE, "", true)
    val PRESET_UPDATE_GHFAST = MirrorSource("preset_update_ghfast", "ghfast 加速", Type.UPDATE, "https://ghfast.top/", true)
    val PRESET_UPDATE_GHPROXY_NET = MirrorSource("preset_update_ghproxy_net", "ghproxy 加速", Type.UPDATE, "https://ghproxy.net/", true)
    val PRESET_UPDATE_GH_PROXY = MirrorSource("preset_update_gh_proxy", "gh-proxy 加速", Type.UPDATE, "https://gh-proxy.com/", true)

    val PRESET_PYPI_OFFICIAL = MirrorSource(
        "preset_pypi_official", "PyPI 官方", Type.PYPI, "https://files.pythonhosted.org", true
    )
    val PRESET_PYPI_TUNA = MirrorSource(
        "preset_pypi_tuna", "清华 TUNA", Type.PYPI, "https://pypi.tuna.tsinghua.edu.cn", true
    )

    private val PRESETS: Map<Type, List<MirrorSource>> = mapOf(
        Type.UPDATE to listOf(PRESET_UPDATE_GHFAST, PRESET_UPDATE_GHPROXY_NET, PRESET_UPDATE_GH_PROXY, PRESET_UPDATE_DIRECT),
        Type.PYPI to listOf(PRESET_PYPI_TUNA, PRESET_PYPI_OFFICIAL),
    )

    // ── 共享存储约定（core-script 的 PyMirrorPrefs 使用相同文件与 key）────────

    const val PREFS_NAME = "mirror_settings"
    const val KEY_UPDATE_SELECTED = "mirror_update_selected"
    const val KEY_PYPI_SELECTED = "mirror_pypi_selected"
    const val KEY_CUSTOM_LIST = "mirror_custom_json"

    /** PyPI 官方 wheel 域名（PyDepManager 与本类共同使用的替换源） */
    const val PYPI_OFFICIAL_HOST = "https://files.pythonhosted.org"

    /** GitHub Release 直链前缀（仅这类 URL 会被镜像加速） */
    const val GITHUB_DIRECT_PREFIX = "https://github.com/"

    private var prefs: SharedPreferences? = null

    /** 在 Application.onCreate 中调用 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── 查询 ───────────────────────────────────────────────────────────────

    /** 全部源 = 预设 + 自定义 */
    fun sources(type: Type): List<MirrorSource> {
        return PRESETS[type].orEmpty() + customSources(type)
    }

    /** 当前选中源（无记录时取预设第一个） */
    fun selected(type: Type): MirrorSource {
        val key = when (type) {
            Type.UPDATE -> KEY_UPDATE_SELECTED
            Type.PYPI -> KEY_PYPI_SELECTED
        }
        val selectedId = prefs?.getString(key, null)
        val all = sources(type)
        return all.firstOrNull { it.id == selectedId } ?: all.first()
    }

    private fun customSources(type: Type): List<MirrorSource> {
        val raw = prefs?.getString(KEY_CUSTOM_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                if (obj.optString("type") != type.name) return@mapNotNull null
                MirrorSource(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    type = type,
                    url = obj.optString("url"),
                    isPreset = false,
                )
            }
        }.getOrDefault(emptyList())
    }

    // ── 变更 ───────────────────────────────────────────────────────────────

    fun select(type: Type, id: String) {
        val key = when (type) {
            Type.UPDATE -> KEY_UPDATE_SELECTED
            Type.PYPI -> KEY_PYPI_SELECTED
        }
        prefs?.edit()?.putString(key, id)?.apply()
    }

    /**
     * 添加自定义源。
     * @return 成功 true；失败 false（名称/URL 非法）
     */
    fun addCustom(type: Type, name: String, url: String): Boolean {
        val trimmedName = name.trim()
        var trimmedUrl = url.trim().trimEnd('/')
        if (trimmedName.isEmpty() || trimmedName.length > 30) return false
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) return false
        if (type == Type.UPDATE && !trimmedUrl.endsWith("/")) trimmedUrl += "/"

        val arr = readAllCustom()
        val id = "custom_${System.currentTimeMillis()}"
        arr.put(
            JSONObject()
                .put("id", id)
                .put("name", trimmedName)
                .put("type", type.name)
                .put("url", trimmedUrl)
        )
        prefs?.edit()?.putString(KEY_CUSTOM_LIST, arr.toString())?.apply()
        return true
    }

    /** 删除自定义源；预设源不可删。若删除的是当前选中源，回落到预设第一个 */
    fun removeCustom(id: String) {
        val arr = readAllCustom()
        val out = JSONArray()
        var removed = false
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) {
                removed = true
                continue
            }
            out.put(obj)
        }
        if (!removed) return
        prefs?.edit()?.putString(KEY_CUSTOM_LIST, out.toString())?.apply()
        Type.entries.forEach { type ->
            if (selected(type).id == id) {
                select(type, PRESETS[type].orEmpty().first().id)
            }
        }
    }

    private fun readAllCustom(): JSONArray {
        val raw = prefs?.getString(KEY_CUSTOM_LIST, null)
        return runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray())
    }

    // ── URL 应用逻辑（供下载器调用）────────────────────────────────────────

    /**
     * 应用「更新下载」镜像：仅对 GitHub Release 直链生效。
     * 选中直连（url 为空）时原样返回。
     */
    fun applyUpdate(url: String): String {
        val source = selected(Type.UPDATE)
        if (source.url.isEmpty()) return url
        return if (url.startsWith(GITHUB_DIRECT_PREFIX)) source.url + url else url
    }

    /** 当前 PyPI wheel 镜像源站（供 core-script 通过 provider 读取） */
    fun selectedPyPiOrigin(): String = selected(Type.PYPI).url.ifEmpty { PYPI_OFFICIAL_HOST }
}
