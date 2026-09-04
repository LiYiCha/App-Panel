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
import com.panel.app.data.model.UnifiedTask
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenLogViewer: (title: String, taskId: String, logPath: String) -> Unit = { _, _, _ -> },
    onOpenScriptEditorScreen: (scriptPath: String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
    }

    val task = uiState.tasks.find { it.id == taskId }
        ?: UnifiedTask(taskId, "任务详情", "", "", "已就绪")

    var selectedTab by remember { mutableStateOf(0) } // 0: 任务详细信息, 1: 运行日志历史
    var showEditDialog by remember { mutableStateOf(false) }
    var deletingTask by remember { mutableStateOf(false) }

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
                    ActionButtonSmall(
                        icon = if (task.isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop,
                        label = if (task.isPinned) "取消置顶" else "置顶",
                        tint = if (task.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { viewModel.pinTask(task.id, !task.isPinned) }
                    )
                    // 动态运行 / 停止
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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部分段：任务信息 / 日志历史（满足用户要求：点击日志历史才显示该任务的日志历史列表）
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("任务详情", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (uiState.activeTaskInstances.isNotEmpty()) "日志历史 (${uiState.activeTaskInstances.size})" else "日志历史",
                            fontSize = 13.sp
                        )
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (selectedTab == 0) {
                // Tab 0: 任务详细信息
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (task.isDisabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                                task.isDisabled -> Color(0xFFFFEBEE)
                                                else -> Color(0xFFE8F5E9)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (task.isRunning) "● 正在运行" else if (task.isDisabled) "已禁用" else "已启用",
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
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "置顶 📌",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable {
                                                clipboardManager.setText(AnnotatedString(task.id))
                                                Toast.makeText(context, "任务ID已复制: ${task.id}", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text(
                                                text = "ID: ${task.id} 📋",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (task.pid != null && task.pid > 0) {
                                            Text(text = "PID: ${task.pid}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    Switch(
                                        checked = !task.isDisabled,
                                        onCheckedChange = { viewModel.toggleTask(task.id, it) },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }

                                // 目标脚本路径与调度规则
                                val scriptFile = remember(task.command) {
                                    val parts = task.command.trim().split("\\s+".toRegex())
                                    parts.firstOrNull { it.endsWith(".js") || it.endsWith(".py") || it.endsWith(".sh") || it.endsWith(".ts") }
                                        ?: parts.getOrNull(1) ?: task.command
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("目标脚本 (Script)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                scriptFile,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable { onOpenScriptEditorScreen(scriptFile) }
                                            )
                                            IconButton(
                                                onClick = { onOpenScriptEditorScreen(scriptFile) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.OpenInNew,
                                                    contentDescription = "打开脚本",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("定时规则 (Cron)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(task.schedule, fontSize = 12.sp, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                        val cronDesc = remember(task.schedule) {
                                            com.panel.app.util.CronExpressionDescriber.describe(task.schedule)
                                        }
                                        if (cronDesc.isNotBlank()) {
                                            Text(cronDesc, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                    }
                                }

                                // 时间统计：上次执行与创建更新
                                val lastExecStr = remember(task.lastExecutionTime) {
                                    task.lastExecutionTime?.let {
                                        try {
                                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                            sdf.format(java.util.Date(if (it < 10000000000L) it * 1000 else it))
                                        } catch (_: Exception) { "$it" }
                                    } ?: "尚未执行"
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("上次执行时间", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(lastExecStr, fontSize = 11.sp, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("超时限制", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${task.timeout} 秒", fontSize = 11.sp, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                if (!task.createdAt.isNullOrBlank() || !task.updatedAt.isNullOrBlank()) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        if (!task.createdAt.isNullOrBlank()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("创建时间", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(task.createdAt.take(19).replace("T", " "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (!task.updatedAt.isNullOrBlank()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("更新时间", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(task.updatedAt.take(19).replace("T", " "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
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

                                // 执行命令展示框
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("执行命令 (Command):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                    // 高级调度与执行配置卡片 (适配白虎/青龙高级字段：前置/后置命令、工作目录、节点代理、重试与延时等)
                    if (!task.preCommand.isNullOrBlank() || !task.postCommand.isNullOrBlank() ||
                        !task.workDir.isNullOrBlank() || !task.agentId.isNullOrBlank() ||
                        (task.retryCount != null && task.retryCount > 0) ||
                        (task.randomRange != null && task.randomRange > 0) ||
                        !task.languages.isNullOrEmpty() || !task.nextRunTime.isNullOrBlank()
                    ) {
                        item {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text("高级调度与环境配置", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    }

                                    if (!task.nextRunTime.isNullOrBlank()) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("下次执行时间", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(task.nextRunTime.take(19).replace("T", " "), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                    }

                                    if (!task.workDir.isNullOrBlank()) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("工作目录 (WorkDir)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(task.workDir, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }

                                    if (!task.agentId.isNullOrBlank()) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("执行节点/代理 (Agent)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(task.agentId, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }

                                    if (task.retryCount != null && task.retryCount > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("重试策略", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("重试 ${task.retryCount} 次 (间隔 ${task.retryInterval ?: 0} 秒)", fontSize = 11.sp)
                                        }
                                    }

                                    if (task.randomRange != null && task.randomRange > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("随机延时 (Random)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("0 ~ ${task.randomRange} 秒", fontSize = 11.sp)
                                        }
                                    }

                                    if (!task.languages.isNullOrEmpty()) {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("执行环境:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            task.languages.forEach { lang ->
                                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                                    Text(lang, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }

                                    if (!task.preCommand.isNullOrBlank()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("前置命令 (Pre-command):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(task.preCommand, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                            }
                                        }
                                    }

                                    if (!task.postCommand.isNullOrBlank()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("后置命令 (Post-command):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(task.postCommand, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 最新日志直达入口卡片
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text("最新实时运行日志", fontSize = 13.sp, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Button(
                                        onClick = {
                                            onOpenLogViewer("实时日志 · ${task.name}", task.id, "")
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("查看全屏日志", fontSize = 11.sp)
                                    }
                                }

                                val previewLines = uiState.activeLogContent.lines().filter { it.isNotBlank() }.take(3)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        if (previewLines.isEmpty()) {
                                            Text("点击上方按钮可在全屏终端中查看实时日志输出", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            previewLines.forEach { line ->
                                                Text(
                                                    text = line,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: 运行日志历史列表（点击才显示）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "历史运行记录 (${uiState.activeTaskInstances.size})",
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(onClick = { viewModel.loadTaskInstancesAndLog(task.id) { _, _ -> } }) {
                                Text("刷新历史", fontSize = 11.sp)
                            }
                        }
                    }

                    if (uiState.activeTaskInstances.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("暂无历史运行日志记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(uiState.activeTaskInstances) { history ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 绝不使用弹窗！直接跳二级全屏日志页
                                        val logTitle = "历史日志 · ${history.startTime}"
                                        if (!history.logPath.isNullOrBlank()) {
                                            onOpenLogViewer(logTitle, "", history.logPath)
                                        } else {
                                            onOpenLogViewer(logTitle, task.id, "")
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                color = if (history.exitCode == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (history.exitCode == 0) "成功" else "失败",
                                                    fontSize = 9.sp,
                                                    color = if (history.exitCode == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(text = history.startTime, fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Text(
                                            text = "耗时: ${history.duration} · 退出码: ${history.exitCode}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!history.logPath.isNullOrBlank()) {
                                            Text(
                                                text = history.logPath,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
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
