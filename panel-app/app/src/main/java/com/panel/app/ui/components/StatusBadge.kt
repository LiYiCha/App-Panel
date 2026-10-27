package com.panel.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 状态徽标色调：与 Material 容器色一一对应，禁用一律中性灰（"关着"不是"坏了"） */
enum class StatusTone { PRIMARY, SECONDARY, TERTIARY, NEUTRAL, ERROR }

/**
 * 三处状态徽标的公共实现（任务卡 / 变量卡 / 任务详情）：
 * 圆角小徽标 + 可选脉冲点（运行中）。已启用=绿、已禁用=灰、运行中=主色脉冲。
 */
@Composable
fun StatusBadge(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val (container, content) = when (tone) {
        StatusTone.PRIMARY ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.SECONDARY ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        StatusTone.TERTIARY ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.ERROR ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Surface(color = container, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            if (pulsing) {
                PulsingDot(color = content)
            }
            icon?.let {
                androidx.compose.material3.Icon(
                    it,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(9.dp)
                )
            }
            Text(text, fontSize = 9.sp, color = content)
        }
    }
}

/** 执行历史成功/失败徽标：退出码 0 → 成功(绿)，否则失败(红)并带退出码 */
@Composable
fun ExitCodeBadge(isSuccess: Boolean, exitCode: Int, modifier: Modifier = Modifier) {
    StatusBadge(
        text = if (isSuccess) "成功" else "失败($exitCode)",
        tone = if (isSuccess) StatusTone.PRIMARY else StatusTone.ERROR,
        modifier = modifier
    )
}

/** 双层脉冲点：外圈扩散 + 内芯实心（运行中状态专用） */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulseDot")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    Canvas(modifier = modifier.size(8.dp)) {
        val r = size.minDimension / 2
        drawCircle(
            color = color.copy(alpha = (1f - progress) * 0.35f),
            radius = r * (1f + progress),
            center = center
        )
        drawCircle(color = color, radius = r * 0.5f, center = center)
    }
}
