package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.logger.AppLogger
import com.panel.app.data.logger.LogEntry
import com.panel.app.data.logger.LogLevel
import com.panel.app.data.logger.LogStorage
import com.panel.app.ui.viewmodel.MainViewModel
import com.panel.app.util.FolderLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperConsoleScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    val logs = AppLogger.logs
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var detailEntry by remember { mutableStateOf<LogEntry?>(null) }
    var retentionDays by remember { mutableStateOf(LogStorage.retentionDays()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs.size, selectedFilter, searchQuery) {
        logs.filter { entry ->
            val matchFilter = when (selectedFilter) {
                "HTTP" -> entry.level == LogLevel.HTTP_OK || entry.level == LogLevel.HTTP_ERR
                "ERR" -> entry.level == LogLevel.ERROR || entry.level == LogLevel.HTTP_ERR
                else -> true
            }
            val matchSearch = searchQuery.isEmpty() ||
                    entry.url?.contains(searchQuery, ignoreCase = true) == true ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.requestBody?.contains(searchQuery, ignoreCase = true) == true ||
                    entry.responseBody?.contains(searchQuery, ignoreCase = true) == true ||
                    entry.error?.contains(searchQuery, ignoreCase = true) == true
            matchFilter && matchSearch
        }
    }

    fun copyText(text: String?, label: String) {
        if (text.isNullOrBlank()) return
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者日志 (${filteredLogs.size})", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val allText = filteredLogs.joinToString("\n\n") { it.toClipboardText() }
                        copyText(allText, "已复制 ${filteredLogs.size} 条日志")
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制全部")
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清除日志")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 控制行：开发者模式开关 + 打开目录
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isDevMode) "● 开发者模式·记录中" else "○ 开发者模式·已关闭",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (uiState.isDevMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.isDevMode,
                    onCheckedChange = { viewModel.toggleDevMode(it) },
                    modifier = Modifier.scale(0.65f)
                )
                IconButton(onClick = { FolderLauncher.open(context, LogStorage.logDirPath()) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "打开日志目录", modifier = Modifier.size(18.dp))
                }
            }

            // 保留时长（启动时自动清理更早的日志文件）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("保留", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(7 to "7天", 30 to "30天", 90 to "90天", LogStorage.KEEP_FOREVER to "永久").forEach { (days, label) ->
                    FilterChip(
                        selected = retentionDays == days,
                        onClick = {
                            LogStorage.setRetentionDays(days)
                            retentionDays = days
                        },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            // 搜索 + 筛选
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索 URL / 参数 / 响应 / 错误", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "ALL" to "全部(${logs.size})",
                    "HTTP" to "HTTP(${logs.count { it.level == LogLevel.HTTP_OK || it.level == LogLevel.HTTP_ERR }})",
                    "ERR" to "错误(${logs.count { it.level == LogLevel.ERROR || it.level == LogLevel.HTTP_ERR }})"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            // 日志列表：一行一条，点击弹出详情
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            !uiState.isDevMode -> "开发者模式未开启，暂无记录"
                            searchQuery.isNotEmpty() -> "未匹配到日志"
                            else -> "暂无日志"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val isErr = entry.level == LogLevel.HTTP_ERR || entry.level == LogLevel.ERROR
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailEntry = entry }
                                .padding(horizontal = 4.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = entry.timestamp.takeIf { it.length >= 19 }?.substring(11, 19) ?: entry.timestamp,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!entry.method.isNullOrBlank()) {
                                    Text(
                                        text = entry.method,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (entry.code != null && entry.code > 0) {
                                    Text(
                                        text = "${entry.code}",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (entry.code in 200..299) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                                if (entry.durationMs != null) {
                                    Text(
                                        text = "${entry.durationMs}ms",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isErr) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                            }
                            Text(
                                text = entry.summaryLine(),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    // 单条详情：底部弹层自带 scrim，点外部关闭，不存在点击穿透
    detailEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { detailEntry = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${entry.timestamp}  [${entry.level}]  ${entry.tag}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!entry.url.isNullOrBlank()) {
                    DetailSection("URL", entry.url) { copyText(entry.url, "URL 已复制") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (entry.code != null) Text("状态码: ${entry.code}", fontSize = 11.sp)
                    if (entry.durationMs != null) Text("耗时: ${entry.durationMs}ms", fontSize = 11.sp)
                }
                if (entry.message.isNotBlank() && entry.message != entry.url) {
                    DetailSection("消息", entry.message) { copyText(entry.message, "消息已复制") }
                }
                if (!entry.requestBody.isNullOrBlank()) {
                    DetailSection("请求入参", entry.requestBody) { copyText(entry.requestBody, "入参已复制") }
                }
                if (!entry.responseBody.isNullOrBlank()) {
                    DetailSection("响应数据", entry.responseBody) { copyText(entry.responseBody, "响应已复制") }
                }
                if (!entry.error.isNullOrBlank()) {
                    DetailSection("异常报错", entry.error, contentColor = MaterialTheme.colorScheme.error) {
                        copyText(entry.error, "异常已复制")
                    }
                }
                Button(
                    onClick = { copyText(entry.toClipboardText(), "整条日志已复制") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制整条日志", fontSize = 12.sp)
                }
            }
        }
    }

    // 真清除：内存 + 磁盘全部删除，下次冷启动不会回灌旧日志
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除全部日志", fontSize = 15.sp) },
            text = { Text("将删除所有按天归档的日志文件并清空当前记录，删除后无法恢复。", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        LogStorage.clearAll()
                        AppLogger.clear()
                        showClearConfirm = false
                        Toast.makeText(context, "全部日志已清除", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("全部清除", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", fontSize = 12.sp)
                }
            }
        )
    }
}

private fun LogEntry.summaryLine(): String {
    if (!url.isNullOrBlank()) {
        val path = url!!.substringAfter("://").substringAfter('/')
        return if (message.isNotBlank() && message != "[$method] $code (${durationMs}ms) $url") {
            "$path · $message"
        } else path
    }
    return message.replace('\n', ' ')
}

private fun LogEntry.toClipboardText(): String = buildString {
    append("[${timestamp}] [${level}] ")
    if (!method.isNullOrBlank()) append("${method} ")
    if (!url.isNullOrBlank()) append(url)
    if (code != null) append(" (code=$code")
    if (durationMs != null) append(", ${durationMs}ms")
    if (code != null) append(")")
    append('\n')
    if (message.isNotBlank() && message != url) append("Msg: $message\n")
    if (!requestBody.isNullOrBlank()) append("Req: $requestBody\n")
    if (!responseBody.isNullOrBlank()) append("Resp: $responseBody\n")
    if (!error.isNullOrBlank()) append("Error: $error\n")
}

@Composable
private fun DetailSection(
    title: String,
    content: String?,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onCopy, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(22.dp)) {
                Text("复制", fontSize = 10.sp)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = content ?: "",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = contentColor,
                modifier = Modifier
                    .padding(6.dp)
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}
