package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.TaskInstanceRecord
import com.panel.app.data.model.UnifiedTask
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTaskDetailScreen(
    taskId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenScriptEditor: (String) -> Unit,
    onOpenLog: (title: String, taskId: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val task = uiState.tasks.find { it.id == taskId }
        ?: UnifiedTask(taskId, "任务详情", "", "", "已就绪")

    BackHandler { onBack() }

    var selectedTab by remember { mutableStateOf(0) }
    var showEditDialog by remember(taskId) { mutableStateOf(false) }
    var deletingTask by remember(taskId) { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        viewModel.loadTaskInstancesAndLog(taskId) { _, _ -> }
    }

    if (showEditDialog) {
        EditTaskDialog(
            task = task,
            onDismiss = { showEditDialog = false },
            onConfirm = {}
        )
    }

    if (deletingTask) {
        AlertDialog(
            onDismissRequest = { deletingTask = false },
            title = { Text("确认删除任务？", fontSize = 16.sp) },
            text = { Text("此操作将永久删除任务「${task.name}」及其执行历史，且不可恢复。", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { deletingTask = false; viewModel.deleteTask(task.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deletingTask = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = task.name.substringAfterLast('/'),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = if (task.isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop,
                        label = if (task.isPinned) "取消置顶" else "置顶",
                        tint = if (task.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { viewModel.pinTask(task.id, !task.isPinned) }
                    )
                    if (task.isRunning) {
                        ActionButtonSmall(
                            icon = Icons.Default.Stop,
                            label = "停止",
                            tint = Color(0xFFEF4444),
                            onClick = { viewModel.stopTask(task.id) }
                        )
                    } else {
                        ActionButtonSmall(
                            icon = Icons.Default.PlayArrow,
                            label = "运行",
                            tint = Color(0xFF10B981),
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
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("任务详情", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val count = uiState.activeTaskInstances.size
                        Text("执行历史 (${count})", fontSize = 12.sp)
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("实时日志", fontSize = 12.sp) }
                )
            }

            when (selectedTab) {
                0 -> TaskDetailTab(task = task, viewModel = viewModel, context = context, clipboardManager = clipboardManager, onOpenScriptEditor = onOpenScriptEditor)
                1 -> ExecutionHistoryTab(instances = uiState.activeTaskInstances, viewModel = viewModel, taskId = taskId)
                2 -> LogTab(task = task, viewModel = viewModel, onOpenLog = onOpenLog, clipboardManager = clipboardManager, context = context)
            }
        }
    }
}

@Composable
private fun TaskDetailTab(
    task: UnifiedTask,
    viewModel: MainViewModel,
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onOpenScriptEditor: (String) -> Unit
) {
    val columnPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = columnPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 状态卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = when {
                                    task.isRunning -> MaterialTheme.colorScheme.primaryContainer
                                    task.isDisabled -> Color(0xFFFFEBEE)
                                    else -> Color(0xFFE8F5E9)
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = when {
                                        task.isRunning -> "● 正在运行"
                                        task.isDisabled -> "已禁用"
                                        else -> "● 已启用"
                                    },
                                    fontSize = 11.sp,
                                    color = when {
                                        task.isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                                        task.isDisabled -> Color(0xFFC62828)
                                        else -> Color(0xFF2E7D32)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            if (task.isPinned) {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text("📌 置顶", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(task.id))
                                    Toast.makeText(context, "任务ID已复制: ${task.id}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("ID: ${task.id}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                            if (task.pid != null && task.pid > 0) {
                                Text(text = "PID: ${task.pid}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Switch(
                            checked = !task.isDisabled,
                            onCheckedChange = { viewModel.toggleTask(task.id, it) },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    // 命令参数
                    if (task.command.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Text("命令", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(task.command))
                                    Toast.makeText(context, "命令已复制", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(4.dp))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = task.command,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // 调度规则
                    if (task.schedule.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("调度规则", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(task.schedule))
                                    Toast.makeText(context, "调度规则已复制", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(4.dp))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = task.schedule,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            DetailCard(title = "任务信息", icon = Icons.Default.Info) {
                DetailRow("状态", task.statusText)
                if (!task.lastRunTime.isNullOrEmpty()) {
                    DetailRow("上次运行", task.lastRunTime)
                }
                if (!task.createdAt.isNullOrEmpty()) {
                    DetailRow("创建时间", task.createdAt)
                }
                if (task.labels.isNotEmpty()) {
                    DetailRow("标签", task.labels.joinToString(", "))
                }
            }
        }

        item {
            DetailCard(title = "高级配置", icon = Icons.Default.Settings) {
                if (!task.preCommand.isNullOrEmpty()) {
                    DetailRow("前置命令", task.preCommand)
                }
                if (!task.postCommand.isNullOrEmpty()) {
                    DetailRow("后置命令", task.postCommand)
                }
                DetailRow("超时(秒)", "${task.timeout}")
                DetailRow("重试次数", "${task.retryCount}")
                DetailRow("随机范围", "${task.randomRange}")
            }
        }
    }
}

@Composable
private fun ExecutionHistoryTab(instances: List<TaskInstanceRecord>, viewModel: MainViewModel, taskId: String) {
    if (instances.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("暂无执行记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(instances.reversed()) { record ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(record.statusText, fontSize = 12.sp, color = when (record.statusText) {
                            "成功" -> Color(0xFF2E7D32)
                            "失败" -> MaterialTheme.colorScheme.error
                            "取消" -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        })
                        Text(record.startTime ?: "--", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (record.duration.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("耗时 ${record.duration}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!record.logPath.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { viewModel.loadTaskInstancesAndLog(taskId) { _, _ -> } },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("重新加载", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTab(task: UnifiedTask, viewModel: MainViewModel, onOpenLog: (title: String, taskId: String) -> Unit, clipboardManager: androidx.compose.ui.platform.ClipboardManager, context: android.content.Context) {
    val uiState by viewModel.uiState.collectAsState()
    val logContent = uiState.activeLogContent
    var isRefreshing by remember(task.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (logContent.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NoteAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("暂无日志，请先运行任务", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { isRefreshing = true; viewModel.runTask(task.id); isRefreshing = false },
                        enabled = !isRefreshing && !task.isDisabled && !task.isRunning
                    ) {
                        if (isRefreshing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("运行后查看日志") }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.loadTaskInstancesAndLog(task.id) { _, _ -> } },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(logContent))
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制日志", fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onOpenLog(task.name, task.id) }) {
                    Text("全屏查看", fontSize = 11.sp)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                LogContentDisplay(logContent = logContent)
            }
        }
    }
}

@Composable
private fun LogContentDisplay(logContent: String) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = logContent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun DetailEditTaskDialog(
    task: UnifiedTask,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var name by remember { mutableStateOf(task.name) }
    var command by remember { mutableStateOf(task.command) }
    var schedule by remember { mutableStateOf(task.schedule) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑任务", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("执行命令") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("定时规则 (Cron)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm() },
                enabled = name.isNotBlank() && command.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DetailCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isMonospace: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (value.length > 60) value.take(60) + "…" else value,
            fontSize = 11.sp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun fmtTime(timestamp: String): String = timestamp
