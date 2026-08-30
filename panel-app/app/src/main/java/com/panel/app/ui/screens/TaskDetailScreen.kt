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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.TaskInstanceRecord
import com.panel.app.data.model.UnifiedTask
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
    }

    val task = uiState.tasks.find { it.id == taskId }
        ?: UnifiedTask(taskId, "任务详情", "", "", "已就绪")

    var selectedTab by remember { mutableStateOf(0) } // 0: 最新实时日志, 1: 历史运行实例
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedInstanceLog by remember { mutableStateOf<String?>(null) }
    var deletingTask by remember { mutableStateOf(false) }

    // 从真实服务端拉取执行历史记录与实时日志
    LaunchedEffect(taskId) {
        viewModel.loadTaskInstancesAndLog(taskId) { _, _ -> }
    }

    var showLiveLogDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = task.name,
                        fontSize = 15.sp,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    // 置顶 / 取消置顶
                    IconButton(
                        onClick = { viewModel.pinTask(task.id, !task.isPinned) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop,
                            contentDescription = "置顶",
                            tint = if (task.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // 查看最新实时输出弹窗
                    IconButton(
                        onClick = { showLiveLogDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = "实时输出", modifier = Modifier.size(18.dp))
                    }
                    // 动态运行 / 停止
                    if (task.isRunning) {
                        IconButton(
                            onClick = { viewModel.stopTask(task.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "停止任务", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.runTask(task.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "运行任务", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑任务", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { deletingTask = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "删除任务", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. 任务全面属性数据卡片 (Task Detailed Attributes & Metadata)
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (task.isDisabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 状态与开关行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = when {
                                        task.isRunning -> MaterialTheme.colorScheme.primaryContainer
                                        task.isDisabled -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (task.isRunning) "● 正在运行" else if (task.isDisabled) "已禁用" else "已就绪",
                                        fontSize = 11.sp,
                                        color = when {
                                            task.isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                                            task.isDisabled -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                if (task.isPinned) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "已置顶 📌",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(text = "ID: ${task.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Switch(
                                checked = !task.isDisabled,
                                onCheckedChange = { viewModel.toggleTask(task.id, it) },
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        // 调度规则与属性详情表格
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("定时表达式 (Cron)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(task.schedule, fontSize = 13.sp, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("超时控制", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${task.timeout} 秒", fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (task.labels.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("标签:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                task.labels.forEach { tag ->
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(3.dp)) {
                                        Text(tag, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }

                        // 执行命令展示框（带快捷复制）
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("执行命令 (Command):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(task.command))
                                        Toast.makeText(context, "命令已复制", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("复制命令", fontSize = 10.sp)
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = task.command,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. 最新执行日志卡片 (Latest Log Summary & Viewer Entry)
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("最新执行日志", fontSize = 13.sp, style = MaterialTheme.typography.titleSmall)
                            }
                            FilledTonalButton(
                                onClick = { showLiveLogDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("查看完整日志", fontSize = 11.sp)
                            }
                        }

                        // 日志预览摘要（前 2-3 行），无大黑框，保持整洁
                        val previewLines = uiState.activeLogContent.lines().filter { it.isNotBlank() }.take(3)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (previewLines.isEmpty()) {
                                    Text("暂无运行日志输出或任务尚未执行", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    previewLines.forEach { line ->
                                        Text(
                                            text = line,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. 运行历史日志记录标题
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "运行历史日志记录 (${uiState.activeTaskInstances.size})",
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { viewModel.loadTaskInstancesAndLog(task.id) { _, _ -> } }) {
                        Text("刷新历史", fontSize = 11.sp)
                    }
                }
            }

            // 4. 运行历史记录列表条目 (Historical Execution Logs List)
            if (uiState.activeTaskInstances.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("暂无历史运行日志记录", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(uiState.activeTaskInstances) { history ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!history.logPath.isNullOrBlank()) {
                                    selectedInstanceLog = "正在从服务器加载该次执行日志...\n路径: ${history.logPath}"
                                    val logPath = history.logPath
                                    val path = if (logPath.contains('/')) logPath.substringBeforeLast('/') else ""
                                    val file = if (logPath.contains('/')) logPath.substringAfterLast('/') else logPath
                                    viewModel.loadServerLogDetail(path, file) { log ->
                                        selectedInstanceLog = log.ifBlank { "该次执行日志为空或已自动清理。" }
                                    }
                                } else if (history.logSnippet.isNotBlank()) {
                                    selectedInstanceLog = history.logSnippet
                                } else {
                                    selectedInstanceLog = "正在从服务器拉取日志..."
                                    viewModel.getTaskLog(task.id) { log ->
                                        selectedInstanceLog = log.ifBlank {
                                            "执行时间: ${history.startTime}\n耗时: ${history.duration}\n退出状态: Code ${history.exitCode}\n--------------------\n任务执行完毕，无异常报错输出。"
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = if (history.exitCode == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (history.exitCode == 0) "成功" else "失败",
                                            fontSize = 9.sp,
                                            color = if (history.exitCode == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(text = history.startTime, fontSize = 11.sp, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(text = "耗时: ${history.duration} • 退出码: ${history.exitCode}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // 历史日志查看弹窗
    if (selectedInstanceLog != null) {
        AlertDialog(
            onDismissRequest = { selectedInstanceLog = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("运行历史日志", fontSize = 15.sp)
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(selectedInstanceLog!!))
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                    }
                }
            },
            text = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(selectedInstanceLog!!.lines()) { line ->
                            Text(text = line, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedInstanceLog = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 实时最新日志查看弹窗
    if (showLiveLogDialog) {
        AlertDialog(
            onDismissRequest = { showLiveLogDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("最新实时输出", fontSize = 15.sp)
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(uiState.activeLogContent))
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                    }
                }
            },
            text = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(uiState.activeLogContent.lines()) { line ->
                            Text(text = line, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLiveLogDialog = false }) {
                    Text("关闭")
                }
            }
        )
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
