package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val MAX_LOG_CHARS = 1024 * 1024  // 与前端 MAX_LOG_VIEW_CHARS 一致（1MB）

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    title: String,
    logPath: String = "",
    taskId: String = "",
    initialContent: String = "",
    viewModel: MainViewModel,
    onBack: () -> Unit,
    taskRunning: Boolean = false
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val cachedLog = remember(taskId, logPath) {
        if (logPath.isNotBlank()) viewModel.getLogFromCache(logPath)
        else if (taskId.isNotBlank()) viewModel.getLogFromCache(taskId)
        else null
    }
    var logContent by remember { mutableStateOf(if (initialContent.isNotBlank()) initialContent else (cachedLog ?: "")) }
    var isLoading by remember { mutableStateOf(initialContent.isBlank() && cachedLog.isNullOrBlank()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableFloatStateOf(11f) }

    fun fetchLog() {
        isLoading = true
        if (logPath.isNotBlank()) {
            val normalized = logPath.replace('\\', '/')
            val fileName = normalized.substringAfterLast('/')
            val dirPath = if (normalized.contains('/')) normalized.substringBeforeLast('/') else ""
            viewModel.loadServerLogDetail(dirPath, fileName, fallbackTaskId = taskId) { content ->
                logContent = content
                isLoading = false
            }
        } else if (taskId.isNotBlank()) {
            viewModel.getTaskLog(taskId) { content ->
                logContent = content
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(title, logPath, taskId) {
        if (initialContent.isBlank() && (taskId.isNotBlank() || logPath.isNotBlank())) {
            fetchLog()
        }
    }

    // 跟随模式：仅当任务正在运行时默认开启，流结束后自动停止；用户可手动切换
    var isFollowing by remember { mutableStateOf(taskRunning && (taskId.isNotBlank() || logPath.isNotBlank())) }
    var isStreamActive by remember { mutableStateOf(false) }
    val streamTargetId = if (taskId.isNotBlank()) taskId else if (taskRunning) logPath else ""

    // 注意：isStreamActive 不能放进 key —— 在 effect 内部写它会使 key 立即变化，
    // 导致协程被取消后重新执行一遍，日志流被重复启动。
    LaunchedEffect(isFollowing, streamTargetId) {
        if (!isFollowing || streamTargetId.isBlank()) {
            isStreamActive = false
            return@LaunchedEffect
        }
        isStreamActive = true
        try {
            viewModel.streamTaskLog(streamTargetId)
                .catch { e ->
                    if (e !is kotlinx.coroutines.CancellationException) {
                        if (logContent.isBlank() && isLoading) {
                            logContent = "日志流已结束或中断: ${e.message ?: ""}"
                        }
                    }
                }
                .collect { latest ->
                    // 与前端一致：超 1MB 时截取末尾部分，避免内存溢出
                    if (latest.isNotBlank()) {
                        logContent = if (latest.length > MAX_LOG_CHARS) latest.takeLast(MAX_LOG_CHARS) else latest
                    }
                    isLoading = false
                }
        } finally {
            // 流结束 = 任务已结束，退出跟随
            isStreamActive = false
            isFollowing = false
            isLoading = false
        }
    }

    val lines = remember(logContent) {
        if (logContent.isBlank()) emptyList() else logContent.lines()
    }

    val filteredLines = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) {
            lines.mapIndexed { idx, line -> idx to line }
        } else {
            lines.mapIndexedNotNull { idx, line ->
                if (line.contains(searchQuery, ignoreCase = true)) idx to line else null
            }
        }
    }

    var autoScrollToBottom by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // 自动滚动到最新日志底部
    LaunchedEffect(filteredLines.size, autoScrollToBottom) {
        if (autoScrollToBottom && filteredLines.isNotEmpty()) {
            // 日志流可能每秒追加多次；动画会排队并造成明显卡顿，直接定位到尾部。
            listState.scrollToItem(filteredLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (logPath.isNotBlank() || taskId.isNotBlank()) {
                            Text(
                                text = logPath.ifBlank { "Task ID: $taskId" },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 自动滚动到底部开关
                    ActionButtonSmall(
                        icon = Icons.Default.VerticalAlignBottom,
                        label = "滚动",
                        tint = if (autoScrollToBottom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            autoScrollToBottom = !autoScrollToBottom
                            Toast.makeText(context, if (autoScrollToBottom) "已开启自动滚动" else "已关闭自动滚动", Toast.LENGTH_SHORT).show()
                        }
                    )
                    // 搜索 / 关闭搜索
                    ActionButtonSmall(
                        icon = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                        label = if (isSearchOpen) "关闭" else "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            isSearchOpen = !isSearchOpen
                            if (!isSearchOpen) searchQuery = ""
                        }
                    )
                    // 实时跟随指示器与刷新按钮
                    if (taskId.isNotBlank() || logPath.isNotBlank()) {
                        if (isFollowing && isStreamActive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Default.PlayArrow, contentDescription = "实时跟随中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        } else if (!isFollowing) {
                            // 任务已结束或查看历史日志，显示手动刷新按钮
                            ActionButtonSmall(
                                icon = Icons.Default.Refresh,
                                label = "刷新",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { fetchLog() }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 搜索栏移到内容区顶部，彻底解决在 TopAppBar 内部被截断、挤压的问题
                if (isSearchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索日志内容 (支持双指手势缩放字号)...", fontSize = 11.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("正在拉取终端运行日志...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                } else if (lines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("暂无日志输出", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            itemsIndexed(filteredLines, key = { _, item -> item.first }) { _, (originalIndex, line) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${originalIndex + 1}".padStart(4, ' '),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = maxOf(8f, fontSizeSp - 1f).sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.width(36.dp)
                                    )
                                    val annotated = if (searchQuery.isNotEmpty() && line.contains(searchQuery, ignoreCase = true)) {
                                        buildAnnotatedString {
                                            val startIdx = line.indexOf(searchQuery, ignoreCase = true)
                                            append(line.substring(0, startIdx))
                                            pushStyle(SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold))
                                            append(line.substring(startIdx, startIdx + searchQuery.length))
                                            pop()
                                            append(line.substring(startIdx + searchQuery.length))
                                        }
                                    } else buildAnnotatedString { append(line) }
                                    Text(
                                        text = annotated,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp,
                                        color = when {
                                            line.contains("error", true) || line.contains("failed", true) -> MaterialTheme.colorScheme.error
                                            line.contains("success", true) || line.contains("done", true) -> MaterialTheme.colorScheme.primary
                                            line.contains("warn", true) -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 快速回顶与到底部悬浮小控制器 (快速滑轮滑动辅助)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部", modifier = Modifier.size(20.dp))
                }
                SmallFloatingActionButton(
                    onClick = { scope.launch { if (filteredLines.isNotEmpty()) listState.animateScrollToItem(filteredLines.size - 1) } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "滚到底部", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
