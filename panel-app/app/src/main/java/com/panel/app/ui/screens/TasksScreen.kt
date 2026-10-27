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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import com.panel.app.ui.components.ScriptFilePickerSection
import com.panel.app.ui.components.SearchPillField
import com.panel.app.ui.components.StatusBadge
import com.panel.app.ui.components.StatusTone
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.panel.app.ui.viewmodel.MainViewModel

/** 任务卡右滑开合状态（右滑露出 启动/停止 + 启用/禁用 两枚动作） */
private enum class TaskSwipeValue { Closed, Open }

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
    currentSubTab: Int = 0,
    sortType: TaskSortType = TaskSortType.DEFAULT,
    onSortTypeChange: (TaskSortType) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }
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

        // 2. 药丸形搜索框（三页统一组件）
        SearchPillField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "搜索任务名称、执行命令或 Cron 规则..."
        )

        // 3. 任务状态筛选药丸（排序/批量/新建已收进顶栏，本行只留筛选）
        val totalCount = uiState.tasks.size
        val runningCount = uiState.tasks.count { it.isRunning }
        val enabledCount = uiState.tasks.count { !it.isDisabled }
        val disabledCount = uiState.tasks.count { it.isDisabled }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态筛选药丸（可点击快速筛选，同时显示实时统计与动态指示）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 全部
                val isAllSelected = selectedFilter == "all"
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isAllSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.clickable { selectedFilter = "all" }
                ) {
                    Text(
                        text = "全部 $totalCount",
                        fontSize = 11.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
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
                    shape = RoundedCornerShape(50),
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
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
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
                    shape = RoundedCornerShape(50),
                    color = if (isEnabledSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isEnabledSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.clickable { selectedFilter = "enabled" }
                ) {
                    Text(
                        text = "已启用 $enabledCount",
                        fontSize = 11.sp,
                        fontWeight = if (isEnabledSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isEnabledSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                    )
                }

                // 已禁用
                val isDisabledSelected = selectedFilter == "disabled"
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDisabledSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isDisabledSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.clickable { selectedFilter = "disabled" }
                ) {
                    Text(
                        text = "已禁用 $disabledCount",
                        fontSize = 11.sp,
                        fontWeight = if (isDisabledSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isDisabledSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
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
            scriptFiles = uiState.scriptTree.extractScriptFiles(),
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

@OptIn(ExperimentalFoundationApi::class)
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
    // 禁用不是删除：整卡半透明置灰（名称/徽标/内容一起退后），红色只留给真正失败
    val mutedAlpha = if (state == TaskVisualState.Disabled) 0.75f else 1f
    val cardAlpha = if (state == TaskVisualState.Disabled) 0.55f else 1f

    // 上次执行时间：统一走 TimeFormat.short（今天 HH:mm / 更早 MM-dd HH:mm / 无 "-"）
    val timeText = remember(task.lastExecutionTime) {
        com.panel.app.util.TimeFormat.short(task.lastExecutionTime)
    }

    // 注意：remember 不能放在条件分支里（批量模式切换会导致槽位错位），
    // 状态始终创建，仅在非批量模式下应用滑动
    val swipeState = rememberTaskSwipeState()
    val scope = rememberCoroutineScope()

    // 防御：列表回收/复用可能把滑动中间状态带回来，表现为"未滑动就露出动作层"
    LaunchedEffect(task.id) {
        if (swipeState.currentValue != TaskSwipeValue.Closed) {
            swipeState.snapTo(TaskSwipeValue.Closed)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 右滑呼出的动作层：启动/停止 + 启用/禁用 + 删除（行内不再放常驻按钮与开关）
        if (!isBatchMode) {
            Row(
                modifier = Modifier.matchParentSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                SwipeAction(
                    container = if (task.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    content = if (task.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = if (task.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    label = if (task.isRunning) "停止" else "启动",
                    onClick = {
                        onRunOrStop()
                        scope.launch { swipeState.animateTo(TaskSwipeValue.Closed) }
                    }
                )
                Spacer(Modifier.width(6.dp))
                SwipeAction(
                    container = if (task.isDisabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    content = if (task.isDisabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = if (task.isDisabled) Icons.Default.CheckCircle else Icons.Default.Block,
                    label = if (task.isDisabled) "启用" else "禁用",
                    onClick = {
                        onToggle(task.isDisabled)
                        scope.launch { swipeState.animateTo(TaskSwipeValue.Closed) }
                    }
                )
                Spacer(Modifier.width(6.dp))
                // 删除：仍走确认弹窗，防误删
                SwipeAction(
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.error,
                    icon = Icons.Default.Delete,
                    label = "删除",
                    onClick = {
                        scope.launch { swipeState.animateTo(TaskSwipeValue.Closed) }
                        onDelete()
                    }
                )
            }
        }
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                // 卡片本体必须不透明：半透明会让底下的右滑动作层透出来（禁用态曾因此"未滑先露按钮"）
                .then(
                    if (!isBatchMode) Modifier
                        .offset { IntOffset(swipeState.requireOffset().roundToInt(), 0) }
                        .anchoredDraggable(swipeState, Orientation.Horizontal)
                    else Modifier
                )
                .clickable {
                    when {
                        !isBatchMode && swipeState.currentValue == TaskSwipeValue.Open ->
                            scope.launch { swipeState.animateTo(TaskSwipeValue.Closed) }
                        isBatchMode -> onSelect()
                        else -> onClick()
                    }
                },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
        // 置灰只作用于内容层，不作用于卡片容器：容器半透明会让右滑动作层从底下透出来
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = cardAlpha }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                // 左缘状态色条：扫一眼列表即可按颜色区分 运行/启用/禁用
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(38.dp)
                        .background(
                            when (state) {
                                TaskVisualState.Running -> MaterialTheme.colorScheme.primary
                                TaskVisualState.Queued -> MaterialTheme.colorScheme.tertiary
                                TaskVisualState.Disabled -> MaterialTheme.colorScheme.outlineVariant
                                TaskVisualState.Ready -> MaterialTheme.colorScheme.secondary
                            },
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(Modifier.width(7.dp))
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
                                color = if (state == TaskVisualState.Disabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            StateBadge(state)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = timeText,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(2.dp))
                    // Cron 规则条：时钟图标 + 等宽表达式
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = task.schedule,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = mutedAlpha)
                            )
                        }
                    }

                    // 命令行：终端图标 + 命令 + 耗时/脚本大小徽章
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = task.command,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = mutedAlpha)
                        )
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

                    // 具体操作（日志/编辑/删除/置顶）已收敛到任务详情页顶栏与日志历史 Tab，
                    // 行内只保留右滑快捷动作，保持列表高密度
                }
            }
        }
    }
}

/** 右滑动作宽度：72dp 动作 × 3（启动/停止、启用/禁用、删除）+ 6dp 间距 × 2 */
private val TaskSwipeOpenWidth = 228.dp

/** 任务卡右滑状态：Closed(0) / Open(-150dp)，松手吸附，无 dismiss 语义 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberTaskSwipeState(): AnchoredDraggableState<TaskSwipeValue> {
    val density = LocalDensity.current
    val state = remember {
        AnchoredDraggableState(
            initialValue = TaskSwipeValue.Closed,
            positionalThreshold = { totalDistance -> totalDistance * 0.4f },
            velocityThreshold = { with(density) { 80.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec = exponentialDecay()
        )
    }
    SideEffect {
        state.updateAnchors(
            DraggableAnchors {
                TaskSwipeValue.Closed at 0f
                TaskSwipeValue.Open at -with(density) { TaskSwipeOpenWidth.toPx() }
            }
        )
    }
    return state
}

/** 右滑露出的单枚动作块 */
@Composable
private fun SwipeAction(
    container: Color,
    content: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
        modifier = Modifier
            .width(72.dp)
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = content)
        }
    }
}

private enum class TaskVisualState { Running, Queued, Disabled, Ready }

/** 状态徽章：已启用(绿+勾)、已禁用(中性灰+禁止)、运行中(主色/脉冲) —— 公共组件薄映射 */
@Composable
private fun StateBadge(state: TaskVisualState) {
    when (state) {
        TaskVisualState.Running -> StatusBadge("运行中", StatusTone.PRIMARY, pulsing = true)
        TaskVisualState.Queued -> StatusBadge("排队中", StatusTone.TERTIARY)
        TaskVisualState.Disabled -> StatusBadge("已禁用", StatusTone.NEUTRAL, icon = Icons.Default.Block)
        TaskVisualState.Ready -> StatusBadge("已启用", StatusTone.SECONDARY, icon = Icons.Default.CheckCircle)
    }
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

