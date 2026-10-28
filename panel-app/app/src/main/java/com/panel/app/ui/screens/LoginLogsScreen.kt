package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginLogsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var logs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val loadLogs = {
        isLoading = true
        viewModel.loadLoginLogs { list ->
            logs = list
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else {
            logs.filter { entry ->
                val ip = entry["ip"]?.toString() ?: ""
                val address = entry["address"]?.toString() ?: ""
                val status = entry["status"]?.toString() ?: ""
                ip.contains(searchQuery, ignoreCase = true) ||
                        address.contains(searchQuery, ignoreCase = true) ||
                        status.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录日志 (${filteredLogs.size})", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = Icons.Default.Refresh,
                        label = "刷新",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            loadLogs()
                            Toast.makeText(context, "正在刷新登录日志...", Toast.LENGTH_SHORT).show()
                        }
                    )
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
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索 IP、归属地或登录状态...", fontSize = 12.sp) },
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (searchQuery.isEmpty()) "暂无登录日志记录" else "未匹配到相关登录日志", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredLogs) { log ->
                        val ip = log["ip"]?.toString() ?: "未知 IP"
                        val address = log["address"]?.toString() ?: log["info"]?.toString() ?: "本地网络/内网"
                        val time = formatLoginLogTime(
                            log["createdAt"]?.toString() ?: log["timestamp"]?.toString() ?: log["time"]?.toString()
                        )
                        val status = log["status"]?.toString() ?: "0"
                        val isSuccess = status == "0" || status.equals("success", ignoreCase = true)

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = ip, fontSize = 14.sp, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                                    Surface(
                                        color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (isSuccess) "登录成功" else "登录失败",
                                            fontSize = 9.sp,
                                            color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(text = "归属地/设备: $address", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "时间: $time", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 登录日志时间格式化：青龙返回毫秒时间戳，兼容 ISO 字符串与已格式化文本 */
private fun formatLoginLogTime(raw: String?): String {
    val value = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return "--"
    // 纯数字按毫秒时间戳处理（秒级时间戳自动 ×1000）
    value.toLongOrNull()?.let { ts ->
        val millis = if (ts > 1_000_000_000_000L) ts else ts * 1000
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
    // ISO 8601（如 2026-10-28T14:00:00.000Z）转本地可读格式
    runCatching {
        val parsed = java.time.Instant.parse(value)
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(parsed.toEpochMilli()))
    }
    return value
}
