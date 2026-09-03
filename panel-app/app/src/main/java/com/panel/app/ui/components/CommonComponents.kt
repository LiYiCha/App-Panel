package com.panel.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 带文字标签的操作按钮，用于任务详情页和订阅列表等场景
 */
@Composable
fun ActionButtonSmall(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(36.dp)
        )
    }
}

/**
 * 解析 cron 表达式为自然中文描述，如 "每天10点59分"。
 * 优先级：分钟/小时精确值 > 频率模式 > 原始返回。
 */
fun parseCronDescription(cron: String): String {
    if (cron.isBlank()) return "未设置"
    val parts = cron.trim().split("\\s+".toRegex())
    if (parts.size < 5) return cron

    val (minute, hour, dayOfMonth, month, dayOfWeek) = parts

    // 精确分钟+小时：如 "59 10 * * *" → "每天10点59分"
    if (minute != "*" && hour != "*" && dayOfMonth == "*" && month == "*") {
        val h = hour.toIntOrNull() ?: 0
        val m = minute.toIntOrNull() ?: 0
        return when {
            h == 0 && m == 0 -> "每天零点整"
            h == 0 -> "每天0点${m}分"
            h < 10 -> "每天上午${h}点${m}分"
            h < 12 -> "每天上午${h}点${m}分"
            h == 12 && m == 0 -> "每天中午12点整"
            h < 18 -> "每天下午${h - 12}点${m}分"
            h < 21 -> "每天晚上${h - 12}点${m}分"
            h < 24 -> "每天深夜${h - 12}点${m}分"
            else -> "每天${h}点${m}分"
        }
    }

    // 仅小时精确：如 "0 10 * * *" → "每天10点整"
    if (minute == "*" && hour != "*" && dayOfMonth == "*" && month == "*") {
        val h = hour.toIntOrNull() ?: 0
        return when {
            h == 0 -> "每天零点整"
            h < 10 -> "每天上午${h}点整"
            h < 12 -> "每天上午${h}点整"
            h == 12 -> "每天中午12点整"
            h < 18 -> "每天下午${h - 12}点整"
            h < 21 -> "每天晚上${h - 12}点整"
            h < 24 -> "每天深夜${h - 12}点整"
            else -> "每天${h}点整"
        }
    }

    // 步长模式：每N分钟 / 每N小时
    if (minute.contains("/") && hour == "*" && dayOfMonth == "*" && month == "*") {
        val n = minute.split("/")[1].toIntOrNull() ?: 0
        return if (n <= 1) "每分钟" else "每${n}分钟"
    }
    if (hour.contains("/") && minute == "*" && dayOfMonth == "*" && month == "*") {
        val n = hour.split("/")[1].toIntOrNull() ?: 0
        return if (n <= 1) "每小时" else "每${n}小时"
    }

    // 常规构建（兜底）
    return buildString {
        if (minute != "*") append(formatTimeField(minute, "分钟")) else append("每分钟")
        if (hour != "*") append(" ${formatTimeField(hour, "小时")}") else append(" 每小时")
        if (dayOfMonth != "*") append(" 每月${dayOfMonth}日") else append(" 每日")
        if (month != "*") append(" ${formatTimeField(month, "月")}")
        if (dayOfWeek != "*" && dayOfWeek != "") append(" 周${formatDayOfWeek(dayOfWeek)}")
    }.trim()
}

private fun formatTimeField(field: String, unit: String): String {
    return when {
        field == "*" -> "每$unit"
        field.contains("/") -> {
            val parts = field.split("/")
            val start = parts[0].toIntOrNull() ?: 0
            val step = parts[1].toIntOrNull() ?: 1
            if (start == 0) "每${step}${unit}" else "每${step}${unit}从${start}${unit}起"
        }
        field.contains("-") -> {
            val parts = field.split("-")
            "${parts[0]}${unit}到${parts[1]}${unit}"
        }
        field.contains(",") -> {
            field.split(",").joinToString("、") { "$it$unit" }
        }
        field.toIntOrNull() != null -> {
            "$field$unit"
        }
        else -> field
    }
}

private fun formatDayOfWeek(day: String): String {
    return when (day.toIntOrNull()) {
        0 -> "日"
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> day
    }
}
