package com.panel.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.panel.app.data.model.UnifiedTask
import com.panel.app.data.model.extractScriptFiles
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: MainViewModel,
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    onOpenTaskDetail: (String) -> Unit,
    onOpenLog: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }
    var editingTask by remember { mutableStateOf<UnifiedTask?>(null) }
    var deletingTask by remember { mutableStateOf<UnifiedTask?>(null) }

    val filteredTasks = remember(searchQuery, selectedFilter, uiState.tasks) {
        val filtered = uiState.tasks.filter { task ->
            val matchFilter = when (selectedFilter) {
                "running" -> task.isRunning
                "enabled" -> !task.isDisabled
                "disabled" -> task.isDisabled
                else -> true
            }
            val matchSearch = if (searchQuery.isEmpty()) true else {
                task.name.contains(searchQuery, ignoreCase = true) ||
                        task.command.contains(searchQuery, ignoreCase = true) ||
                        task.schedule.contains(searchQuery, ignoreCase = true)
            }
            matchFilter && matchSearch
        }
        filtered.sortedWith(
            compareByDescending<UnifiedTask> { it.isPinned }
                .thenBy { it.isDisabled }
                .thenBy { it.name }
        )
    }

    val selectedTasks = remember(uiState.tasks) { uiState.tasks.filter { it.selected } }
    val allSelected = remember(filteredTasks, selectedTasks) { filteredTasks.isNotEmpty() && filteredTasks.all { it.selected } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // 2. 全宽搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索任务名称、执行命令或 Cron 规则...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        // 3. 任务状态筛选标签与批量模式入口
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf(
                    "all" to "全部 (${uiState.tasks.size})",
                    "running" to "运行中 (${uiState.tasks.count { it.isRunning }})",
                    "enabled" to "已启用 (${uiState.tasks.count { !it.isDisabled }})",
                    "disabled" to "已禁用 (${uiState.tasks.count { it.isDisabled }})"
                )
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(30.dp)
                    )
                }
            }
        }

        // 顶部批量操作栏 (置于顶部，绝不占用主体列表空间)
        if (uiState.isTaskBatchMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { viewModel.selectAllTasks(it) },
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (selectedTasks.isEmpty()) "全选" else "已选 ${selectedTasks.size}",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        val ids = selectedTasks.map { it.id }
                        IconButton(onClick = { viewModel.batchRunTasks(ids) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "运行", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.batchStopTasks(ids) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = "停止", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.batchToggleTasks(ids, true) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "启用", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.batchToggleTasks(ids, false) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "禁用", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.batchPinTasks(ids, true) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PushPin, contentDescription = "置顶", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.batchDeleteTasks(ids) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 4. 任务卡片列表 (支持下拉刷新)
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshCurrentPanel() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredTasks.isEmpty()) {
                    // 空状态必须自己可滚动：PullToRefreshBox 依赖嵌套滚动分发，
                    // 内容不可滚动时下拉手势产生不了滚动增量，列表为空就刷不动
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (uiState.isLoading) "正在刷新任务..." else "暂无匹配的定时任务", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                isBatchMode = uiState.isTaskBatchMode,
                                onSelect = { viewModel.toggleTaskSelection(task.id) },
                                onClick = { onOpenTaskDetail(task.id) },
                                onToggle = { enabled -> viewModel.toggleTask(task.id, enabled) },
                                onTogglePin = { viewModel.pinTask(task.id, !task.isPinned) },
                                onRunOrStop = {
                                    if (task.isRunning) {
                                        viewModel.stopTask(task.id)
                                    } else {
                                        viewModel.runTask(task.id)
                                    }
                                },
                                onOpenLog = { onOpenLog(task.id) },
                                onEdit = { editingTask = task },
                                onDelete = { deletingTask = task }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            scriptFiles = uiState.scriptTree.extractScriptFiles(),
            onDismiss = onDismissCreateDialog,
            onConfirm = { name, cmd, cron ->
                viewModel.createTask(name, cmd, cron)
                onDismissCreateDialog()
            }
        )
    }

    if (editingTask != null) {
        EditTaskDialog(
            task = editingTask!!,
            onDismiss = { editingTask = null },
            onConfirm = { updated ->
                viewModel.updateTask(updated)
                editingTask = null
            }
        )
    }

    if (deletingTask != null) {
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text("确认删除任务", fontSize = 15.sp) },
            text = { Text("确定要删除定时任务 [${deletingTask!!.name}] 吗？", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTask(deletingTask!!.id)
                        deletingTask = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun TaskCard(
    task: UnifiedTask,
    isBatchMode: Boolean = false,
    onSelect: () -> Unit = {},
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onTogglePin: () -> Unit,
    onRunOrStop: () -> Unit,
    onOpenLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // 状态语义：禁用是"关着"不是"坏了"，所以用中性色而不是错误红。
    // 红色只留给真正的失败，否则用户无法区分"没启用"和"出问题了"。
    val state = when {
        task.isRunning -> TaskVisualState.Running
        task.isDisabled -> TaskVisualState.Disabled
        task.statusText == "排队中" -> TaskVisualState.Queued
        else -> TaskVisualState.Ready
    }
    // 禁用不是删除：标题保持全对比度保证可读，只让次要信息退后
    val mutedAlpha = if (state == TaskVisualState.Disabled) 0.45f else 1f

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isBatchMode) onSelect() else onClick()
            },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                if (isBatchMode) {
                    Checkbox(
                        checked = task.selected,
                        onCheckedChange = { onSelect() },
                        modifier = Modifier.padding(end = 4.dp).size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (task.isPinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "已置顶",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = task.name,
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            StateBadge(state)
                        }
                        if (!isBatchMode) {
                            // M3 要求触控目标至少 48dp；视觉尺寸由 Switch 内部控制
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onToggle(task.isDisabled) },
                                contentAlignment = Alignment.Center
                            ) {
                                Switch(
                                    checked = !task.isDisabled,
                                    onCheckedChange = onToggle,
                                    modifier = Modifier.scale(0.75f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = task.command,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = mutedAlpha)
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = mutedAlpha)
                            )
                            Text(
                                text = task.schedule,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = mutedAlpha)
                            )
                        }


                    if (!isBatchMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (task.isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop,
                                    contentDescription = "置顶",
                                    tint = if (task.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(onClick = onRunOrStop, modifier = Modifier.size(24.dp)) {
                                if (task.isRunning) {
                                    Icon(Icons.Default.Stop, contentDescription = "停止任务", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "立即执行", tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                }
                            }
                            IconButton(onClick = onOpenLog, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "查看日志", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑属性", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "删除任务", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class TaskVisualState { Running, Queued, Disabled, Ready }

/** 状态徽章：已启用(绿色)、已禁用(红色)、运行中(主色/脉冲) */
@Composable
private fun StateBadge(state: TaskVisualState) {
    val (label, container, content) = when (state) {
        TaskVisualState.Running -> Triple(
            "运行中",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        TaskVisualState.Queued -> Triple("排队中", Color(0xFFFFF3E0), Color(0xFFB45309))
        TaskVisualState.Disabled -> Triple(
            "已禁用",
            Color(0xFFFFEBEE),
            Color(0xFFC62828)
        )
        TaskVisualState.Ready -> Triple(
            "已启用",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32)
        )
    }
    Surface(color = container, shape = RoundedCornerShape(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            if (state == TaskVisualState.Running) {
                PulsingDot()
            }
            Text(label, fontSize = 9.sp, color = content)
        }
    }
}

/** 双层脉冲点：外圈扩散 + 内芯实心，替代原来用 "●" 字符假装动画 */
@Composable
private fun PulsingDot() {
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
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(8.dp)) {
        val r = size.minDimension / 2
        drawCircle(
            color = color.copy(alpha = (1f - progress) * 0.35f),
            radius = r * (1f + progress),
            center = center
        )
        drawCircle(color = color, radius = r * 0.5f, center = center)
    }
}

@Composable
fun CreateTaskDialog(
    scriptFiles: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("0 8 * * *") }
    var showFilePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建定时任务", fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("选择脚本文件:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { showFilePicker = !showFilePicker },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showFilePicker) "收起列表" else "浏览脚本库", fontSize = 10.sp)
                    }
                }

                if (showFilePicker) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(scriptFiles) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val baseName = file.substringAfterLast("/")
                                            name = baseName.substringBeforeLast(".")
                                            command = when {
                                                file.endsWith(".py") -> "python3 $file"
                                                file.endsWith(".js") -> "node $file"
                                                file.endsWith(".sh") -> "bash $file"
                                                else -> "python3 $file"
                                            }
                                            showFilePicker = false
                                        }
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text(file, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("执行命令 (例如 python3 checkin.py)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("定时 Cron 规则 (分 时 日 月 周)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotEmpty() && command.isNotEmpty()) onConfirm(name, command, schedule) }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun EditTaskDialog(
    task: UnifiedTask,
    onDismiss: () -> Unit,
    onConfirm: (UnifiedTask) -> Unit
) {
    var name by remember { mutableStateOf(task.name) }
    var command by remember { mutableStateOf(task.command) }
    var schedule by remember { mutableStateOf(task.schedule) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑任务", fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("执行命令", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("Cron 规则", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(task.copy(name = name, command = command, schedule = schedule)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
