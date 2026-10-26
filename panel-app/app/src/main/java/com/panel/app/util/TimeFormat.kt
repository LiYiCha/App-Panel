package com.panel.app.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一的短时间戳展示格式（任务卡最近执行时间 / cron 下次运行等共用）：
 * 今天 → "HH:mm"，更早 → "MM-dd HH:mm"，非法/未执行 → "-"。
 * 兼容秒级与毫秒级时间戳（<1e10 视为秒）。
 */
object TimeFormat {

    fun short(timestamp: Long?): String {
        val t = timestamp ?: return "-"
        if (t <= 0L) return "-"
        return shortMillis(if (t < 10_000_000_000L) t * 1000 else t)
    }

    fun shortMillis(millis: Long): String {
        if (millis <= 0L) return "-"
        val pattern = if (DateUtils.isToday(millis)) "HH:mm" else "MM-dd HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }
}
