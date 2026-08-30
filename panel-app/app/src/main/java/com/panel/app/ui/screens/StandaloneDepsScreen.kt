package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
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
import com.panel.app.data.model.UnifiedDep
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneDepsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
    }

    var showInstallDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("all") }
    var selectedStatus by remember { mutableStateOf("all") }
    var deletingDep by remember { mutableStateOf<UnifiedDep?>(null) }
    var viewingLogDep by remember { mutableStateOf<UnifiedDep?>(null) }
    var depLogContent by remember { mutableStateOf("正在获取安装与构建日志...") }

    val filteredDeps = remember(searchQuery, selectedType, selectedStatus, uiState.deps) {
        uiState.deps.filter { dep ->
            val matchType = if (selectedType == "all") true else dep.type.equals(selectedType, ignoreCase = true)
            val matchStatus = when (selectedStatus) {
                "installed" -> dep.status == 1
                "failed" -> dep.status == 2 || dep.status == 4
                "installing" -> dep.status == 0 || dep.status == 3
                else -> true
            }
            val matchSearch = if (searchQuery.isEmpty()) true else {
                dep.name.contains(searchQuery, ignoreCase = true) ||
                        (dep.remarks?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchType && matchStatus && matchSearch
        }
    }

    val selectedDeps = remember(filteredDeps) { filteredDeps.filter { it.selected } }
    val allSelected = remember(filteredDeps, selectedDeps) { filteredDeps.isNotEmpty() && selectedDeps.size == filteredDeps.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "依赖包管理 (${uiState.deps.size})", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text(text = "按面板官方 API 安装与卸载运行环境依赖", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    Button(
                        onClick = { showInstallDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("安装依赖", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 全宽搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索已安装的依赖包名称或备注...", fontSize = 12.sp) },
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

            // 类型与状态筛选标签行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("all" to "全部 (${uiState.deps.size})", "nodejs" to "Node.js", "python3" to "Python3", "linux" to "Linux").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.setDepBatchMode(!uiState.isDepBatchMode) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (uiState.isDepBatchMode) "完成" else "批量", fontSize = 11.sp)
                }
            }

            // 状态筛选标签行 (已安装, 安装中, 安装失败)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val installedCount = uiState.deps.count { it.status == 1 }
                val failedCount = uiState.deps.count { it.status == 2 || it.status == 4 }
                val installingCount = uiState.deps.count { it.status == 0 || it.status == 3 }
                listOf(
                    "all" to "全部状态",
                    "installed" to "已安装 ($installedCount)",
                    "failed" to "失败 ($failedCount)",
                    "installing" to "安装中 ($installingCount)"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedStatus == key,
                        onClick = { selectedStatus = key },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // 顶部批量操作栏 (置于顶部，绝不占用主体列表空间)
            if (uiState.isDepBatchMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { viewModel.selectAllDeps(it) },
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = if (selectedDeps.isEmpty()) "全选" else "已选 ${selectedDeps.size}",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val ids = selectedDeps.map { it.id }
                                    viewModel.forceDeleteDeps(ids)
                                },
                                enabled = selectedDeps.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("强制清除", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val ids = selectedDeps.map { it.id }
                                    viewModel.batchDeleteDeps(ids)
                                },
                                enabled = selectedDeps.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("批量卸载", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = { viewModel.refreshCurrentPanel() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredDeps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(if (uiState.isLoading) "正在同步依赖列表..." else "未发现符合条件的依赖包", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredDeps, key = { it.id }) { dep ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (uiState.isDepBatchMode) {
                                                viewModel.toggleDepSelection(dep.id)
                                            } else {
                                                viewingLogDep = dep
                                                viewModel.getDepLog(dep.id) { log -> depLogContent = log }
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (uiState.isDepBatchMode) {
                                            Checkbox(
                                                checked = dep.selected,
                                                onCheckedChange = { viewModel.toggleDepSelection(dep.id) },
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(text = dep.name, fontSize = 13.sp, style = MaterialTheme.typography.titleMedium)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = dep.type.uppercase(),
                                                        fontSize = 8.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                val (statusText, statusBg, statusFg) = when (dep.status) {
                                                    0 -> Triple("安装中", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                                    1 -> Triple("已安装", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                                    2 -> Triple("安装失败", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                                    3 -> Triple("卸载中", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                                    4 -> Triple("卸载失败", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                                    else -> Triple("已安装", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                                }
                                                Surface(
                                                    color = statusBg,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = statusText,
                                                        fontSize = 9.sp,
                                                        color = statusFg,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (!dep.remarks.isNullOrEmpty()) {
                                                Text(text = dep.remarks, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // 若为安装失败，提供快速重新安装/重试按键
                                            if (dep.status == 2 || dep.status == 4) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.installDep(dep.name, dep.version, dep.type, dep.remarks ?: "")
                                                        Toast.makeText(context, "正在重新安装 [${dep.name}]...", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = "重试安装", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            // 查看安装/构建日志按键
                                            IconButton(
                                                onClick = {
                                                    viewingLogDep = dep
                                                    viewModel.getDepLog(dep.id) { log -> depLogContent = log }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "安装日志", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }

                                            // 卸载按键（触发二次确认弹窗）
                                            IconButton(
                                                onClick = { deletingDep = dep },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "卸载依赖", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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

    // 1. 依赖安装 Dialog
    if (showInstallDialog) {
        InstallDepDialog(
            onDismiss = { showInstallDialog = false },
            onConfirm = { name, version, type, remark ->
                viewModel.installDep(name, version, type, remark)
                showInstallDialog = false
            }
        )
    }

    // 2. 依赖卸载二次确认 Dialog (解决删除没有二次弹窗的严重问题)
    if (deletingDep != null) {
        AlertDialog(
            onDismissRequest = { deletingDep = null },
            title = { Text("确认卸载依赖包", fontSize = 16.sp) },
            text = {
                Text(
                    text = "确定要从服务端卸载依赖包 [${deletingDep!!.name}] 吗？\n卸载后依赖此包的定时任务脚本可能会执行报错。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.forceDeleteDeps(listOf(deletingDep!!.id))
                            deletingDep = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("强制清除", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.deleteDep(deletingDep!!.id, deletingDep!!.type)
                            deletingDep = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("常规卸载", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDep = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 依赖安装日志 Dialog (解决看不到依赖日志的严重问题)
    if (viewingLogDep != null) {
        AlertDialog(
            onDismissRequest = { viewingLogDep = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(text = "安装日志 - ${viewingLogDep!!.name}", fontSize = 15.sp)
                }
            },
            text = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(depLogContent.lines()) { line ->
                            Text(text = line, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewingLogDep = null }) {
                    Text("关闭")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    clipboardManager.setText(AnnotatedString(depLogContent))
                    Toast.makeText(context, "依赖日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制日志")
                }
            }
        )
    }
}

@Composable
fun InstallDepDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("nodejs") }
    var remark by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装环境依赖包", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 依赖环境类型选择 (对齐官方 Node.js / Python3 / Linux)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = type == "nodejs",
                        onClick = { type = "nodejs" },
                        label = { Text("Node.js (npm)", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = type == "python3",
                        onClick = { type = "python3" },
                        label = { Text("Python3 (pip)", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = type == "linux",
                        onClick = { type = "linux" },
                        label = { Text("Linux", fontSize = 10.sp) }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("依赖包名称 (例如 requests / axios)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("指定版本号 (可选，留空为 latest)", fontSize = 11.sp) },
                    placeholder = { Text("例如 2.31.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("依赖用途备注 (可选)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotEmpty()) {
                    onConfirm(name, version, type, remark)
                }
            }) {
                Text("开始安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
