package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.adapter.QinglongApiHelpers
import com.panel.app.data.model.UnifiedTask
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.viewmodel.MainViewModel
import com.panel.app.util.CronExpressionDescriber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTaskDetailScreen(
    taskId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenScriptEditor: (String) -> Unit,
    onOpenLog: (title: String, taskId: String) -> Unit = { _, _ -> },
    onOpenHistory: (taskId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val task = uiState.tasks.find { it.id == taskId }
        ?: UnifiedTask(taskId, "任务详情", "", "", "已就绪")

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearToast()
        }
    }

    BackHandler { onBack() }

    var showEditDialog by remember(taskId) { mutableStateOf(false) }
    var deletingTask by remember(taskId) { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        viewModel.loadTaskInstancesAndLog(taskId) { _, _ -> }
    }

    if (showEditDialog) {
        EditTaskDialog(
            task = task,
            onDismiss = { showEditDialog = false },
            onConfirm = { updated ->
                viewModel.updateTask(updated)
                showEditDialog = false
            }
        )
    }

    if (deletingTask) {
        AlertDialog(
            onDismissRequest = { deletingTask = false },
            title = { Text("确认删除任务？", fontSize = 16.sp) },
            text = { Text("此操作将永久删除任务「${task.name}」及其执行记录，不可恢复。", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingTask = false
                        viewModel.deleteTask(task.id)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = { TextButton(onClick = { deletingTask = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            // 需求 1：标题靠左展示，不居中
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = task.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "ID: ${task.id}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = if (task.isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop,
                        label = if (task.isPinned) "已置顶" else "置顶",
                        tint = if (task.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { viewModel.pinTask(task.id, !task.isPinned) }
                    )
                    if (task.isRunning) {
                        ActionButtonSmall(
                            icon = Icons.Default.Stop,
                            label = "停止",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { viewModel.stopTask(task.id) }
                        )
                    } else {
                        ActionButtonSmall(
                            icon = Icons.Default.PlayArrow,
                            label = "运行",
                            tint = MaterialTheme.colorScheme.secondary,
                            onClick = { viewModel.runTask(task.id) }
                        )
                    }
                    ActionButtonSmall(
                        icon = Icons.Default.Edit,
                        label = "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { showEditDialog = true }
                    )
                    ActionButtonSmall(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { deletingTask = true }
                    )
                }
            )
        }
    ) { padding ->
        // 需求 7：彻底移除 Tab 等多余组件，单一列表，一行一个内容
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ================= 1. 三大二级页面直达入口 =================
            item {
                Text(
                    text = "快速直达二级页面",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                )
            }

            // 二级入口 1：实时执行日志
            item {
                NavigableActionCard(
                    icon = Icons.Default.Terminal,
                    title = "最新日志",
                    subtitle = if (task.isRunning) "任务当前正在运行中，点击跟随实时日志流" else "点击查看最新日志输出",
                    badge = if (task.isRunning) "运行中" else "查看日志",
                    badgeColor = if (task.isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    onClick = { onOpenLog(task.name, task.id) }
                )
            }

            // 二级入口 2：脚本代码页面
            item {
                val scriptPath = extractScriptPath(task.command)
                NavigableActionCard(
                    icon = Icons.Default.Code,
                    title = "脚本代码文件",
                    subtitle = if (scriptPath.isNotBlank()) "目标文件: $scriptPath (点击在线查看与编辑)" else "从执行命令查看或编辑脚本源码",
                    badge = scriptPath.ifBlank { "源码编辑" },
                    badgeColor = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        val path = scriptPath.ifBlank { task.name }
                        onOpenScriptEditor(path)
                    }
                )
            }

            // 二级入口 3：执行历史记录页面
            item {
                val count = uiState.activeTaskInstances.size
                NavigableActionCard(
                    icon = Icons.Default.History,
                    title = "历次执行历史记录",
                    subtitle = "累计已记录 $count 条执行历史实例，点击查看完整历史",
                    badge = "$count 条记录",
                    badgeColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { onOpenHistory(task.id) }
                )
            }

            // ================= 2. 状态与基础运行信息 =================
            item {
                Text(
                    text = "运行与状态信息",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                )
            }

            item {
                DetailCard(title = "任务基础信息", icon = Icons.Default.Info) {
                    // 运行状态与启停开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("任务状态", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    color = when {
                                        task.isRunning -> MaterialTheme.colorScheme.secondaryContainer
                                        task.isDisabled -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = task.statusText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            task.isRunning -> MaterialTheme.colorScheme.secondary
                                            task.isDisabled -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (task.isPinned) {
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                                        Text("已置顶 📌", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                if (task.pid != null && task.pid > 0) {
                                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                        Text("PID: ${task.pid}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                        Switch(
                            checked = !task.isDisabled,
                            onCheckedChange = { viewModel.toggleTask(task.id, it) },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 任务名称
                    DetailRow(
                        label = "任务名称",
                        value = task.name
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 任务ID
                    DetailRow(
                        label = "任务 ID",
                        value = task.id,
                        isMonospace = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 执行命令
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val scriptSize = remember(task.command, uiState.scriptTree) {
                            val map = buildScriptSizeMap(uiState.scriptTree)
                            findScriptSizeForCommand(task.command, map)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("执行命令", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!scriptSize.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "脚本大小: $scriptSize",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = task.command,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // ================= 3. 定时调度与运行周期 =================
            item {
                Text(
                    text = "定时调度与执行周期",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                )
            }

            item {
                DetailCard(title = "定时规则与执行历史", icon = Icons.Default.Schedule) {
                    val cronDesc = remember(task.schedule) {
                        CronExpressionDescriber.describe(task.schedule)
                    }

                    // 需求 12：展示中文自然语言解读
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("主定时规则 (Cron)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = task.schedule,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (cronDesc.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = cronDesc,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 需求 2：补全缺失字段 extraSchedules
                    if (task.extraSchedules.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("额外定时规则 (${task.extraSchedules.size} 条)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            task.extraSchedules.forEach { extra ->
                                val extraDesc = CronExpressionDescriber.describe(extra)
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(extra, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                                    if (extraDesc.isNotBlank()) {
                                        Text("($extraDesc)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 下次运行时间 (补全字段)
                    DetailRow(
                        label = "下次计划运行",
                        value = task.nextRunTime?.ifBlank { null } ?: "等待调度服务计算"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 上次运行时间
                    DetailRow(
                        label = "上次运行时间",
                        value = task.lastRunTime?.ifBlank { null } ?: "尚未记录执行时间"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 上次运行时长 (补全字段)
                    val cachedDuration = uiState.taskDurationCache[task.id]
                    val runningDurationText = when {
                        task.lastRunningTime != null && task.lastRunningTime > 0 ->
                            "${QinglongApiHelpers.formatSeconds(task.lastRunningTime)} (${task.lastRunningTime} 秒)"
                        !cachedDuration.isNullOrBlank() -> cachedDuration
                        else -> "未记录时长"
                    }
                    DetailRow(
                        label = "上次运行时长",
                        value = runningDurationText
                    )
                }
            }

            // ================= 4. 高级配置参数 (全量补全缺失字段) =================
            item {
                Text(
                    text = "高级配置参数",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                )
            }

            item {
                DetailCard(title = "高级属性配置", icon = Icons.Default.Tune) {
                    // 超时时间
                    DetailRow(
                        label = "超时时间",
                        value = if (task.timeout > 0) "${task.timeout} 秒" else "默认不限 (系统默认)"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 失败重试配置 (补全字段)
                    DetailRow(
                        label = "失败重试",
                        value = if (task.retryCount > 0) "${task.retryCount} 次 (间隔: ${task.retryInterval} 秒)" else "不重试"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 随机延时 (补全字段)
                    DetailRow(
                        label = "随机延时范围",
                        value = if (task.randomRange > 0) "0 ~ ${task.randomRange} 秒" else "无随机延时"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 工作目录 (补全字段)
                    DetailRow(
                        label = "工作目录",
                        value = task.workDir?.ifBlank { null } ?: "默认 (面板 scripts 根目录)",
                        isMonospace = task.workDir?.isNotBlank() == true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 前置命令 (补全字段)
                    DetailRow(
                        label = "前置命令 (Pre-command)",
                        value = task.preCommand?.ifBlank { null } ?: "无",
                        isMonospace = task.preCommand?.isNotBlank() == true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 后置命令 (补全字段)
                    DetailRow(
                        label = "后置命令 (Post-command)",
                        value = task.postCommand?.ifBlank { null } ?: "无",
                        isMonospace = task.postCommand?.isNotBlank() == true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 多实例并发 (补全字段)
                    DetailRow(
                        label = "多实例并发",
                        value = if (task.allowMultipleInstances) "允许并发执行" else "排队/独占执行"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 语言环境与运行器 (补全字段)
                    DetailRow(
                        label = "语言环境 / 运行时",
                        value = if (task.languages.isNotEmpty()) task.languages.joinToString(", ") else "默认环境"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 执行 Agent 节点 (补全字段)
                    DetailRow(
                        label = "执行 Agent 节点",
                        value = task.agentId?.ifBlank { null } ?: "Master 本地节点"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 日志清理策略 (补全字段)
                    DetailRow(
                        label = "日志清理配置",
                        value = task.cleanConfig?.ifBlank { null } ?: "按系统全局保留策略"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 任务标签
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text("任务标签 (Labels)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                        if (task.labels.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                task.labels.forEach { label ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        } else {
                            Text("暂无标签", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }

                    // 创建与更新时间
                    if (task.createdAt?.isNotBlank() == true || task.updatedAt?.isNotBlank() == true) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        DetailRow(label = "创建时间", value = task.createdAt?.ifBlank { "未知" } ?: "未知")
                        if (task.updatedAt?.isNotBlank() == true) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            DetailRow(label = "更新时间", value = task.updatedAt)
                        }
                    }
                }
            }

            // 底部安全留白
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 现代沉浸式二级页面直达入口卡片组件 */
@Composable
private fun NavigableActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (badge.isNotBlank()) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun extractScriptPath(command: String): String {
    val tokens = command.trim().split("\\s+".toRegex())
    return tokens.firstOrNull { it.contains('.') && (it.endsWith(".js") || it.endsWith(".py") || it.endsWith(".sh") || it.endsWith(".ts")) }
        ?: tokens.drop(1).firstOrNull() ?: ""
}
