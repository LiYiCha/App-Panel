package com.panel.app.ui.screens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// 分段规则枚举值（规则页与分段弹窗共用）
const val ACCT_NONE = "none"
const val ACCT_CUSTOM = "custom"
const val FIELD_AUTO = "auto"
const val FIELD_CUSTOM = "custom"

/**
 * 一条分段规则：账号层分隔（acct）+ 账号内字段分隔（field），与具体变量解耦。
 * 变量通过 bindings 绑定规则，一个变量最多绑一条。
 */
data class SegRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val acct: String,
    val acctCustom: String,
    val field: String,
    val fieldCustom: String
)

// 内置分段规则模板（只读，新建时点选即填分隔，名称仍需用户输入）
val SEG_TEMPLATES = listOf(
    "单值/自动识别" to SegRule(id = "", name = "单值/自动识别", acct = ACCT_NONE, acctCustom = "", field = FIELD_AUTO, fieldCustom = ""),
    "Cookie 字段（;）" to SegRule(id = "", name = "Cookie 字段（;）", acct = ACCT_NONE, acctCustom = "", field = ";", fieldCustom = ""),
    "URL 参数（&）" to SegRule(id = "", name = "URL 参数（&）", acct = ACCT_NONE, acctCustom = "", field = "&", fieldCustom = ""),
    "键值对（=）" to SegRule(id = "", name = "键值对（=）", acct = ACCT_NONE, acctCustom = "", field = "=", fieldCustom = ""),
    "多账号换行" to SegRule(id = "", name = "多账号换行", acct = "\n", acctCustom = "", field = FIELD_AUTO, fieldCustom = ""),
    "多账号 @" to SegRule(id = "", name = "多账号 @", acct = "@", acctCustom = "", field = FIELD_AUTO, fieldCustom = ""),
    "互助码 &/@" to SegRule(id = "", name = "互助码 &/@", acct = "&", acctCustom = "", field = "@", fieldCustom = "")
)

// 内置预设分隔符符号（只是常量供 UI 展示，不入库；换行用特殊值 "\n" 表示）
val PRESET_SYM_ACCT = listOf("\n", "@", "&", ",", "|")
val PRESET_SYM_FIELD = listOf(FIELD_AUTO, ";", "&", "=", "#")

fun acctSepLabel(acct: String, acctCustom: String): String = when (acct) {
    ACCT_NONE -> "无"
    "\n" -> "换行"
    ACCT_CUSTOM -> acctCustom.trim().ifEmpty { "自定义" }
    else -> acct
}

fun fieldSepLabel(field: String, fieldCustom: String): String = when (field) {
    FIELD_AUTO -> "自动"
    FIELD_CUSTOM -> fieldCustom.trim().ifEmpty { "自定义" }
    else -> field
}

/**
 * 分段规则存储（SharedPreferences "env_seg_prefs"）：
 * - rules_v2 = JSON 数组 [{id,name,acct,acctCustom,field,fieldCustom}]
 * - bindings_v2 = JSON 对象 {变量名: 规则id}
 * - 旧格式 seg_<变量名>（JSON {a,ac,f,fc}）在读取入口统一迁移为一条规则 + 一条绑定，
 *   迁移完删除旧 key；已绑定过的变量视为迁移过（旧备份写回的冗余条目直接丢弃），迁移幂等。
 */
object SegRuleStore {
    private const val PREFS = "env_seg_prefs"
    private const val LEGACY_PREFIX = "seg_"
    const val KEY_RULES = "rules_v2"
    const val KEY_BINDINGS = "bindings_v2"
    const val KEY_SYM_ACCT = "sym_acct"
    const val KEY_SYM_FIELD = "sym_field"
    const val KEY_TPL_CUSTOM = "tpl_custom"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 读取 + 迁移的统一入口：返回 (规则表, 变量绑定)
    private fun load(context: Context): Pair<List<SegRule>, Map<String, String>> {
        val sp = prefs(context)
        val rules = parseRules(sp.getString(KEY_RULES, null)).toMutableList()
        val bindings = parseBindings(sp.getString(KEY_BINDINGS, null)).toMutableMap()
        val legacyKeys = sp.all.keys.filter { it.startsWith(LEGACY_PREFIX) }
        if (legacyKeys.isNotEmpty()) {
            val editor = sp.edit()
            legacyKeys.forEach { key ->
                val varName = key.removePrefix(LEGACY_PREFIX)
                // 已绑定（迁移过）则只清旧 key，避免重复建规则
                if (!bindings.containsKey(varName)) {
                    runCatching {
                        val o = JSONObject(sp.getString(key, "") ?: "")
                        SegRule(
                            name = varName,
                            acct = o.getString("a"),
                            acctCustom = o.optString("ac"),
                            field = o.getString("f"),
                            fieldCustom = o.optString("fc")
                        )
                    }.getOrNull()?.let {
                        rules.add(it)
                        bindings[varName] = it.id
                    }
                }
                editor.remove(key)
            }
            editor.apply()
            saveRules(context, rules)
            saveBindings(context, bindings)
        }
        return rules to bindings
    }

    /** 读取规则表（首次接触即完成旧格式迁移） */
    fun loadRules(context: Context): List<SegRule> = load(context).first

    /** 读取变量绑定表 {变量名: 规则id} */
    fun loadBindings(context: Context): Map<String, String> = load(context).second

    /** 按变量查规则：先查 bindings，无绑定返回 null */
    fun ruleFor(context: Context, envName: String): SegRule? {
        val (rules, bindings) = load(context)
        val id = bindings[envName] ?: return null
        return rules.firstOrNull { it.id == id }
    }

    /** 绑定变量到规则（同变量重复绑定以新规则覆盖） */
    fun bindRule(context: Context, envName: String, ruleId: String) {
        val (_, bindings) = load(context)
        saveBindings(context, bindings + (envName to ruleId))
    }

    /** 解绑变量（不清变量值） */
    fun unbindRule(context: Context, envName: String) {
        val (_, bindings) = load(context)
        if (envName in bindings) saveBindings(context, bindings - envName)
    }

    /** 新增或更新一条规则（不改动绑定） */
    fun upsertRule(context: Context, rule: SegRule) {
        val (rules, _) = load(context)
        val idx = rules.indexOfFirst { it.id == rule.id }
        val next = if (idx >= 0) rules.toMutableList().also { it[idx] = rule } else rules + rule
        saveRules(context, next)
    }

    /** 删除规则，同时解绑所有指向它的变量（不影响变量值） */
    fun deleteRule(context: Context, ruleId: String) {
        val (rules, bindings) = load(context)
        saveRules(context, rules.filterNot { it.id == ruleId })
        saveBindings(context, bindings.filterValues { it != ruleId })
    }

    fun saveRules(context: Context, rules: List<SegRule>) {
        prefs(context).edit().putString(KEY_RULES, serializeRules(rules)).apply()
    }

    fun saveBindings(context: Context, bindings: Map<String, String>) {
        val o = JSONObject()
        bindings.forEach { (varName, ruleId) -> o.put(varName, ruleId) }
        prefs(context).edit().putString(KEY_BINDINGS, o.toString()).apply()
    }

    /** 规则列表 → JSON 数组字符串（规则表与自定义模板共用同一序列化方式） */
    private fun serializeRules(rules: List<SegRule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("acct", r.acct)
                    .put("acctCustom", r.acctCustom)
                    .put("field", r.field)
                    .put("fieldCustom", r.fieldCustom)
            )
        }
        return arr.toString()
    }

    // ==================== 自定义符号库 ====================

    /** 读取用户自定义分隔符符号库（true=字段层 sym_field，false=账号层 sym_acct），损坏 JSON 返回空列表 */
    fun loadCustomSymbols(context: Context, field: Boolean): List<String> {
        val raw = prefs(context).getString(if (field) KEY_SYM_FIELD else KEY_SYM_ACCT, null)
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.optString(i) }
        }.getOrNull().orEmpty()
    }

    /** 追加一个自定义符号：trim 后非空且不重复才写入（内置预设不入库，见 [PRESET_SYM_ACCT] / [PRESET_SYM_FIELD]） */
    fun addCustomSymbol(context: Context, field: Boolean, sym: String) {
        val s = sym.trim()
        if (s.isEmpty()) return
        val current = loadCustomSymbols(context, field)
        if (s in current) return
        saveCustomSymbols(context, field, current + s)
    }

    /** 移除一个自定义符号（不存在时静默） */
    fun removeCustomSymbol(context: Context, field: Boolean, sym: String) {
        val current = loadCustomSymbols(context, field)
        if (sym !in current) return
        saveCustomSymbols(context, field, current - sym)
    }

    private fun saveCustomSymbols(context: Context, field: Boolean, symbols: List<String>) {
        val arr = JSONArray()
        symbols.forEach { arr.put(it) }
        prefs(context).edit()
            .putString(if (field) KEY_SYM_FIELD else KEY_SYM_ACCT, arr.toString())
            .apply()
    }

    // ==================== 自定义模板 ====================

    /** 读取自定义模板列表（与规则同一结构与序列化，存储在独立 key，损坏 JSON 返回空列表） */
    fun loadCustomTemplates(context: Context): List<SegRule> =
        parseRules(prefs(context).getString(KEY_TPL_CUSTOM, null))

    /** 新增或更新一条自定义模板（按 id 匹配；id 由 SegRule 默认 UUID 生成，与规则表不同 key 天然不冲突） */
    fun upsertCustomTemplate(context: Context, rule: SegRule) {
        val templates = loadCustomTemplates(context)
        val idx = templates.indexOfFirst { it.id == rule.id }
        val next = if (idx >= 0) templates.toMutableList().also { it[idx] = rule } else templates + rule
        prefs(context).edit().putString(KEY_TPL_CUSTOM, serializeRules(next)).apply()
    }

    /** 删除一条自定义模板（不影响规则与绑定） */
    fun deleteCustomTemplate(context: Context, id: String) {
        val next = loadCustomTemplates(context).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_TPL_CUSTOM, serializeRules(next)).apply()
    }

    /** 规则总条数（备份页展示）：rules_v2 条数 + 尚未迁移的旧 seg_* 条数 */
    fun ruleCount(context: Context): Int {
        val sp = prefs(context)
        val bindings = parseBindings(sp.getString(KEY_BINDINGS, null))
        val legacy = sp.all.keys.count {
            it.startsWith(LEGACY_PREFIX) && !bindings.containsKey(it.removePrefix(LEGACY_PREFIX))
        }
        return parseRules(sp.getString(KEY_RULES, null)).size + legacy
    }

    private fun parseRules(raw: String?): List<SegRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SegRule(
                    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    acct = o.optString("acct", ACCT_NONE),
                    acctCustom = o.optString("acctCustom"),
                    field = o.optString("field", FIELD_AUTO),
                    fieldCustom = o.optString("fieldCustom")
                )
            }
        }.getOrNull().orEmpty()
    }

    private fun parseBindings(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap {
                o.keys().forEach { key -> put(key, o.optString(key)) }
            }
        }.getOrNull().orEmpty()
    }
}
