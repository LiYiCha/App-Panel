package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.panel.app.data.model.UnifiedSubscription
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {},
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    onCreateClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isBatchMode by remember { mutableStateOf(false) }
    var editingSub by remember { mutableStateOf<UnifiedSubscription?>(null) }
    var viewLogSubId by remember { mutableStateOf<String?>(null) }
    var subLogContent by remember { mutableStateOf("") }
    var isLogLoading by remember { mutableStateOf(false) }
    var deletingSub by remember { mutableStateOf<UnifiedSubscription?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val filteredSubs = remember(searchQuery, uiState.subscriptions) {
        if (searchQuery.isBlank()) uiState.subscriptions
        else uiState.subscriptions.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true) ||
                    it.schedule.contains(searchQuery, ignoreCase = true) ||
                    it.whitelist.contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedSubs = remember(uiState.subscriptions) { uiState.subscriptions.filter { it.selected } }
    val allSelected = remember(filteredSubs, selectedSubs) { filteredSubs.isNotEmpty() && filteredSubs.all { it.selected } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("仓库订阅", fontSize = 15.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
        // 1. 全宽搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索任务名称、仓库地址、Cron 规则...", fontSize = 12.sp) },
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

        // 2. 顶栏操作区：刷新、批量删除、新建/同步仓库
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { isBatchMode = !isBatchMode },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = if (isBatchMode) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(if (isBatchMode) Icons.Default.Checklist else Icons.Default.ChecklistRtl, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isBatchMode) "退出批量" else "批量", fontSize = 11.sp)
                }

                if (isBatchMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.selectAllSubscriptions(!allSelected) }
                            .padding(horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { viewModel.selectAllSubscriptions(it) },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("全选 (${selectedSubs.size})", fontSize = 11.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isBatchMode && selectedSubs.isNotEmpty()) {
                    Button(
                        onClick = { showBatchDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除 (${selectedSubs.size})", fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = onCreateClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("同步仓库", fontSize = 11.sp)
                }
            }
        }

        // 3. 仓库列表
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshCurrentPanel() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredSubs.isEmpty()) {
                    // PullToRefreshBox 完全依赖 nested scroll 手势，空态必须是可滚动容器，否则下拉无响应
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        contentPadding = PaddingValues(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (uiState.isLoading) "正在刷新仓库同步任务..." else "暂无匹配的仓库同步任务",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                    ) {
                        items(filteredSubs, key = { it.id }) { sub ->
                            RepoSyncCard(
                                sub = sub,
                                isBatchMode = isBatchMode,
                                onSelect = { viewModel.toggleSubscriptionSelection(sub.id) },
                                onRunOrStop = {
                                    if (sub.isRunning) {
                                        viewModel.stopSubscription(sub.id)
                                    } else {
                                        viewModel.runSubscription(sub.id)
                                    }
                                },
                                onViewLog = {
                                    viewLogSubId = sub.id
                                    isLogLoading = true
                                    viewModel.getSubscriptionLog(sub.id) { log ->
                                        subLogContent = log
                                        isLogLoading = false
                                    }
                                },
                                onEdit = { editingSub = sub },
                                onDelete = { deletingSub = sub }
                            )
                        }
                    }
                }
            }
        }
    }
    }

    // 新建仓库弹窗
    if (showCreateDialog) {
        SubscriptionDialog(
            initial = null,
            onDismiss = onDismissCreateDialog,
            onConfirm = { newSub ->
                viewModel.createSubscription(newSub)
                onDismissCreateDialog()
            }
        )
    }

    // 编辑仓库弹窗
    if (editingSub != null) {
        SubscriptionDialog(
            initial = editingSub,
            onDismiss = { editingSub = null },
            onConfirm = { updated ->
                viewModel.updateSubscription(updated)
                editingSub = null
            }
        )
    }

    // 单个删除确认弹窗
    if (deletingSub != null) {
        AlertDialog(
            onDismissRequest = { deletingSub = null },
            title = { Text("确认删除仓库任务", fontSize = 15.sp) },
            text = { Text("确定要删除仓库同步任务 [${deletingSub?.name}] 吗？删除后将不再自动同步该仓库。", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubscription(deletingSub!!.id)
                        deletingSub = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSub = null }) { Text("取消") }
            }
        )
    }

    // 批量删除确认弹窗
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("确认批量删除", fontSize = 15.sp) },
            text = { Text("确定要批量删除选中的 ${selectedSubs.size} 个仓库同步任务吗？该操作不可撤销。", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.batchDeleteSubscriptions(selectedSubs.map { it.id })
                        showBatchDeleteConfirm = false
                        isBatchMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("批量删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // 查看拉取日志弹窗
    if (viewLogSubId != null) {
        AlertDialog(
            onDismissRequest = { viewLogSubId = null },
            title = { Text("仓库同步日志", fontSize = 15.sp) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 360.dp)) {
                    if (isLogLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else {
                        OutlinedTextField(
                            value = subLogContent.ifBlank { "暂无同步日志" },
                            onValueChange = {},
                            readOnly = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (subLogContent.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(subLogContent))
                            Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制")
                }
            },
            dismissButton = {
                Button(onClick = { viewLogSubId = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
fun RepoSyncCard(
    sub: UnifiedSubscription,
    isBatchMode: Boolean,
    onSelect: () -> Unit,
    onRunOrStop: () -> Unit,
    onViewLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isBatchMode) { onSelect() },
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. 首行：复选框/图标、名称、状态徽章与快捷操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (isBatchMode) {
                        Checkbox(
                            checked = sub.selected,
                            onCheckedChange = { onSelect() },
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.ForkRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = sub.name,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                // 操作按键区
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 状态徽章 (⚡)
                    Surface(
                        color = if (sub.isRunning) Color(0xFFE8F5E9) else if (sub.isDisabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (sub.isRunning) Color(0xFF2E7D32) else if (sub.isDisabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = if (sub.isRunning) "同步中" else sub.statusText,
                                fontSize = 9.sp,
                                color = if (sub.isRunning) Color(0xFF2E7D32) else if (sub.isDisabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ActionButtonSmall(
                        icon = if (sub.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        label = if (sub.isRunning) "停止" else "同步",
                        tint = if (sub.isRunning) Color(0xFFEF4444) else Color(0xFF10B981),
                        onClick = onRunOrStop
                    )
                    ActionButtonSmall(
                        icon = Icons.AutoMirrored.Filled.Note,
                        label = "日志",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onViewLog
                    )
                    ActionButtonSmall(
                        icon = Icons.Default.Edit,
                        label = "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onEdit
                    )
                    ActionButtonSmall(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onDelete
                    )
                }
            }

            // 2. 第二行：独立标签展示行 (语言环境徽章 + 节点徽章)，彻底避免挤压标题
            if (sub.languages.isNotEmpty() || sub.location.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 语言环境徽章
                    sub.languages.forEach { lang ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = lang,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 本地/节点徽章
                    if (sub.location.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = sub.location,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 2. 仓库地址
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "[git] ${sub.url}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // 3. 同步周期与上一次/下一次执行时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "周期: ${sub.schedule}${if (sub.branch != "main") " • 分支: ${sub.branch}" else ""}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sub.lastRunTime != null && sub.lastRunTime != "--") {
                        Text(text = "上: ${sub.lastRunTime}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sub.nextRunTime != null && sub.nextRunTime != "--") {
                        Text(text = "下: ${sub.nextRunTime}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionDialog(
    initial: UnifiedSubscription?,
    onDismiss: () -> Unit,
    onConfirm: (UnifiedSubscription) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var branch by remember { mutableStateOf(initial?.branch ?: "main") }
    var schedule by remember { mutableStateOf(initial?.schedule ?: "0 0 * * *") }
    var whitelist by remember { mutableStateOf(initial?.whitelist ?: "") }
    var blacklist by remember { mutableStateOf(initial?.blacklist ?: "") }
    var autoAddCron by remember { mutableStateOf(initial?.autoAddCron ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "同步/新建 Git 仓库" else "编辑仓库同步配置", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        if (name.isEmpty() && it.contains("/")) {
                            name = it.substringAfterLast("/").removeSuffix(".git")
                        }
                    },
                    label = { Text("仓库地址 (Git URL)") },
                    placeholder = { Text("https://github.com/owner/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text("分支 (Branch)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = schedule,
                        onValueChange = { schedule = it },
                        label = { Text("定时规则 (Cron)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = whitelist,
                    onValueChange = { whitelist = it },
                    label = { Text("白名单关键词 (选填，逗号或竖线分割)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoAddCron, onCheckedChange = { autoAddCron = it })
                    Spacer(Modifier.width(4.dp))
                    Text("自动识别脚本注释并添加定时调度任务", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        val sub = UnifiedSubscription(
                            id = initial?.id ?: "",
                            name = name.trim(),
                            type = "public-repo",
                            url = url.trim(),
                            branch = branch.trim().ifEmpty { "main" },
                            schedule = schedule.trim().ifEmpty { "0 0 * * *" },
                            whitelist = whitelist.trim(),
                            blacklist = blacklist.trim(),
                            autoAddCron = autoAddCron
                        )
                        onConfirm(sub)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(if (initial == null) "开始同步" else "保存修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
