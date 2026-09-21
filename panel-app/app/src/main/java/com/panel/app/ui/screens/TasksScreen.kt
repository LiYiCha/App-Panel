package com.panel.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import com.panel.app.data.adapter.QinglongApiHelpers
import androidx.compose.foundation.shape.CircleShape
import com.panel.app.data.model.ScriptNode
import com.panel.app.data.model.UnifiedTask
import com.panel.app.data.model.extractScriptFiles
import com.panel.app.ui.viewmodel.MainViewModel

enum class TaskSortType(val label: String) {
    DEFAULT("默认排序"),
    NAME_ASC("名称 A-Z"),
    NAME_DESC("名称 Z-A"),
    NEXT_RUN("下次运行"),
    LAST_RUN("上次运行"),
    STATUS("运行状态")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: MainViewModel,
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    onOpenTaskDetail: (String) -> Unit,
    onOpenLog: (String) -> Unit,
    currentSubTab: Int = 0
) {
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }
    var sortType by remember { mutableStateOf(TaskSortType.DEFAULT) }
    var localShowCreateDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<UnifiedTask?>(null) }
    var deletingTask by remember { mutableStateOf<UnifiedTask?>(null) }

    val scriptSizeMap = remember(uiState.scriptTree) {
        buildScriptSizeMap(uiState.scriptTree)
    }

    val filteredTasks = remember(searchQuery, selectedFilter, sortType, uiState.tasks) {
        val bySearch = if (searchQuery.isEmpty()) {
            uiState.tasks
        } else {
            uiState.tasks.filter { task ->
                task.name.contains(searchQuery, ignoreCase = true) ||
                        task.command.contains(searchQuery, ignoreCase = true) ||
                        task.schedule.contains(searchQuery, ignoreCase = true)
            }
        }
        val byFilter = when (selectedFilter) {
            "running" -> bySearch.filter { it.isRunning }
            "enabled" -> bySearch.filter { !it.isDisabled }
            "disabled" -> bySearch.filter { it.isDisabled }
            else -> bySearch
        }
        when (sortType) {
            TaskSortType.DEFAULT -> byFilter.sortedWith(
                compareByDescending<UnifiedTask> { it.isPinned }
                    .thenBy { it.isDisabled }
                    .thenBy { it.name }
            )
            TaskSortType.NAME_ASC -> byFilter.sortedWith(
                compareByDescending<UnifiedTask> { it.isPinned }
                    .thenBy { it.name.lowercase() }
            )
            TaskSortType.NAME_DESC -> byFilter.sortedWith(
                compareByDescending<UnifiedTask> { it.isPinned }
                    .thenByDescending { it.name.lowercase() }
            )
            TaskSortType.NEXT_RUN -> byFilter.sortedWith(
                compareByDescending<UnifiedTask> { it.isPinned }
                    .thenBy { it.nextRunTime.orEmpty().ifEmpty { "9999" } }
            )
            TaskSortType.LAST_RUN -> byFilter.sortedWith(
                compareByDescending<UnifiedTask> { it.isPinned }
                    .thenByDescending { it.lastExecutionTime ?: 0L }
            )
            TaskSortType.STATUS -> byFilter.sortedWith(
                compareByDescending<UnifiedTask> { it.isRunning }
                    .thenByDescending { it.isPinned }
                    .thenBy { it.isDisabled }
                    .thenBy { it.name }
            )
        }
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

        // 3. 任务状态筛选与效率工具栏（集成交互式状态药丸与排序/批量/新建）
        var showSortMenu by remember { mutableStateOf(false) }

        val totalCount = uiState.tasks.size
        val runningCount = uiState.tasks.count { it.isRunning }
        val enabledCount = uiState.tasks.count { !it.isDisabled }
        val disabledCount = uiState.tasks.count { it.isDisabled }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：状态筛选药丸（可点击快速筛选，同时显示实时统计与动态指示）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 全部
                val isAllSelected = selectedFilter == "all"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isAllSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.clickable { selectedFilter = "all" }
                ) {
                    Text(
                        text = "全部 $totalCount",
                        fontSize = 11.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                    )
                }

                // 运行中
                val isRunningSelected = selectedFilter == "running"
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isRunningSelected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                        runningCount > 0 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = when {
                        isRunningSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                        runningCount > 0 -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                        else -> null
                    },
                    modifier = Modifier.clickable { selectedFilter = "running" }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (runningCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha),
                                        shape = CircleShape
                                    )
                            )
                        }
                        Text(
                            text = "运行中 $runningCount",
                            fontSize = 11.sp,
                            fontWeight = if (isRunningSelected || runningCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isRunningSelected || runningCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 已启用
                val isEnabledSelected = selectedFilter == "enabled"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isEnabledSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isEnabledSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.clickable { selectedFilter = "enabled" }
                ) {
                    Text(
                        text = "已启用 $enabledCount",
                        fontSize = 11.sp,
                        fontWeight = if (isEnabledSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isEnabledSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                    )
                }

                // 已禁用
                val isDisabledSelected = selectedFilter == "disabled"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDisabledSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isDisabledSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.clickable { selectedFilter = "disabled" }
                ) {
                    Text(
                        text = "已禁用 $disabledCount",
                        fontSize = 11.sp,
                        fontWeight = if (isDisabledSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isDisabledSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // 右侧：效率工具栏 (多维排序 / 批量模式 / 快捷新建)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 排序下拉菜单
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (sortType != TaskSortType.DEFAULT)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = if (sortType != TaskSortType.DEFAULT)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        else null,
                        modifier = Modifier.clickable { showSortMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "排序",
                                modifier = Modifier.size(13.dp),
                                tint = if (sortType != TaskSortType.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = sortType.label,
                                fontSize = 11.sp,
                                fontWeight = if (sortType != TaskSortType.DEFAULT) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sortType != TaskSortType.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        TaskSortType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        type.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (sortType == type) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sortType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (sortType == type) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Spacer(Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    sortType = type
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                // 批量模式切换
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.isTaskBatchMode)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = if (uiState.isTaskBatchMode)
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else null,
                    modifier = Modifier.clickable {
                        viewModel.setTaskBatchMode(!uiState.isTaskBatchMode)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = "批量操作",
                            modifier = Modifier.size(13.dp),
                            tint = if (uiState.isTaskBatchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (uiState.isTaskBatchMode) "退出" else "批量",
                            fontSize = 11.sp,
                            fontWeight = if (uiState.isTaskBatchMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (uiState.isTaskBatchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 快捷新建按钮
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        localShowCreateDialog = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新建任务",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "新建",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
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
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshTasks() },
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
                            val scriptSize = remember(task.command, scriptSizeMap) {
                                findScriptSizeForCommand(task.command, scriptSizeMap)
                            }
                            TaskCard(
                                task = task,
                                scriptSize = scriptSize,
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

    if (showCreateDialog || localShowCreateDialog) {
        CreateTaskDialog(
            scriptFiles = uiState.scriptTree.extractScriptFiles(),
            onDismiss = {
                onDismissCreateDialog()
                localShowCreateDialog = false
            },
            onConfirm = { newTask ->
                viewModel.createTask(newTask)
                onDismissCreateDialog()
                localShowCreateDialog = false
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
    scriptSize: String? = null,
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
                .padding(horizontal = 10.dp, vertical = 4.dp),
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 22.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onToggle(task.isDisabled) },
                                contentAlignment = Alignment.Center
                            ) {
                                Switch(
                                    checked = !task.isDisabled,
                                    onCheckedChange = onToggle,
                                    modifier = Modifier.scale(0.68f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.command,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = mutedAlpha)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (task.lastRunningTime != null && task.lastRunningTime > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(3.dp)
                                    ) {
                                        Text(
                                            text = QinglongApiHelpers.formatSeconds(task.lastRunningTime),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (!scriptSize.isNullOrBlank()) {
                                    if (task.lastRunningTime != null && task.lastRunningTime > 0) {
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(3.dp)
                                    ) {
                                        Text(
                                            text = scriptSize,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 紧凑操作按钮行 (保持列表卡片紧凑整洁、高密度呈现)
                    if (!isBatchMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onTogglePin,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (task.isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop,
                                        contentDescription = if (task.isPinned) "已置顶" else "置顶",
                                        tint = if (task.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onRunOrStop,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    if (task.isRunning) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = "停止任务",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "立即执行",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onOpenLog,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Notes,
                                        contentDescription = "查看日志",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onEdit,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑任务",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onDelete,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除任务",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
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
        TaskVisualState.Queued -> Triple("排队中", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        TaskVisualState.Disabled -> Triple(
            "已禁用",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        )
        TaskVisualState.Ready -> Triple(
            "已启用",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary
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
    onConfirm: (UnifiedTask) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("0 8 * * *") }
    var showFilePicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    // 白虎与青龙高级配置项
    var preCommand by remember { mutableStateOf("") }
    var postCommand by remember { mutableStateOf("") }
    var timeoutStr by remember { mutableStateOf("30") }
    var workDir by remember { mutableStateOf("") }
    var retryCountStr by remember { mutableStateOf("0") }
    var retryIntervalStr by remember { mutableStateOf("0") }
    var randomRangeStr by remember { mutableStateOf("0") }
    var labelsStr by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建定时任务", fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    label = { Text("定时规则 (Cron)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val cronDesc = remember(schedule) {
                    com.panel.app.util.CronExpressionDescriber.describe(schedule)
                }
                if (cronDesc.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(cronDesc, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                val presets = remember {
                    com.panel.app.util.CronExpressionDescriber.PRESETS_5_PART
                }
                Text("常用预设:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(presets) { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (schedule == p.expression) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { schedule = p.expression }
                        ) {
                            Text(
                                text = p.label,
                                fontSize = 10.sp,
                                color = if (schedule == p.expression) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 高级配置切换按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showAdvanced) "收起高级配置 ▲" else "展开白虎更多配置 ▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = preCommand,
                        onValueChange = { preCommand = it },
                        label = { Text("前置命令 (选填)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postCommand,
                        onValueChange = { postCommand = it },
                        label = { Text("后置命令 (选填)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeoutStr,
                            onValueChange = { timeoutStr = it },
                            label = { Text("超时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = workDir,
                            onValueChange = { workDir = it },
                            label = { Text("工作目录(选填)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = retryCountStr,
                            onValueChange = { retryCountStr = it },
                            label = { Text("重试次数", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = retryIntervalStr,
                            onValueChange = { retryIntervalStr = it },
                            label = { Text("重试间隔(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = randomRangeStr,
                            onValueChange = { randomRangeStr = it },
                            label = { Text("随机延时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = labelsStr,
                        onValueChange = { labelsStr = it },
                        label = { Text("标签 (逗号分隔)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && command.isNotBlank()) {
                        val task = UnifiedTask(
                            id = "",
                            name = name.trim(),
                            command = command.trim(),
                            schedule = schedule.trim(),
                            statusText = "已就绪",
                            preCommand = preCommand.trim().ifBlank { null },
                            postCommand = postCommand.trim().ifBlank { null },
                            timeout = timeoutStr.toIntOrNull() ?: 30,
                            workDir = workDir.trim().ifBlank { null },
                            retryCount = retryCountStr.toIntOrNull() ?: 0,
                            retryInterval = retryIntervalStr.toIntOrNull() ?: 0,
                            randomRange = randomRangeStr.toIntOrNull() ?: 0,
                            labels = if (labelsStr.isBlank()) emptyList() else labelsStr.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        onConfirm(task)
                    }
                },
                enabled = name.isNotBlank() && command.isNotBlank()
            ) {
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
    var showAdvanced by remember { mutableStateOf(false) }

    var preCommand by remember { mutableStateOf(task.preCommand ?: "") }
    var postCommand by remember { mutableStateOf(task.postCommand ?: "") }
    var timeoutStr by remember { mutableStateOf(task.timeout.toString()) }
    var workDir by remember { mutableStateOf(task.workDir ?: "") }
    var retryCountStr by remember { mutableStateOf(task.retryCount.toString()) }
    var retryIntervalStr by remember { mutableStateOf(task.retryInterval.toString()) }
    var randomRangeStr by remember { mutableStateOf(task.randomRange.toString()) }
    var labelsStr by remember { mutableStateOf(task.labels.joinToString(", ")) }

    val scrollState = rememberScrollState()

    val cronDesc = remember(schedule) {
        com.panel.app.util.CronExpressionDescriber.describe(schedule)
    }

    val presets = remember(schedule) {
        if (schedule.trim().split("\\s+".toRegex()).size == 6)
            com.panel.app.util.CronExpressionDescriber.PRESETS_6_PART
        else
            com.panel.app.util.CronExpressionDescriber.PRESETS_5_PART
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑任务", fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                if (cronDesc.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(cronDesc, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Text("常用预设:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(presets) { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (schedule == p.expression) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { schedule = p.expression }
                        ) {
                            Text(
                                text = p.label,
                                fontSize = 10.sp,
                                color = if (schedule == p.expression) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 高级配置切换按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showAdvanced) "收起高级配置 ▲" else "展开白虎更多配置 ▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = preCommand,
                        onValueChange = { preCommand = it },
                        label = { Text("前置命令", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postCommand,
                        onValueChange = { postCommand = it },
                        label = { Text("后置命令", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeoutStr,
                            onValueChange = { timeoutStr = it },
                            label = { Text("超时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = workDir,
                            onValueChange = { workDir = it },
                            label = { Text("工作目录", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = retryCountStr,
                            onValueChange = { retryCountStr = it },
                            label = { Text("重试次数", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = retryIntervalStr,
                            onValueChange = { retryIntervalStr = it },
                            label = { Text("重试间隔(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = randomRangeStr,
                            onValueChange = { randomRangeStr = it },
                            label = { Text("随机延时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = labelsStr,
                        onValueChange = { labelsStr = it },
                        label = { Text("标签 (逗号分隔)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = task.copy(
                        name = name.trim(),
                        command = command.trim(),
                        schedule = schedule.trim(),
                        preCommand = preCommand.trim().ifBlank { null },
                        postCommand = postCommand.trim().ifBlank { null },
                        timeout = timeoutStr.toIntOrNull() ?: task.timeout,
                        workDir = workDir.trim().ifBlank { null },
                        retryCount = retryCountStr.toIntOrNull() ?: task.retryCount,
                        retryInterval = retryIntervalStr.toIntOrNull() ?: task.retryInterval,
                        randomRange = randomRangeStr.toIntOrNull() ?: task.randomRange,
                        labels = if (labelsStr.isBlank()) emptyList() else labelsStr.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    onConfirm(updated)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 遍历脚本树建立映射，提取每个脚本文件的尺寸（支持全路径、无前导斜杠路径和纯文件名匹配）
 */
fun buildScriptSizeMap(nodes: List<ScriptNode>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    fun traverse(list: List<ScriptNode>) {
        for (node in list) {
            if (!node.isDir && !node.size.isNullOrBlank() && node.size != "-") {
                val size = node.size
                map[node.path] = size
                map[node.path.trimStart('/')] = size
                map[node.name] = size
            }
            node.children?.let { traverse(it) }
        }
    }
    traverse(nodes)
    return map
}

/**
 * 从任务执行命令中解析出脚本路径或文件名，并从尺寸映射中查找文件大小
 */
fun findScriptSizeForCommand(command: String, sizeMap: Map<String, String>): String? {
    if (command.isBlank() || sizeMap.isEmpty()) return null
    val tokens = command.trim().split("\\s+".toRegex())
    for (token in tokens.reversed()) {
        val clean = token.removeSurrounding("\"", "'").removePrefix("./").removePrefix("/")
        if (clean.contains(".")) {
            sizeMap[clean]?.let { return it }
            val baseName = clean.substringAfterLast('/')
            sizeMap[baseName]?.let { return it }
        }
    }
    return null
}

