package com.panel.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.SortedSet
import java.util.TreeSet

/**
 * 极简 5 字段 cron 下次执行时间计算器。
 *
 * 背景：青龙 API 不下发任务下次运行时间（UnifiedTask.nextRunTime 仅白虎面板有值），
 * 详情页此前只能显示"等待调度服务计算"占位文案。本计算器按 cron 表达式本地推算，
 * 作为青龙任务的兜底显示。
 *
 * 支持：* , - / 与数字；dom 与 dow 同时受限时按标准 cron 语义取并集（OR）。
 * 不支持：@reboot 等宏、秒域、英文月/星期缩写（解析失败返回 null，由调用方兜底）。
 */
object CronNextRun {

    /** 计算自 now 起的下次执行时间；无法解析或四年内无匹配返回 null */
    fun computeNext(schedule: String, now: Calendar = Calendar.getInstance()): String? =
        computeNextMillis(schedule, now)?.let { TimeFormat.shortMillis(it) }

    /** 同上但返回时间戳（毫秒），供调用方自行格式化 */
    fun computeNextMillis(schedule: String, now: Calendar = Calendar.getInstance()): Long? {
        var parts = schedule.trim().split("\\s+".toRegex())
        if (parts.size < 5) return null
        // 青龙使用 6 域 cron（秒 分 时 日 月 周，如 "10 12 10 * * *" = 每天 10:12:10）；
        // 超过 5 域时丢弃前面的秒域，取最后 5 域（分 时 日 月 周）
        if (parts.size > 5) parts = parts.takeLast(5)

        val minutes = expand(parts[0], 0, 59) ?: return null
        val hours = expand(parts[1], 0, 23) ?: return null
        val doms = expand(parts[2], 1, 31) ?: return null
        val months = expand(parts[3], 1, 12) ?: return null
        // 星期：0/7=周日，统一折到 0-6
        val dowsRaw = expand(parts[4], 0, 7) ?: return null
        val dows = buildSet {
            dowsRaw.forEach { add(if (it == 7) 0 else it) }
        }

        val domRestricted = parts[2] != "*"
        val dowRestricted = parts[4] != "*"

        fun dayMatches(dom: Int, month: Int, dow: Int): Boolean {
            if (month !in months) return false
            return when {
                !domRestricted && !dowRestricted -> true
                domRestricted && !dowRestricted -> dom in doms
                !domRestricted && dowRestricted -> dow in dows
                else -> dom in doms || dow in dows
            }
        }

        val start = (now.clone() as Calendar).apply {
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val cursor = start.clone() as Calendar
        // 最多向后找 4 年（覆盖闰年 + 年任务）
        repeat(366 * 4) {
            if (dayMatches(
                    cursor.get(Calendar.DAY_OF_MONTH),
                    cursor.get(Calendar.MONTH) + 1,
                    cursor.get(Calendar.DAY_OF_WEEK) - 1 // Calendar: 1=Sun..7=Sat → cron 0-6
                )
            ) {
                val sameDay = cursor.get(Calendar.YEAR) == start.get(Calendar.YEAR) &&
                        cursor.get(Calendar.DAY_OF_YEAR) == start.get(Calendar.DAY_OF_YEAR)
                val hourLimit = if (sameDay) start.get(Calendar.HOUR_OF_DAY) else 23
                val minuteLimit = if (sameDay) start.get(Calendar.MINUTE) else 59

                for (h in hours) {
                    if (h < hourLimit) continue
                    val m = minutes.firstOrNull { h > hourLimit || it >= minuteLimit }
                    if (m != null) {
                        return (cursor.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, m)
                        }.timeInMillis
                    }
                }
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1)
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
        }
        return null
    }

    /** 展开单个 cron 域为合法值集合；非法返回 null */
    private fun expand(field: String, min: Int, max: Int): SortedSet<Int>? {
        val out = TreeSet<Int>()
        for (token in field.split(',')) {
            if (token.isEmpty()) return null
            val (rangePart, step) = if (token.contains('/')) {
                val seg = token.split('/')
                if (seg.size != 2) return null
                seg[0] to (seg[1].toIntOrNull() ?: return null)
            } else {
                token to 1
            }
            if (step <= 0) return null

            val lo: Int
            val hi: Int
            when {
                rangePart == "*" -> { lo = min; hi = max }
                rangePart.contains('-') -> {
                    val seg = rangePart.split('-')
                    if (seg.size != 2) return null
                    lo = seg[0].toIntOrNull() ?: return null
                    hi = seg[1].toIntOrNull() ?: return null
                }
                else -> {
                    // 单值：允许 "5/2" 语义（从 5 起步进 2 到 max）
                    lo = rangePart.toIntOrNull() ?: return null
                    hi = if (token.contains('/')) max else lo
                }
            }
            if (lo < min || hi > max || lo > hi) return null
            var v = lo
            while (v <= hi) {
                out.add(v)
                v += step
            }
        }
        return if (out.isEmpty()) null else out
    }
}
