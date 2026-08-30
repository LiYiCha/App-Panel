package com.panel.app.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var isLoading by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf<List<TaskInstanceRecord>>(emptyList()) }

    fun loadData() {
        isLoading = true
        viewModel.loadAllExecutionHistory { list ->
            historyList = list
            isLoading = false
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("执行历史 (${historyList.size})", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("全平台调度流水与各次运行输出归档", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            // 1. 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索任务名称、ID 或执行时间...", fontSize = 12.sp) },
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

            // 2. 状态过滤 Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val successCount = historyList.count { it.exitCode == 0 || it.statusText == "成功" }
                val failCount = historyList.count { it.exitCode != 0 || it.statusText == "失败" }

                FilterChip(
                    selected = selectedStatus == "all",
                    onClick = { selectedStatus = "all" },
                    label = { Text("全部 (${historyList.size})", fontSize = 11.sp) },
                    modifier = Modifier.height(30.dp)
                )
                FilterChip(
                    selected = selectedStatus == "success",
                    onClick = { selectedStatus = "success" },
                    label = { Text("成功 ($successCount)", fontSize = 11.sp) },
                    modifier = Modifier.height(30.dp)
                )
                FilterChip(
                    selected = selectedStatus == "failed",
                    onClick = { selectedStatus = "failed" },
                    label = { Text("失败 ($failCount)", fontSize = 11.sp) },
                    modifier = Modifier.height(30.dp)
                )
            }

            // 3. 历史流水列表
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
                    } else {
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
