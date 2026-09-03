package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.RunningTaskInfo
import com.panel.app.data.model.UnifiedTask
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.components.parseCronDescription
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenLogViewer: (title: String, taskId: String, logPath: String) -> Unit = { _, _, _ -> },
    onOpenScriptEditorScreen: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    val task = uiState.tasks.find { it.id == taskId }
        ?: UnifiedTask(taskId, "任务详情", "", "", "已就绪")

    // Cron 表达式 → 人类可读描述（使用统一解析函数）
    val cronDesc = remember(task.schedule) { parseCronDescription(task.schedule ?: "") }

    var showEditDialog by remember { mutableStateOf(false) }
    var deletingTask by remember { mutableStateOf(false) }
    var isInstanceSelectionMode by remember { mutableStateOf(false) }
    var selectedInstanceIds by remember { mutableStateOf(mutableSetOf<String>()) }
    var historyExpanded by remember { mutableStateOf(true) }
    var showBatchBar by remember { mutableStateOf(false) }

    // 从真实服务端拉取执行历史记录与实时日志
    LaunchedEffect(taskId) {
        viewModel.loadTaskInstancesAndLog(taskId) { _, _ -> }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = task.name,
                        fontSize = 15.sp,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = if (task.isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop,
                        label = if (task.isPinned) "已置顶" else "置顶",
                        tint = if (task.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { viewModel.pinTask(task.id, !task.isPinned) }
                    )
                    ActionButtonSmall(
                        icon = if (task.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        label = if (task.isRunning) "停止" else "运行",
                        tint = if (task.isRunning) Color(0xFFEF4444) else Color(0xFF10B981),
                        onClick = { if (task.isRunning) viewModel.stopTask(task.id) else viewModel.runTask(task.id) }
                    )
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
                    ActionButtonSmall(
                        icon = Icons.Default.SelectAll,
                        label = if (isInstanceSelectionMode) "取消" else "批量",
                        tint = if (isInstanceSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            if (isInstanceSelectionMode) {
                                isInstanceSelectionMode = false
                                selectedInstanceIds = mutableSetOf()
                            } else {
                                showBatchBar = !showBatchBar
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // ── 任务基本信息 ───────────────────────────────────────
            item {
                InfoSection(title = "基本信息") {
                    if (task.schedule.isNotBlank()) {
                        InfoRow("调度", cronDesc.ifEmpty { task.schedule ?: "--" })
                        InfoRow("Cron", task.schedule ?: "--", monospace = true)
                    }
                    InfoRow("超时", "${task.timeout}s")
                    if (!task.workDir.isNullOrBlank()) InfoRow("工作目录", task.workDir ?: "--")
                    if (task.labels.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("标签:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            task.labels.forEach { tag ->
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(3.dp)) {
                                    Text(tag, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                    val ct = task.createdAt
                    val ut = task.updatedAt
                    if (!ct.isNullOrBlank()) InfoRow("创建", ct.take(16).replace("T", " "))
                    if (!ut.isNullOrBlank()) InfoRow("更新", ut.take(16).replace("T", " "))
                }
            }

            // ── 脚本路径 ──────────────────────────────────────────
            item {
                InfoSection(title = "脚本路径") {
                    InfoRow("命令", task.command, monospace = true)
                }
            }

            // ── 打开脚本 ──────────────────────────────────────────
            item {
                val scriptPath = remember(task.command) {
                    val parts = task.command.trim().split("\\s+".toRegex())
                    parts.firstOrNull { it.startsWith("/") && !it.startsWith("/dev/") }
                        ?: parts.getOrNull(1)?.takeIf { it.contains('.') }
                        ?: null
                }
                if (scriptPath != null) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .clickable { onOpenScriptEditorScreen(scriptPath) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("打开脚本", fontSize = 12.sp, style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ActionButtonSmall(
                                    icon = Icons.Default.Edit,
                                    label = "编辑",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = { onOpenScriptEditorScreen(scriptPath) }
                                )
                            }
                        }
                    }
                }
            }

            // ── 最近执行 ──────────────────────────────────────────
            val lastExec = uiState.activeTaskInstances.lastOrNull()
            if (lastExec != null) {
                item {
                    InfoSection(title = "最近执行") {
                        InfoRow("时间", lastExec.startTime)
                        InfoRow("耗时", lastExec.duration.ifEmpty { "--" })
                    }
                }
            }

            // ── 操作按钮 ──────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.loadTaskInstancesAndLog(task.id) { _, _ -> } },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp).weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刷新历史", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onOpenLogViewer("实时日志 · ${task.name}", task.id, "") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp).weight(1f)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("实时日志", fontSize = 11.sp)
                    }
                }
            }

            // ── 批量管理工具栏（固定显示，不在滚动区域内）────────
            item {
                if (showBatchBar || isInstanceSelectionMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val total = uiState.activeTaskInstances.size
                            val count = selectedInstanceIds.size
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("已选 ${count}/${total}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (count < total && total > 0) {
                                    TextButton(onClick = { selectedInstanceIds = uiState.activeTaskInstances.map { it.id }.toMutableSet() }) {
                                        Text("全选", fontSize = 10.sp)
                                    }
                                }
                                if (count > 0) {
                                    TextButton(onClick = {
                                        val idsToDelete = selectedInstanceIds.toList()
                                        selectedInstanceIds = mutableSetOf()
                                        viewModel.batchDeleteTaskInstances(idsToDelete) {}
                                    }) { Text("删除已选", fontSize = 10.sp) }
                                }
                                TextButton(onClick = {
                                    showBatchBar = false
                                    isInstanceSelectionMode = false
                                    selectedInstanceIds = mutableSetOf()
                                }) { Text("关闭", fontSize = 10.sp) }
                            }
                        }
                    }
                }
            }

            // ── 执行历史 ──────────────────────────────────────────
            item {
                CollapsibleSection(
                    title = "日志历史 (${uiState.activeTaskInstances.size})",
                    expanded = historyExpanded,
                    onToggle = { historyExpanded = !historyExpanded }
                ) {
                    if (uiState.activeTaskInstances.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("暂无历史运行记录", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(uiState.activeTaskInstances) { history ->
                                HistoryItem(
                                    history = history,
                                    task = task,
                                    isSelectionMode = isInstanceSelectionMode,
                                    isSelected = selectedInstanceIds.contains(history.id),
                                    onToggleSelect = { checked ->
                                        if (checked) selectedInstanceIds.add(history.id)
                                        else selectedInstanceIds.remove(history.id)
                                    },
                                    onOpenLog = { logTitle, logPath ->
                                        onOpenLogViewer(logTitle, task.id, logPath)
                                    },
                                    onStopRunning = {
                                        viewModel.stopRunningTask(RunningTaskInfo(taskId = task.id, name = task.name, instanceId = history.id))
                                    },
                                    onDelete = {
                                        viewModel.deleteTaskInstance(history.id) {
                                            if (selectedInstanceIds.contains(history.id)) selectedInstanceIds.remove(history.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
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
            title = { Text("确认删除任务", fontSize = 15.sp) },
            text = { Text("确定要从服务端删除定时任务 [${task.name}] 吗？", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTask(task.id)
                        deletingTask = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = false }) { Text("取消") }
            }
        )
    }
}

// ── 子组件 ──────────────────────────────────────────────────────────────────────

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
        Text(value, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif)
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, style = MaterialTheme.typography.titleSmall)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), content = content)
            }
        }
    }
}

@Composable
private fun HistoryItem(
    history: com.panel.app.data.model.TaskInstanceRecord,
    task: UnifiedTask,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: (Boolean) -> Unit,
    onOpenLog: (String, String) -> Unit,
    onStopRunning: () -> Unit,
    onDelete: () -> Unit
) {
    val isRunning = history.statusText == "运行中"
    val isSuccess = history.exitCode == 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelectionMode) Modifier.clickable {} else Modifier.clickable {
                val logTitle = "历史日志 · ${history.startTime}"
                onOpenLog(logTitle, history.logPath ?: "")
            }),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = onToggleSelect)
                Spacer(Modifier.width(8.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = when {
                            isRunning -> MaterialTheme.colorScheme.primaryContainer
                            isSuccess -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = history.statusText,
                            fontSize = 9.sp,
                            color = when {
                                isRunning || isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(text = history.startTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                val descParts = buildList {
                    if (history.pid != null && isRunning) add("PID: ${history.pid}")
                    if (!history.endTime.isNullOrBlank()) add(history.endTime!!)
                    add("耗时 ${history.duration}")
                    add("退出码 ${history.exitCode}")
                }.joinToString(" · ")
                Text(descParts, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            } else if (isRunning) {
                IconButton(onClick = onStopRunning) {
                    Icon(Icons.Default.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}
