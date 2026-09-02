package com.panel.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.LabelStat
import com.panel.app.data.model.PanelDashboard
import com.panel.app.data.model.TaskRank
import com.panel.app.data.model.TrendPoint
import com.panel.app.ui.viewmodel.MainViewModel

/**
 * 面板仪表盘（二级页面，从设置页网格进入）。
 *
 * 两个面板支持的字段不同：缺的数据留 null，对应区块整体隐藏，
 * 绝不显示伪造的 0 值。青龙字段最全；白虎没有成功/失败拆分与标签统计。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // 拦截系统返回键：不注册 BackHandler 时返回事件会被 Activity 兜底消费，
    // 表现就是"在二级页面按返回直接回到桌面"
    BackHandler { onBack() }

    var dashboard by remember { mutableStateOf(viewModel.getCachedDashboard()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun load() {
        isLoading = true
        viewModel.loadDashboard { result ->
            dashboard = result.getOrNull()
            errorMessage = result.exceptionOrNull()?.message
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val d = dashboard

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("面板仪表盘", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (d == null) errorMessage ?: "正在拉取统计..." else "数据实时来自面板后端",
                            fontSize = 10.sp,
                            color = if (errorMessage != null && d == null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (d == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isLoading) "正在拉取面板统计数据..." else errorMessage ?: "暂无统计数据",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. 概览指标
                    item { OverviewSection(d) }

                    // 2. 趋势图
                    if (d.trend.isNotEmpty()) {
                        item { TrendSection(d.trend) }
                    }

                    // 3. 排行
                    if (d.topByCount.isNotEmpty() || d.topByTime.isNotEmpty()) {
                        item { RankSection(d) }
                    }

                    // 4. 标签统计（青龙）
                    if (d.labelStats.isNotEmpty()) {
                        item { LabelSection(d.labelStats) }
                    }

                    // 5. 资源详情
                    if (d.resourceDetail.isNotEmpty()) {
                        item { ResourceSection(d.resourceDetail) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 概览

@Composable
private fun OverviewSection(d: PanelDashboard) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("概览", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            // 第一行：任务总量
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (d.totalTasks != null) StatChip("任务总数", "${d.totalTasks}", Color(0xFF3B82F6), Modifier.weight(1f))
                if (d.enabledTasks != null) StatChip("已启用", "${d.enabledTasks}", Color(0xFF10B981), Modifier.weight(1f))
                if (d.disabledTasks != null) StatChip("已禁用", "${d.disabledTasks}", Color(0xFFEF4444), Modifier.weight(1f))
            }

            // 第二行：今日执行
            if (d.todayRuns != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatChip("今日运行", "${d.todayRuns}", Color(0xFF8B5CF6), Modifier.weight(1f))
                    if (d.todaySuccess != null) {
                        StatChip("成功", "${d.todaySuccess}", Color(0xFF10B981), Modifier.weight(1f))
                    }
                    if (d.todayFail != null) {
                        StatChip("失败", "${d.todayFail}", Color(0xFFEF4444), Modifier.weight(1f))
                    }
                    if (d.successRate != null) {
                        StatChip("成功率", "${d.successRate}%", Color(0xFF3B82F6), Modifier.weight(1f))
                    }
                }
            }

            // 第三行：白虎补充的总量类指标
            val extras = buildList {
                if (d.scheduledCount != null) add(Triple("调度中", "${d.scheduledCount}", Color(0xFFF59E0B)))
                if (d.runningCount != null) add(Triple("运行中", "${d.runningCount}", Color(0xFF10B981)))
                if (d.totalEnvs != null) add(Triple("环境变量", "${d.totalEnvs}", Color(0xFF3B82F6)))
                if (d.totalLogs != null) add(Triple("日志数", "${d.totalLogs}", Color(0xFF8B5CF6)))
            }
            if (extras.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    extras.forEachIndexed { i, (label, value, color) ->
                        StatChip(label, value, color, Modifier.weight(1f))
                        if (extras.size == 3 && i == 2) {
                            // 3 个时补一个占位保持对齐
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (d.avgTimeMs != null) {
                Text(
                    "今日平均耗时：${d.avgTimeMs} ms",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatChip(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------- 趋势

@Composable
private fun TrendSection(points: List<TrendPoint>) {
    // 数据里可能混入全 0 的占位日，过滤掉后仍无数据就不渲染
    val hasData = points.any { it.total > 0 }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("近 ${points.size} 天执行趋势", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            if (!hasData) {
                Text("近期没有执行记录", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val maxTotal = points.maxOf { it.total }.coerceAtLeast(1)
                val successColor = Color(0xFF10B981)
                val failColor = Color(0xFFEF4444)
                val axisColor = MaterialTheme.colorScheme.surfaceVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    // 基线
                    drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)

                    val n = points.size.coerceAtLeast(1)
                    val slot = size.width / n
                    val barWidth = (slot * 0.55f).coerceAtLeast(2f)
                    points.forEachIndexed { i, p ->
                        if (p.total <= 0) return@forEachIndexed
                        val x = i * slot + (slot - barWidth) / 2
                        val totalH = size.height * (p.total / maxTotal.toFloat())
                        val failH = totalH * (p.fail.toFloat() / p.total)
                        // 成功部分（下段）
                        drawRoundRect(
                            color = successColor,
                            topLeft = Offset(x, size.height - (totalH - failH)),
                            size = Size(barWidth, totalH - failH)
                        )
                        // 失败部分（叠在上段）
                        if (failH > 0f) {
                            drawRoundRect(
                                color = failColor,
                                topLeft = Offset(x, size.height - totalH),
                                size = Size(barWidth, failH)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(points.firstOrNull()?.date?.substring(5) ?: "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(successColor)
                        Spacer(Modifier.width(3.dp))
                        Text("成功", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        LegendDot(failColor)
                        Spacer(Modifier.width(3.dp))
                        Text("失败", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(points.lastOrNull()?.date?.substring(5) ?: "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(7.dp).background(color, RoundedCornerShape(2.dp)))
}

// ---------------------------------------------------------------- 排行

@Composable
private fun RankSection(d: PanelDashboard) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("任务排行", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            if (d.topByCount.isNotEmpty()) {
                RankList("执行次数 TOP ${d.topByCount.size}", d.topByCount)
            }
            if (d.topByTime.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                RankList("耗时 TOP ${d.topByTime.size}", d.topByTime)
            }
        }
    }
}

@Composable
private fun RankList(title: String, items: List<TaskRank>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { rank ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (rank.rank) {
                        1 -> Color(0xFFFFB300)
                        2 -> Color(0xFF9E9E9E)
                        3 -> Color(0xFFBF7138)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${rank.rank}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rank.rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(rank.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    rank.detail?.let {
                        Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Text(rank.value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------- 标签

@Composable
private fun LabelSection(labels: List<LabelStat>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("标签统计", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            labels.forEach { l ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.widthIn(min = 40.dp)
                    ) {
                        Text(
                            l.label,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("${l.count} 个任务", fontSize = 11.sp, modifier = Modifier.weight(1f))
                    l.successRate?.let {
                        Text("成功率 $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("今日 ${l.todayRuns} 次", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 资源

@Composable
private fun ResourceSection(detail: Map<String, String>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("服务器资源", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            detail.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, fontSize = 11.sp, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
