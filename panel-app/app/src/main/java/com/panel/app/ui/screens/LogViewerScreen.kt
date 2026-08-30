package com.panel.app.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
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
import com.panel.app.ui.viewmodel.MainViewModel
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var logContent by remember { mutableStateOf(initialContent) }
    var isLoading by remember { mutableStateOf(initialContent.isBlank()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }

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
                    if (isSearchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索日志内容...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )
                    } else {
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
                                    text = if (logPath.isNotBlank()) logPath else "Task ID: $taskId",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在拉取终端运行日志...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else if (lines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无日志输出", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                androidx.compose.foundation.text.selection.SelectionContainer(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
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
                                    fontSize = 10.sp,
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
                                    fontSize = 11.sp,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
