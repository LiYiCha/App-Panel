package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel

data class ScriptLogGroup(
    val scriptName: String,
    val logs: List<LogItem>
)

data class LogItem(
    val fileName: String,
    val fullPath: String,
    val id: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerLogsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenLogViewer: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var logTree by remember { mutableStateOf<com.google.gson.JsonElement?>(null) }
    var activeLogPath by remember { mutableStateOf<String?>(null) }
    var activeLogContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isReadingFile by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val reloadTree = {
        isLoading = true
        viewModel.loadServerLogsTree { elem ->
            logTree = elem
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reloadTree()
    }

    BackHandler {
        if (activeLogPath != null) {
            activeLogPath = null
        } else {
            onBack()
        }
    }

    // 将服务端的目录树按脚本/任务名称深度归类分组
    val categorizedGroups = remember(logTree) {
        val groups = mutableListOf<ScriptLogGroup>()
        val rootFiles = mutableListOf<LogItem>()

        val arr = if (logTree?.isJsonObject == true && logTree!!.asJsonObject.has("data")) {
            logTree!!.asJsonObject.get("data").asJsonArray
        } else if (logTree?.isJsonArray == true) {
            logTree!!.asJsonArray
        } else null

        arr?.forEach { elem ->
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "未命名任务"
                val isDir = obj.get("type")?.asString == "directory" || obj.has("children")
                if (isDir && obj.has("children") && obj.get("children").isJsonArray) {
                    val subFiles = mutableListOf<LogItem>()
                    obj.get("children").asJsonArray.forEach { sub ->
                        if (sub.isJsonObject) {
                            val subObj = sub.asJsonObject
                            val subTitle = subObj.get("title")?.asString ?: subObj.get("name")?.asString ?: "log"
                            val id = subObj.get("id")?.asString
                            val fullPath = if (!id.isNullOrEmpty()) id else "$title/$subTitle"
                            subFiles.add(LogItem(subTitle, fullPath, id))
                        }
                    }
                    if (subFiles.isNotEmpty()) {
                        // 倒序排列日志（最新日志文件排在最前）
                        subFiles.sortByDescending { it.fileName }
                        groups.add(ScriptLogGroup(title, subFiles))
                    }
                } else {
                    val id = obj.get("id")?.asString
                    rootFiles.add(LogItem(title, id ?: title, id))
                }
            }
        }
        if (rootFiles.isNotEmpty()) {
            rootFiles.sortByDescending { it.fileName }
            groups.add(0, ScriptLogGroup("系统根日志 / 独立任务", rootFiles))
        }
        // 按脚本名称升序排列组
        groups.sortedBy { it.scriptName }
    }

    // 默认展开所有组
    LaunchedEffect(categorizedGroups) {
        categorizedGroups.forEach { group ->
            if (!expandedGroups.containsKey(group.scriptName)) {
                expandedGroups[group.scriptName] = true
            }
        }
    }

    // 搜索过滤（支持根据脚本名称或具体日志文件名过滤）
    val filteredGroups = remember(categorizedGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            categorizedGroups
        } else {
            val q = searchQuery.trim().lowercase()
            categorizedGroups.mapNotNull { group ->
                val matchGroup = group.scriptName.lowercase().contains(q)
                val matchedLogs = group.logs.filter { it.fileName.lowercase().contains(q) }
                if (matchGroup) {
                    group
                } else if (matchedLogs.isNotEmpty()) {
                    group.copy(logs = matchedLogs)
                } else null
            }
        }
    }

    val totalLogsCount = remember(categorizedGroups) {
        categorizedGroups.sumOf { it.logs.size }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (activeLogPath != null) activeLogPath!! else "服务端日志 (按脚本归类)",
                            fontSize = 16.sp,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (activeLogPath == null) {
                            Text(
                                text = "共 ${categorizedGroups.size} 个脚本模块 · $totalLogsCount 份日志记录",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeLogPath != null) {
                            activeLogPath = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (activeLogPath != null) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(activeLogContent))
                            Toast.makeText(context, "日志内容已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制日志")
                        }
                    } else {
                        IconButton(onClick = {
                            reloadTree()
                            Toast.makeText(context, "正在刷新服务端日志列表...", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (activeLogPath != null) {
                // 日志内容阅读器界面
                if (isReadingFile) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        val lines = remember(activeLogContent) { activeLogContent.lines() }
                        if (lines.isEmpty() || activeLogContent.isBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("该日志文件内容为空", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(lines) { line ->
                                    Text(
                                        text = line,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 脚本归类分组主界面
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 搜索框与快捷折叠操作栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索脚本名称或日志日期...", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        TextButton(
                            onClick = {
                                val allExpanded = categorizedGroups.all { expandedGroups[it.scriptName] == true }
                                categorizedGroups.forEach {
                                    expandedGroups[it.scriptName] = !allExpanded
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val allExpanded = categorizedGroups.all { expandedGroups[it.scriptName] == true }
                            Text(if (allExpanded) "全部折叠" else "全部展开", fontSize = 11.sp)
                        }
                    }

                    // 2. 按脚本分组展示列表
                    if (filteredGroups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "未搜索到匹配的脚本日志" else "暂无服务端日志文件",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredGroups, key = { it.scriptName }) { group ->
                                val isExpanded = expandedGroups[group.scriptName] ?: false
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (isExpanded) 180f else 0f,
                                    label = "arrow"
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // 脚本分组卡片头部（点击可折叠/展开）
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedGroups[group.scriptName] = !isExpanded
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = group.scriptName,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "包含 ${group.logs.size} 份历史执行日志",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${group.logs.size}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .rotate(arrowRotation),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // 展开后的具体日志文件列表
                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                    thickness = 0.5.dp
                                                )

                                                group.logs.forEach { log ->
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                onOpenLogViewer(log.fileName, log.fullPath)
                                                            },
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.weight(1f),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.AutoMirrored.Filled.Article,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.secondary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = log.fileName,
                                                                    fontSize = 11.sp,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    maxLines = 1,
                                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                )
                                                            }

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "查看",
                                                                    fontSize = 10.sp,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                                Icon(
                                                                    Icons.Default.ChevronRight,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
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
