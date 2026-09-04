package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneLogScreen(
    taskName: String,
    initialLog: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 拦截物理返回按键，确保干净返回主界面
    BackHandler {
        onBack()
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableIntStateOf(11) }

    // 纯动态日志列表：无任何硬编码 Mock 假数据
    val rawLines = remember {
        val lines = if (initialLog.isNotEmpty()) {
            initialLog.lines().toMutableList()
        } else {
            mutableListOf(
                "正在连接面板日志流...",
                "执行任务: $taskName",
                "等待远端输出..."
            )
        }
        mutableStateListOf<String>().apply { addAll(lines) }
    }

    // 后台静默环形缓冲保护（默认 3000 行滑动窗口），杜绝 OOM，不在 UI 显示干扰用户的横幅
    val maxCapacity = 3000
    LaunchedEffect(rawLines.size) {
        if (rawLines.size > maxCapacity) {
            val toRemove = rawLines.size - maxCapacity
            repeat(toRemove) {
                if (rawLines.isNotEmpty()) rawLines.removeAt(0)
            }
        }
    }

    val filteredLines = remember(searchQuery, rawLines) {
        if (searchQuery.isEmpty()) {
            rawLines
        } else {
            rawLines.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    fun copyLog() {
        val content = if (searchQuery.isEmpty()) {
            rawLines.joinToString("\n")
        } else {
            filteredLines.joinToString("\n")
        }
        clipboardManager.setText(AnnotatedString(content))
        Toast.makeText(context, "日志已复制到剪贴板 (${if (searchQuery.isEmpty()) rawLines.size else filteredLines.size} 行)", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = taskName,
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
                    // 切换搜索栏
                    IconButton(onClick = { showSearchBar = !showSearchBar }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(18.dp))
                    }
                    // 字号缩放
                    IconButton(onClick = { if (fontSizeSp > 9) fontSizeSp -= 1 }, modifier = Modifier.size(30.dp)) {
                        Text("A-", fontSize = 10.sp)
                    }
                    IconButton(onClick = { if (fontSizeSp < 20) fontSizeSp += 1 }, modifier = Modifier.size(30.dp)) {
                        Text("A+", fontSize = 10.sp)
                    }
                    // 滚动到底部
                    IconButton(onClick = {
                        coroutineScope.launch {
                            if (filteredLines.isNotEmpty()) {
                                listState.animateScrollToItem(filteredLines.size - 1)
                            }
                        }
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = "底部", modifier = Modifier.size(18.dp))
                    }
                    // 清空日志
                    IconButton(onClick = {
                        rawLines.clear()
                        Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空", modifier = Modifier.size(18.dp))
                    }
                    // 单一、专业的复制按钮
                    IconButton(onClick = { copyLog() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制日志", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 可折叠搜索过滤栏
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("过滤日志关键词...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            // 专业终端主窗体
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (filteredLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无日志输出", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom != 1f) {
                                            val target = fontSizeSp * zoom
                                            fontSizeSp = target.coerceIn(8f, 26f).toInt()
                                        }
                                    }
                                }
                        ) {
                        itemsIndexed(filteredLines) { index, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSizeSp.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSizeSp.sp,
                                    color = when {
                                        line.contains("SUCCESS") || line.contains("成功") -> Color(0xFF2E7D32)
                                        line.contains("ERROR") || line.contains("FAIL") || line.contains("失败") -> MaterialTheme.colorScheme.error
                                        line.contains("WARN") -> Color(0xFFF57C00)
                                        line.contains("EXEC") -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}
}
