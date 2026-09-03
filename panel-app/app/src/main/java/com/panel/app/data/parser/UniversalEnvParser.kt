package com.panel.app.data.parser

import com.google.gson.*
import com.panel.app.data.model.UnifiedEnv
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.regex.Pattern

object UniversalEnvParser {

    // 自定义解析规则列表（可通过网络加载）
    private val customRules = mutableListOf<Rule>()

    data class Rule(
        val keyRegex: String = ".*",
        val splitChar: String = ";"
    )

    /** 从网络加载自定义解析规则，追加到内置规则之前 */
    fun loadCustomRules(rules: List<Rule>) {
        synchronized(customRules) {
            customRules.addAll(0, rules)
        }
    }

    /** 获取当前所有规则（内置+自定义） */
    fun getAllRules(): List<Rule> = synchronized(customRules) { customRules.toList() }

    /**
     * 还原 HTML 转义实体 (如 &#38;, &amp;, &#34;, &#61;) 与 URL Percent 编码
     */
    fun sanitizeRawString(rawText: String?): String {
        if (rawText.isNullOrEmpty()) return ""
        var str = rawText
            .replace("&#38;", "&")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#61;", "=")
            .replace("&#59;", ";")

        try {
            if (str.contains("%3D", ignoreCase = true) || str.contains("%26", ignoreCase = true)) {
                str = URLDecoder.decode(str, StandardCharsets.UTF_8.name())
            }
        } catch (_: Exception) {
            // 忽略转义异常
        }
        return str
    }

    /**
     * 智能多格式环境变量解析函数 (支持 JSON 导入 / 裸 Cookie / export / 多行跨引号 / @ 账号拆解)
     */
    fun parseText(inputText: String, splitAtAccount: Boolean): List<UnifiedEnv> {
        val result = mutableListOf<UnifiedEnv>()
        val sanitized = sanitizeRawString(inputText).trim()
        if (sanitized.isEmpty()) return emptyList()

        // 1. 优先尝试解析官方标准导出的 JSON 数组格式: [{"name":"...", "value":"...", "remarks":"..."}]
        if (sanitized.startsWith("[") && sanitized.endsWith("]")) {
            try {
                val element = JsonParser.parseString(sanitized)
                if (element.isJsonArray) {
                    element.asJsonArray.forEach { item ->
                        if (item.isJsonObject) {
                            val obj = item.asJsonObject
                            val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val value = obj.get("value")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val remarks = obj.get("remarks")?.takeIf { !it.isJsonNull }?.asString ?: "JSON导入"
                            if (name.isNotEmpty()) {
                                result.add(
                                    UnifiedEnv(
                                        id = UUID.randomUUID().toString(),
                                        name = name,
                                        value = value,
                                        remarks = remarks,
                                        enabled = true
                                    )
                                )
                            }
                        }
                    }
                    if (result.isNotEmpty()) return result
                }
            } catch (_: Exception) {}
        }

        // 1.5 URL Query 格式: app=mdwz&dataEncStr=xxx 或含 & 值的 URL（如 redirect=https://a.com?a=1&b=2）
        // 关键：不能盲目按 & 拆分，因为 value 本身可能含 &（如 URL 查询参数）。
        // 用环境变量名模式 [A-Za-z_][A-Za-z0-9_]*= 来定位 key 边界，确保只在合法 key 前切分。
        if (!sanitized.startsWith("export ", ignoreCase = true) &&
            Regex("""[A-Za-z_][A-Za-z0-9_]*=""").containsMatchIn(sanitized)
        ) {
            val keyPattern = Regex("""([A-Za-z_][A-Za-z0-9_]*)=(.*?)(?=[A-Za-z_][A-Za-z0-9_]*=|$)""")
            val matches = keyPattern.findAll(sanitized)
            val pairs = matches.mapNotNull { m ->
                val key = m.groupValues[1].trim()
                val value = m.groupValues[2].trim()
                if (key.isNotEmpty()) Pair(key, value) else null
            }.toList()
            if (pairs.size >= 2) {
                pairs.forEach { (k, v) ->
                    result.add(
                        UnifiedEnv(
                            id = UUID.randomUUID().toString(),
                            name = k,
                            value = v,
                            remarks = "URL参数导入",
                            enabled = true
                        )
                    )
                }
                return result
            }
        }

        // 2. 裸 Cookie 检测 (如用户直接粘贴了 pt_key=xxx; pt_pin=yyy; 但没有写 export JD_COOKIE=)
        val isRawJdCookie = (sanitized.contains("pt_key=") || sanitized.contains("pt_pin=")) &&
                !sanitized.startsWith("export ", ignoreCase = true) &&
                Regex("""^[A-Za-z0-9_]+\s*=""").find(sanitized)?.value?.startsWith("pt_") == true
        if (isRawJdCookie) {
            if (splitAtAccount && (sanitized.contains("\n") || sanitized.contains("&") || sanitized.contains("@"))) {
                val lines = sanitized.split(Regex("""[\r\n&@]+""")).filter { it.contains("pt_key=") || it.contains("pt_pin=") }
                lines.forEachIndexed { idx, line ->
                    val clean = line.trim().trim(';').trim()
                    if (clean.isNotEmpty()) {
                        result.add(
                            UnifiedEnv(
                                id = UUID.randomUUID().toString(),
                                name = "JD_COOKIE",
                                value = clean,
                                remarks = "智能识别京东Cookie #${idx + 1}",
                                enabled = true
                            )
                        )
                    }
                }
                if (result.isNotEmpty()) return result
            } else {
                result.add(
                    UnifiedEnv(
                        id = UUID.randomUUID().toString(),
                        name = "JD_COOKIE",
                        value = sanitized.trim(';').trim(),
                        remarks = "智能识别京东Cookie",
                        enabled = true
                    )
                )
                return result
            }
        }

        // 3. 增强版正则：支持跨行双引号、单引号包围的 Shell export 或 KEY=VALUE
        val envPattern = Pattern.compile("""(?m)^(?:\s*export\s+)?([A-Za-z0-9_]+)\s*=\s*(?:("[\s\S]*?"|'[\s\S]*?')|([^\r\n]+))""")
        val matcher = envPattern.matcher(sanitized)

        while (matcher.find()) {
            val name = matcher.group(1)?.trim() ?: continue
            var rawVal = (matcher.group(2) ?: matcher.group(3) ?: "").trim()
            if ((rawVal.startsWith("\"") && rawVal.endsWith("\"")) || (rawVal.startsWith("'") && rawVal.endsWith("'"))) {
                if (rawVal.length >= 2) {
                    rawVal = rawVal.substring(1, rawVal.length - 1)
                }
            }

            if (name.isNotEmpty()) {
                if (splitAtAccount && (rawVal.contains("&") || rawVal.contains("@")) && !rawVal.startsWith("[") && !rawVal.startsWith("{")) {
                    val delimiter = if (rawVal.contains("@")) "@" else "&"
                    val subItems = rawVal.split(delimiter)
                    subItems.forEachIndexed { index, sub ->
                        val subTrim = sub.trim()
                        if (subTrim.isNotEmpty()) {
                            result.add(
                                UnifiedEnv(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    value = subTrim,
                                    remarks = "拆解账号 #${index + 1}",
                                    enabled = true
                                )
                            )
                        }
                    }
                } else {
                    result.add(
                        UnifiedEnv(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            value = rawVal,
                            remarks = "智能解析导入",
                            enabled = true
                        )
                    )
                }
            }
        }

        if (result.isEmpty()) {
            // 4. 兜底按行切分
            sanitized.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.contains("=") && !trimmed.startsWith("#")) {
                    val idx = trimmed.indexOf("=")
                    val k = trimmed.substring(0, idx).replace(Regex("^export\\s+"), "").trim()
                    val v = trimmed.substring(idx + 1).replace(Regex("^[\"']|[\"']$"), "").trim()
                    if (k.isNotEmpty()) {
                        result.add(
                            UnifiedEnv(
                                id = UUID.randomUUID().toString(),
                                name = k,
                                value = v,
                                remarks = "行匹配导入",
                                enabled = true
                            )
                        )
                    }
                }
            }
        }

        return result
    }

    /**
     * 针对 JSON（[{"id":"..."}]）、URL Query（app=mdwz&dataEncStr=...）、Cookie（pt_key=...;pt_pin=...;）与单值变量的自适应解析
     */
    fun parseSubItems(envName: String, value: String): List<Pair<String, String>> {
        val sanitized = sanitizeRawString(value).trim()
        val list = mutableListOf<Pair<String, String>>()

        // 1. JSON 格式检测 (支持 JSON 数组与 JSON 对象)
        if ((sanitized.startsWith("[") && sanitized.endsWith("]")) || (sanitized.startsWith("{") && sanitized.endsWith("}"))) {
            try {
                val element = JsonParser.parseString(sanitized)
                if (element.isJsonArray) {
                    val array = element.asJsonArray
                    if (array.size() == 1 && array[0].isJsonObject) {
                        // 单账号 JSON 数组包装格式: [{"id":"5499469","token":"..."}]
                        val obj = array[0].asJsonObject
                        for ((k, v) in obj.entrySet()) {
                            val strVal = if (v.isJsonPrimitive) v.asJsonPrimitive.asString else v.toString()
                            list.add(Pair(k, strVal))
                        }
                        return list
                    } else {
                        array.forEachIndexed { idx, item ->
                            if (item.isJsonObject) {
                                for ((k, v) in item.asJsonObject.entrySet()) {
                                    val strVal = if (v.isJsonPrimitive) v.asJsonPrimitive.asString else v.toString()
                                    list.add(Pair("[$idx].$k", strVal))
                                }
                            } else {
                                val strVal = if (item.isJsonPrimitive) item.asJsonPrimitive.asString else item.toString()
                                list.add(Pair("[$idx]", strVal))
                            }
                        }
                        return list
                    }
                } else if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    for ((k, v) in obj.entrySet()) {
                        val strVal = if (v.isJsonPrimitive) v.asJsonPrimitive.asString else v.toString()
                        list.add(Pair(k, strVal))
                    }
                    return list
                }
            } catch (_: Exception) {}
        }

        // 2. URL Query 格式检测 (含 & 且不含 ;)
        // 若值本身是完整 URL（含 ://），不做拆分，整体保留
        if (sanitized.contains("&") && !sanitized.contains(";") && !sanitized.contains("://")) {
            val parts = sanitized.split("&")
            val pairs = parts.mapNotNull { part ->
                val trimmed = part.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx <= 0) return@mapNotNull null
                val k = trimmed.substring(0, eqIdx).trim()
                val v = trimmed.substring(eqIdx + 1).trim()
                if (k.isNotEmpty()) Pair(k, v) else null
            }.toList()
            if (pairs.isNotEmpty()) {
                list.addAll(pairs)
                if (list.isNotEmpty()) return list
            }
        }

        // 3. Cookie 或分号多键值变量 (如 pt_key=...; pt_pin=...)
        val parts = sanitized.split(";", "&")
        parts.forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("=")) {
                val idx = trimmed.indexOf("=")
                val k = trimmed.substring(0, idx).trim()
                val v = trimmed.substring(idx + 1).trim()
                if (k.isNotEmpty()) {
                    list.add(Pair(k, v))
                }
            }
        }

        // 4. 单字段变量防护
        if (list.isEmpty() && sanitized.isNotEmpty()) {
            list.add(Pair(envName, sanitized))
        }

        return list
    }

    /**
     * 自适应合成分段修改后的文本，根据原始数据特征无损还原
     */
    fun assembleSubItems(envName: String, pairs: List<Pair<String, String>>, originalValue: String = ""): String {
        if (pairs.isEmpty()) return ""
        val trimmedOrig = sanitizeRawString(originalValue).trim()

        // 1. 单字段特例
        if (pairs.size == 1 && pairs[0].first == envName && !pairs[0].second.contains("=")) {
            return pairs[0].second
        }

        // 2. 原始为 JSON 格式还原
        if (trimmedOrig.startsWith("[") && trimmedOrig.endsWith("]")) {
            try {
                val element = JsonParser.parseString(trimmedOrig)
                if (element.isJsonArray && element.asJsonArray.size() == 1 && element.asJsonArray[0].isJsonObject) {
                    val obj = JsonObject()
                    pairs.forEach { (k, v) -> obj.addProperty(k, v) }
                    val newArray = JsonArray()
                    newArray.add(obj)
                    return newArray.toString()
                }
            } catch (_: Exception) {}
        }
        if (trimmedOrig.startsWith("{") && trimmedOrig.endsWith("}")) {
            try {
                val obj = JsonObject()
                pairs.forEach { (k, v) -> obj.addProperty(k, v) }
                return obj.toString()
            } catch (_: Exception) {}
        }

        // 3. 原始为 URL Query 格式还原 (以 & 连接)
        if (trimmedOrig.contains("&") && !trimmedOrig.contains(";")) {
            return pairs.joinToString("&") { "${it.first}=${it.second}" }
        }

        // 4. 默认以分号连接 (Cookie 格式)
        return pairs.joinToString(";") { "${it.first}=${it.second}" }
    }
}
