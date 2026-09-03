package com.panel.app.util

/**
 * 轻量、高效且无需外部依赖的 Cron 表达式中文自然语言描述解析器。
 * 支持：
 * - 5 位标准格式：分 时 日 月 周
 * - 6 位秒级格式：秒 分 时 日 月 周（青龙、白虎面板常用）
 */
object CronExpressionDescriber {

    data class CronPreset(val label: String, val expression: String)

    val PRESETS_6_PART = listOf(
        CronPreset("每5秒", "*/5 * * * * *"),
        CronPreset("每30秒", "*/30 * * * * *"),
        CronPreset("每分钟", "0 * * * * *"),
        CronPreset("每5分钟", "0 */5 * * * *"),
        CronPreset("每小时", "0 0 * * * *"),
        CronPreset("每天0点", "0 0 0 * * *"),
        CronPreset("每天8点", "0 0 8 * * *"),
        CronPreset("每周一0点", "0 0 0 * * 1"),
        CronPreset("每月1号0点", "0 0 0 1 * *")
    )

    val PRESETS_5_PART = listOf(
        CronPreset("每分钟", "* * * * *"),
        CronPreset("每5分钟", "*/5 * * * *"),
        CronPreset("每15分钟", "*/15 * * * *"),
        CronPreset("每小时整点", "0 * * * *"),
        CronPreset("每天0点", "0 0 * * *"),
        CronPreset("每天8点", "0 8 * * *"),
        CronPreset("每周一8点", "0 8 * * 1"),
        CronPreset("每月1号8点", "0 8 1 * *")
    )

    /**
     * 将 Cron 表达式解析为人类可读的中文描述。
     * 若解析失败或格式未知，返回空字符串。
     */
    fun describe(expression: String?): String {
        if (expression.isNullOrBlank()) return ""
        val trimmed = expression.trim().replace("\\s+".toRegex(), " ")
        val parts = trimmed.split(" ")
        if (parts.size != 5 && parts.size != 6) return ""

        return try {
            if (parts.size == 6) {
                describe6Part(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
            } else {
                describe5Part(parts[0], parts[1], parts[2], parts[3], parts[4])
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun describe6Part(sec: String, min: String, hour: String, dom: String, mon: String, dow: String): String {
        if (sec.startsWith("*/") && min == "*" && hour == "*" && dom == "*" && mon == "*" && dow.matches(Regex("[*?]"))) {
            val s = sec.substringAfter("*/")
            return "每 $s 秒执行一次"
        }
        if (sec == "0" && min.startsWith("*/") && hour == "*" && dom == "*" && mon == "*" && dow.matches(Regex("[*?]"))) {
            val m = min.substringAfter("*/")
            return "每 $m 分钟执行一次"
        }
        if (sec == "0" && min == "0" && hour.startsWith("*/") && dom == "*" && mon == "*" && dow.matches(Regex("[*?]"))) {
            val h = hour.substringAfter("*/")
            return "每 $h 小时整点执行"
        }

        val sb = StringBuilder()
        if (mon != "*" && mon != "?") {
            sb.append(parseListOrRange(mon, "月")).append(" ")
        }
        val hasDom = dom != "*" && dom != "?"
        val hasDow = dow != "*" && dow != "?"
        if (hasDom && hasDow) {
            sb.append(parseDom(dom)).append(" 且 ").append(parseDow(dow)).append(" ")
        } else if (hasDom) {
            sb.append(parseDom(dom)).append(" ")
        } else if (hasDow) {
            sb.append(parseDow(dow)).append(" ")
        } else if (mon == "*" || mon == "?") {
            if (hour != "*") {
                sb.append("每天 ")
            }
        }

        val timeDesc = formatTime6(sec, min, hour)
        sb.append(timeDesc)

        val result = sb.toString().trim()
        return if (result.isNotEmpty()) "$result 执行" else ""
    }

    private fun describe5Part(min: String, hour: String, dom: String, mon: String, dow: String): String {
        if (min.startsWith("*/") && hour == "*" && dom == "*" && mon == "*" && dow.matches(Regex("[*?]"))) {
            val m = min.substringAfter("*/")
            return "每 $m 分钟执行一次"
        }
        if (min == "0" && hour.startsWith("*/") && dom == "*" && mon == "*" && dow.matches(Regex("[*?]"))) {
            val h = hour.substringAfter("*/")
            return "每 $h 小时整点执行"
        }

        val sb = StringBuilder()
        if (mon != "*" && mon != "?") {
            sb.append(parseListOrRange(mon, "月")).append(" ")
        }
        val hasDom = dom != "*" && dom != "?"
        val hasDow = dow != "*" && dow != "?"
        if (hasDom && hasDow) {
            sb.append(parseDom(dom)).append(" 且 ").append(parseDow(dow)).append(" ")
        } else if (hasDom) {
            sb.append(parseDom(dom)).append(" ")
        } else if (hasDow) {
            sb.append(parseDow(dow)).append(" ")
        } else if (mon == "*" || mon == "?") {
            if (hour != "*") {
                sb.append("每天 ")
            }
        }

        val timeDesc = formatTime5(min, hour)
        sb.append(timeDesc)

        val result = sb.toString().trim()
        return if (result.isNotEmpty()) "$result 执行" else ""
    }

    private fun formatTime6(sec: String, min: String, hour: String): String {
        return when {
            hour == "*" && min == "*" && sec == "*" -> "每秒"
            hour == "*" && min == "*" && sec.all { it.isDigit() } -> "每分钟的第 $sec 秒"
            hour == "*" && min.all { it.isDigit() } && sec.all { it.isDigit() } ->
                String.format(java.util.Locale.US, "每小时的 %02d:%02d", min.toInt(), sec.toInt())
            hour.all { it.isDigit() } && min.all { it.isDigit() } && sec.all { it.isDigit() } ->
                String.format(java.util.Locale.US, "%02d:%02d:%02d", hour.toInt(), min.toInt(), sec.toInt())
            else -> {
                val hStr = if (hour == "*") "每小时" else "${hour}点"
                val mStr = if (min == "*") "每分" else "${min}分"
                val sStr = if (sec == "0") "" else "${sec}秒"
                "$hStr $mStr $sStr".trim()
            }
        }
    }

    private fun formatTime5(min: String, hour: String): String {
        return when {
            hour == "*" && min == "*" -> "每分钟"
            hour == "*" && min.all { it.isDigit() } ->
                String.format(java.util.Locale.US, "每小时第 %02d 分钟", min.toInt())
            hour.all { it.isDigit() } && min.all { it.isDigit() } ->
                String.format(java.util.Locale.US, "%02d:%02d", hour.toInt(), min.toInt())
            else -> {
                val hStr = if (hour == "*") "每小时" else "${hour}点"
                val mStr = if (min == "*") "每分" else "${min}分"
                "$hStr $mStr".trim()
            }
        }
    }

    private fun parseDom(dom: String): String {
        if (dom.contains("-")) {
            val parts = dom.split("-")
            return "每月 ${parts[0]}号至${parts[1]}号"
        }
        if (dom.contains(",")) {
            val items = dom.split(",").joinToString("号、")
            return "每月 ${items}号"
        }
        if (dom.all { it.isDigit() }) {
            return "每月 ${dom}号"
        }
        return "每月的第 $dom 天"
    }

    private fun parseDow(dow: String): String {
        val weekMap = mapOf(
            "0" to "日", "7" to "日", "1" to "一", "2" to "二",
            "3" to "三", "4" to "四", "5" to "五", "6" to "六"
        )
        if (dow.contains("-")) {
            val parts = dow.split("-")
            val s = weekMap[parts[0]] ?: parts[0]
            val e = weekMap[parts[1]] ?: parts[1]
            return "每周$s 至 周$e"
        }
        if (dow.contains(",")) {
            val items = dow.split(",").map { weekMap[it] ?: it }.joinToString("、")
            return "每周$items"
        }
        val single = weekMap[dow] ?: dow
        return "每周$single"
    }

    private fun parseListOrRange(expr: String, suffix: String): String {
        if (expr.contains("-")) {
            val p = expr.split("-")
            return "${p[0]}至${p[1]}$suffix"
        }
        if (expr.contains(",")) {
            val items = expr.split(",").joinToString("、")
            return "$items$suffix"
        }
        return "$expr$suffix"
    }
}
