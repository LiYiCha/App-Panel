package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.logger.AppLogger
import com.panel.app.data.logger.LogLevel
import com.panel.app.data.logger.LogStorage
import com.panel.app.ui.viewmodel.MainViewModel
import com.panel.app.util.LogDirOpener

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
    var selectedLevel by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }
    var retentionDays by remember { mutableStateOf(LogStorage.retentionDays()) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs.size, selectedLevel, searchQuery) {
        logs.filter { entry ->
            val matchLevel = when (selectedLevel) {
                "HTTP_OK" -> entry.level == LogLevel.HTTP_OK
                "HTTP_ERR" -> entry.level == LogLevel.HTTP_ERR
                "ERR" -> entry.level == LogLevel.ERROR || entry.level == LogLevel.HTTP_ERR
                else -> true
            }
            val matchSearch = if (searchQuery.isEmpty()) true else {
                (entry.url?.contains(searchQuery, ignoreCase = true) == true) ||
                        (entry.message.contains(searchQuery, ignoreCase = true)) ||
                        (entry.requestBody?.contains(searchQuery, ignoreCase = true) == true) ||
                        (entry.responseBody?.contains(searchQuery, ignoreCase = true) == true) ||
                        (entry.error?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchLevel && matchSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者调试控制台 (${filteredLogs.size})", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val allText = filteredLogs.joinToString("\n\n") { entry ->
                            buildString {
                                append("[${entry.timestamp}] [${entry.level}] ${entry.method ?: ""} ${entry.url ?: ""} (Code: ${entry.code ?: 0}, ${entry.durationMs ?: 0}ms)\n")
                                if (!entry.requestBody.isNullOrBlank()) append("Req: ${entry.requestBody}\n")
                                if (!entry.responseBody.isNullOrBlank()) append("Resp: ${entry.responseBody}\n")
                                if (!entry.error.isNullOrBlank()) append("Error: ${entry.error}\n")
                            }
                        }
                        clipboardManager.setText(AnnotatedString(allText))
                        Toast.makeText(context, "已复制所有抓取日志到剪贴板！", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制日志")
                    }
                    IconButton(onClick = {
                        AppLogger.clear()
                        Toast.makeText(context, "已清空界面记录（文件日志保留）", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空界面记录")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. 开发者抓取状态卡片 (Dev Mode Status Banner)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (uiState.isDevMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isDevMode) "● 开发者日志实时捕获中" else "○ 开发者日志记录已暂停",
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (uiState.isDevMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (uiState.isDevMode) "完整记录 HTTP 请求、入参、响应体与系统报错" else "开启开关后即可开始抓取所有网络与系统错误",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isDevMode,
                        onCheckedChange = { viewModel.toggleDevMode(it) },
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }

            // 2. 日志落盘控制条（保留时长 / 清除当前与全部 / 用文件管理器打开）
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("日志目录（按天归档，可长期留存分析）", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(LogStorage.logDirPath()))
                                Toast.makeText(context, "日志目录路径已复制", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("复制路径", fontSize = 10.sp)
                        }
                    }

                    Text(
                        text = LogStorage.logDirPath(),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "日志保留时长（启动时自动清理更早的日志文件）",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(7 to "7 天", 30 to "30 天", 90 to "90 天", LogStorage.KEEP_FOREVER to "永久").forEach { (days, label) ->
                            FilterChip(
                                selected = retentionDays == days,
                                onClick = {
                                    LogStorage.setRetentionDays(days)
                                    retentionDays = days
                                    Toast.makeText(context, "日志保留时长已设为 $label", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                LogStorage.clearCurrent()
                                AppLogger.clear()
                                Toast.makeText(context, "已清除当前日志（今天 + 界面记录）", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("清除当前日志", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = { showClearAllConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("清除全部日志", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                val error = LogDirOpener.open(context)
                                error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("打开日志目录", fontSize = 10.sp)
                        }
                    }

                    Text(
                        text = "崩溃日志会自动同步写入文件（无需开启开发者模式）；MT 管理器等工具可直接浏览上述目录。",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索请求 URL、参数、响应体或错误...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // 4. 类别筛选 Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf(
                    "ALL" to "全部 (${logs.size})",
                    "HTTP_OK" to "成功 (${logs.count { it.level == LogLevel.HTTP_OK }})",
                    "HTTP_ERR" to "HTTP 异常 (${logs.count { it.level == LogLevel.HTTP_ERR }})",
                    "ERR" to "错误/崩溃 (${logs.count { it.level == LogLevel.ERROR }})"
                )
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedLevel == key,
                        onClick = { selectedLevel = key },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // 5. 详细日志列表
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (!uiState.isDevMode) "开发者模式未开启，暂无抓取记录" else if (searchQuery.isNotEmpty()) "未匹配到相关调试日志" else "暂无抓取记录，请进行网络操作",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val isExpanded = expandedLogId == entry.id
                        val isErr = entry.level == LogLevel.HTTP_ERR || entry.level == LogLevel.ERROR

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedLogId = if (isExpanded) null else entry.id
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isErr) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // 头部行：Method + Status Code + Duration + Timestamp
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!entry.method.isNullOrBlank()) {
                                            Surface(
                                                color = when (entry.method) {
                                                    "GET" -> MaterialTheme.colorScheme.primaryContainer
                                                    "POST" -> MaterialTheme.colorScheme.tertiaryContainer
                                                    "PUT" -> MaterialTheme.colorScheme.secondaryContainer
                                                    "DELETE" -> MaterialTheme.colorScheme.errorContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = entry.method,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (entry.code != null && entry.code > 0) {
                                            Surface(
                                                color = if (entry.code in 200..299) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${entry.code}",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (entry.durationMs != null) {
                                            Text(text = "${entry.durationMs}ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = entry.timestamp, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // URL 行
                                if (!entry.url.isNullOrBlank()) {
                                    Text(
                                        text = entry.url,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = if (isExpanded) 10 else 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(text = entry.message, fontSize = 11.sp)
                                }

                                // 展开详情区 (入参、响应体、错误堆栈)
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                        // 请求体 / 参数
                                        if (!entry.requestBody.isNullOrBlank()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("请求入参 (Request Body):", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    TextButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(entry.requestBody))
                                                            Toast.makeText(context, "入参已复制", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(0.dp),
                                                        modifier = Modifier.height(20.dp)
                                                    ) {
                                                        Text("复制", fontSize = 9.sp)
                                                    }
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = entry.requestBody,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(6.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 响应体
                                        if (!entry.responseBody.isNullOrBlank()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("响应数据 (Response Body):", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    TextButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(entry.responseBody))
                                                            Toast.makeText(context, "响应已复制", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(0.dp),
                                                        modifier = Modifier.height(20.dp)
                                                    ) {
                                                        Text("复制", fontSize = 9.sp)
                                                    }
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = entry.responseBody,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(6.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 异常报错
                                        if (!entry.error.isNullOrBlank()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("异常报错 (Error):", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = entry.error,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 清除全部日志二次确认（删除所有归档文件，不可恢复）
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清除全部日志", fontSize = 15.sp) },
            text = {
                Text(
                    text = "将删除本地全部按天归档的日志文件，并清空当前界面记录，删除后无法恢复。确定继续吗？",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        LogStorage.clearAll()
                        AppLogger.clear()
                        showClearAllConfirm = false
                        Toast.makeText(context, "全部日志已清除", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("全部清除", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("取消", fontSize = 12.sp)
                }
            }
        )
    }
}
