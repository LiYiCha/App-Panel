package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    title: String,
    logPath: String = "",
    taskId: String = "",
    initialContent: String = "",
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var logContent by remember { mutableStateOf(initialContent) }
    var isLoading by remember { mutableStateOf(initialContent.isBlank()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableFloatStateOf(11f) }

    fun fetchLog() {
        isLoading = true
        if (taskId.isNotBlank()) {
            viewModel.getTaskLog(taskId) { content ->
                logContent = content
                isLoading = false
            }
        } else if (logPath.isNotBlank()) {
            val normalized = logPath.replace('\\', '/')
            val fileName = normalized.substringAfterLast('/')
            val dirPath = if (normalized.contains('/')) normalized.substringBeforeLast('/') else ""
            viewModel.loadServerLogDetail(dirPath, fileName) { content ->
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

    // 跟随模式：订阅真实日志流，任务结束前持续刷新内容
    var isFollowing by remember { mutableStateOf(false) }
    LaunchedEffect(isFollowing, taskId) {
        if (!isFollowing || taskId.isBlank()) return@LaunchedEffect
        viewModel.streamTaskLog(taskId)
            .catch { e -> logContent = "日志流中断: ${e.message}" }
            .collect { latest ->
                logContent = latest
                isLoading = false
            }
        // 流结束 = 任务已结束，退出跟随
        isFollowing = false
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
            listState.animateScrollToItem(filteredLines.size - 1)
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
                    IconButton(onClick = {
                        autoScrollToBottom = !autoScrollToBottom
                        Toast.makeText(context, if (autoScrollToBottom) "已开启自动滚动" else "已关闭自动滚动", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = "自动滚动",
                            tint = if (autoScrollToBottom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) searchQuery = ""
                    }) {
                        Icon(if (isSearchOpen) Icons.Default.Close else Icons.Default.Search, contentDescription = "搜索")
                    }
                    // 跟随模式：任务运行中持续刷新，结束后自动停止
                    if (taskId.isNotBlank()) {
                        IconButton(onClick = {
                            isFollowing = !isFollowing
                            Toast.makeText(
                                context,
                                if (isFollowing) "已开启实时跟随" else "已停止跟随",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "实时跟随",
                                tint = if (isFollowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { fetchLog() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新日志")
                    }
                    IconButton(onClick = {
                        if (logContent.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(logContent))
                            Toast.makeText(context, "日志全文已复制", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制日志")
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
                    Column(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("正在拉取终端运行日志...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else if (lines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("暂无日志输出", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    androidx.compose.foundation.text.selection.SelectionContainer(
                        modifier = Modifier.fillMaxSize().weight(1f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom != 1f) {
                                            val target = fontSizeSp * zoom
                                            fontSizeSp = target.coerceIn(8f, 26f)
                                        }
                                    }
                                }
                        ) {
                            itemsIndexed(filteredLines) { _, (originalIndex, line) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                ) {
                                    // 行号 (靠左紧凑对齐，主题动态色)
                                    Text(
                                        text = "${originalIndex + 1}".padStart(4, ' '),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = maxOf(8f, fontSizeSp - 1f).sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.width(36.dp)
                                    )

                                    // 行内容
                                    val annotated = if (searchQuery.isNotEmpty() && line.contains(searchQuery, ignoreCase = true)) {
                                        buildAnnotatedString {
                                            val startIdx = line.indexOf(searchQuery, ignoreCase = true)
                                            append(line.substring(0, startIdx))
                                            pushStyle(SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold))
                                            append(line.substring(startIdx, startIdx + searchQuery.length))
                                            pop()
                                            append(line.substring(startIdx + searchQuery.length))
                                        }
                                    } else {
                                        buildAnnotatedString { append(line) }
                                    }

                                    val textColor = when {
                                        line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true) -> MaterialTheme.colorScheme.error
                                        line.contains("success", ignoreCase = true) || line.contains("done", ignoreCase = true) -> MaterialTheme.colorScheme.primary
                                        line.contains("warn", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Text(
                                        text = annotated,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp,
                                        color = textColor,
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
