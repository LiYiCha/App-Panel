package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.RunningTaskInfo
import com.panel.app.data.model.TaskInstanceRecord
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenLogViewer: (String, String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("all") }
    var viewMode by remember { mutableStateOf(0) } // 0: 按脚本归类, 1: 时间线流水
    var isLoading by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf<List<TaskInstanceRecord>>(emptyList()) }
    val expandedScripts = remember { mutableStateMapOf<String, Boolean>() }

    // 运行中任务：只有拿到真实运行实例才能精确停止
    var runningTasks by remember { mutableStateOf<List<RunningTaskInfo>>(emptyList()) }

    fun loadData() {
        isLoading = true
        viewModel.loadAllExecutionHistory { list ->
            historyList = list
            isLoading = false
        }
        viewModel.loadRunningTasks { list, _ ->
            runningTasks = list
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val filteredList = remember(historyList, searchQuery, selectedStatus) {
        historyList.filter { item ->
            val matchStatus = when (selectedStatus) {
                "success" -> item.exitCode == 0 || item.statusText == "成功"
                "failed" -> item.exitCode != 0 || item.statusText == "失败"
                else -> true
            }
            val matchSearch = if (searchQuery.isBlank()) true else {
                item.taskName.contains(searchQuery, ignoreCase = true) ||
                        item.startTime.contains(searchQuery, ignoreCase = true) ||
                        item.id.contains(searchQuery, ignoreCase = true)
            }
            matchStatus && matchSearch
        }
    }

    // 按脚本/任务名称归类分组
    val groupedByScript = remember(filteredList) {
        filteredList.groupBy { it.taskName.ifBlank { "未命名脚本任务" } }
            .toList()
            .sortedByDescending { it.second.size }
    }

    // 默认展开前 5 个脚本组
    LaunchedEffect(groupedByScript) {
        groupedByScript.take(5).forEach { (scriptName, _) ->
            if (!expandedScripts.containsKey(scriptName)) {
                expandedScripts[scriptName] = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("执行日志与历史 (${historyList.size})", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("按脚本归类归档 · 点击展开即看各次输出", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
            // 0. 运行中任务（可精确停止实例）
            if (runningTasks.isNotEmpty()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "运行中 (${runningTasks.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        runningTasks.forEach { task ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        buildString {
                                            append("任务 #${task.taskId}")
                                            task.elapsedSeconds?.let { append(" · 已运行 ${it}s") }
                                            task.pid?.let { append(" · PID $it") }
                                        },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.stopRunningTask(task)
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ loadData() }, 1200)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("停止", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 1. 搜索框与显示模式切换
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索脚本名称、执行日期或记录 ID...", fontSize = 12.sp) },
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

            // 2. 状态过滤 Chip 与 视图模式切换栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val successCount = historyList.count { it.exitCode == 0 || it.statusText == "成功" }
                    val failCount = historyList.count { it.exitCode != 0 || it.statusText == "失败" }

                    FilterChip(
                        selected = selectedStatus == "all",
                        onClick = { selectedStatus = "all" },
                        label = { Text("全部", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedStatus == "success",
                        onClick = { selectedStatus = "success" },
                        label = { Text("成功($successCount)", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedStatus == "failed",
                        onClick = { selectedStatus = "failed" },
                        label = { Text("失败($failCount)", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }

                // 按脚本归类 vs 时间线流水切换
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (viewMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { viewMode = 0 }
                        ) {
                            Text(
                                "按脚本归类",
                                fontSize = 10.sp,
                                fontWeight = if (viewMode == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (viewMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (viewMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { viewMode = 1 }
                        ) {
                            Text(
                                "流水明细",
                                fontSize = 10.sp,
                                fontWeight = if (viewMode == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (viewMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. 历史记录列表（支持按脚本归类分组）
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { loadData() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(if (isLoading) "正在同步执行历史记录..." else "暂无匹配的执行历史记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (viewMode == 0) {
                        // 模式 A：按脚本归类分组展示
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(groupedByScript, key = { it.first }) { (scriptName, records) ->
                                val isExpanded = expandedScripts[scriptName] ?: false
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (isExpanded) 180f else 0f,
                                    label = "arrow"
                                )
                                val successCount = records.count { it.exitCode == 0 || it.statusText == "成功" }
                                val failCount = records.count { it.exitCode != 0 || it.statusText == "失败" }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // 脚本归类头部
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedScripts[scriptName] = !isExpanded }
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
                                                        text = scriptName,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "已执行 ${records.size} 次 · 成功 $successCount · 失败 $failCount",
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
                                                        text = "${records.size}",
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

                                        // 展开后的各次执行日志明细
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

                                                records.forEach { record ->
                                                    val isSuccess = record.exitCode == 0 || record.statusText == "成功"
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                onOpenLogViewer(scriptName, record.id)
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
                                                                Surface(
                                                                    color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                                    shape = RoundedCornerShape(3.dp)
                                                                ) {
                                                                    Text(
                                                                        text = if (isSuccess) "成功" else "失败(${record.exitCode})",
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                                Text(
                                                                    text = record.startTime,
                                                                    fontSize = 11.sp,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (record.duration.isNotBlank()) {
                                                                    Text(
                                                                        text = "· 耗时 ${record.duration}",
                                                                        fontSize = 10.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }

                                                            Text(
                                                                text = "查看输出 >",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
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
                    } else {
                        // 模式 B：时间线流水展开
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredList, key = { it.id }) { record ->
                                val isSuccess = record.exitCode == 0 || record.statusText == "成功"
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val title = record.taskName.ifBlank { "执行记录 #${record.id}" }
                                            onOpenLogViewer(title, record.id)
                                        },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = record.taskName.ifBlank { "执行记录 #${record.id}" },
                                                fontSize = 13.sp,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isSuccess) "成功 (0)" else "失败 (${record.exitCode})",
                                                    fontSize = 10.sp,
                                                    color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(text = record.startTime, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(text = if (record.duration.isNotBlank()) record.duration else "完成", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
