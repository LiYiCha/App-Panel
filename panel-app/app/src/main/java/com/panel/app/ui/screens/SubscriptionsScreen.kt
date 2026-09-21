package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.UnifiedSubscription
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.viewmodel.MainViewModel
import com.panel.app.util.CronExpressionDescriber

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

    var localShowCreateDialog by remember { mutableStateOf(false) }
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
                    onClick = {
                        localShowCreateDialog = true
                        onCreateClick()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增订阅", fontSize = 11.sp)
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshSubscriptions() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredSubs.isEmpty()) {
                    // PullToRefreshBox 完全依赖 nested scroll 手势，空态必须是可滚动容器，否则下拉无响应
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
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
    if (showCreateDialog || localShowCreateDialog) {
        SubscriptionDialog(
            initial = null,
            onDismiss = {
                localShowCreateDialog = false
                onDismissCreateDialog()
            },
            onConfirm = { newSub ->
                viewModel.createSubscription(newSub)
                localShowCreateDialog = false
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isRunning = sub.isRunning
    val statusColor = when {
        isRunning -> MaterialTheme.colorScheme.secondary
        sub.isDisabled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isBatchMode) { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isRunning) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                 else if (sub.selected && isBatchMode) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                 else androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. 顶部标题行：图标/复选框 + 名称 + 状态微标 + 分支标签 + 右侧操作按钮
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
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = sub.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // 状态徽标 (紧随标题，不另起一行)
                    Surface(
                        color = when {
                            isRunning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            sub.isDisabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (isRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                )
                            }
                            Text(
                                text = if (isRunning) "同步中" else if (sub.isDisabled) "已禁用" else "就绪",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }

                    // 分支标签 (增加最大宽度限制与省略保护)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = sub.branch.ifBlank { "main" },
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .widthIn(max = 68.dp)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // 操作按钮区 (包含立即同步/停止、日志、编辑、删除)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ActionButtonSmall(
                        icon = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        label = if (isRunning) "停止" else "同步",
                        tint = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        onClick = onRunOrStop
                    )
                    ActionButtonSmall(
                        icon = Icons.AutoMirrored.Filled.Notes,
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

            // 2. 仓库地址行（增加 weight 约束，防止长地址挤压复制图标）
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboardManager.setText(AnnotatedString(sub.url))
                        Toast.makeText(context, "仓库地址已复制", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "git",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = sub.url,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制地址",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // 3. 配置规则与属性标签（横向滚动 + 最大宽度限制，避免长白名单/黑名单破损布局）
            val hasTags = sub.autoAddCron || sub.whitelist.isNotBlank() || sub.blacklist.isNotBlank() || sub.languages.isNotEmpty() || sub.location.isNotBlank()
            if (hasTags) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sub.autoAddCron) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "自动添加任务",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (sub.whitelist.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "白名单: ${sub.whitelist}",
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier
                                    .widthIn(max = 140.dp)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (sub.blacklist.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "黑名单: ${sub.blacklist}",
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .widthIn(max = 140.dp)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    sub.languages.forEach { lang ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = lang,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // 4. 定时调度与最近执行状态（两端加装权重限制，超长时自然截断）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = sub.schedule,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val cronDesc = remember(sub.schedule) { CronExpressionDescriber.describe(sub.schedule) }
                    if (cronDesc.isNotBlank()) {
                        Text(
                            text = "($cronDesc)",
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!sub.lastRunTime.isNullOrBlank() && sub.lastRunTime != "--") {
                        Text(
                            text = "上: ${sub.lastRunTime}",
                            fontSize = 9.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!sub.nextRunTime.isNullOrBlank() && sub.nextRunTime != "--") {
                        Text(
                            text = "下: ${sub.nextRunTime}",
                            fontSize = 9.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.outline
                        )
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
    var extensions by remember { mutableStateOf(initial?.extensions ?: "") }
    var alias by remember { mutableStateOf(initial?.alias ?: "") }
    var targetPath by remember { mutableStateOf(initial?.targetPath ?: "") }
    var autoAddCron by remember { mutableStateOf(initial?.autoAddCron ?: true) }
    var autoDelCron by remember { mutableStateOf(initial?.autoDelCron ?: true) }
    var type by remember { mutableStateOf(initial?.type ?: "public-repo") }

    // 白虎面板高级配置字段
    var showAdvanced by remember { mutableStateOf(
        initial?.proxy?.isNotEmpty() == true ||
        initial?.proxyUrl?.isNotEmpty() == true ||
        initial?.authToken?.isNotEmpty() == true ||
        initial?.sparsePath?.isNotEmpty() == true ||
        initial?.singleFile == true ||
        initial?.repoDirName?.isNotEmpty() == true ||
        initial?.commentToTask == true ||
        initial?.dependences?.isNotEmpty() == true
    ) }
    var proxy by remember { mutableStateOf(initial?.proxy ?: "none") }
    var proxyUrl by remember { mutableStateOf(initial?.proxyUrl ?: "") }
    var authToken by remember { mutableStateOf(initial?.authToken ?: "") }
    var sparsePath by remember { mutableStateOf(initial?.sparsePath ?: "") }
    var singleFile by remember { mutableStateOf(initial?.singleFile ?: false) }
    var repoDirName by remember { mutableStateOf(initial?.repoDirName ?: "") }
    var commentToTask by remember { mutableStateOf(initial?.commentToTask ?: false) }
    var dependences by remember { mutableStateOf(initial?.dependences ?: "") }

    val cronDesc = remember(schedule) {
        CronExpressionDescriber.describe(schedule)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "同步/新建 Git 仓库" else "编辑仓库同步配置", fontSize = 16.sp) },
        text = {
            ScrollableColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 类型选择
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("public-repo" to "公开仓库", "private-repo" to "私有仓库", "file" to "单文件")
                        .forEach { (val_, label) ->
                            FilterChip(
                                selected = type == val_,
                                onClick = { type = val_ },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = {
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
                    label = { Text("同步名称") },
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
                        label = { Text("同步周期 (Cron)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (cronDesc.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(cronDesc, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("唯一别名 (Alias，选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = whitelist,
                    onValueChange = { whitelist = it },
                    label = { Text("白名单关键词 (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = blacklist,
                    onValueChange = { blacklist = it },
                    label = { Text("黑名单关键词 (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = extensions,
                    onValueChange = { extensions = it },
                    label = { Text("脚本后缀 (选填，逗号分隔)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetPath,
                    onValueChange = { targetPath = it },
                    label = { Text("目标路径 (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoAddCron, onCheckedChange = { autoAddCron = it })
                    Spacer(Modifier.width(4.dp))
                    Text("自动识别脚本注释并添加定时调度任务", fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoDelCron, onCheckedChange = { autoDelCron = it })
                    Spacer(Modifier.width(4.dp))
                    Text("自动删除已失效的同步任务", fontSize = 11.sp)
                }

                // 高级同步与代理配置折叠项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("高级同步与代理配置", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showAdvanced) {
                    // 代理配置
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("none" to "无代理", "http" to "HTTP代理", "socks5" to "Socks5").forEach { (val_, label) ->
                            FilterChip(
                                selected = proxy == val_,
                                onClick = { proxy = val_ },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }

                    if (proxy != "none") {
                        OutlinedTextField(
                            value = proxyUrl,
                            onValueChange = { proxyUrl = it },
                            label = { Text("代理地址 (proxy_url)") },
                            placeholder = { Text("http://127.0.0.1:7890") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = authToken,
                        onValueChange = { authToken = it },
                        label = { Text("鉴权 Token / 密码 (auth_token)") },
                        placeholder = { Text("私有仓库访问令牌 (选填)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = sparsePath,
                        onValueChange = { sparsePath = it },
                        label = { Text("部分检出路径 (sparse_path)") },
                        placeholder = { Text("指定只拉取的子目录 (选填)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = repoDirName,
                        onValueChange = { repoDirName = it },
                        label = { Text("本地存放目录名 (repo_dir_name)") },
                        placeholder = { Text("自定义本地目录名称 (选填)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dependences,
                        onValueChange = { dependences = it },
                        label = { Text("依赖文件/包清单 (dependences)") },
                        placeholder = { Text("例如 requirements.txt 或 package.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = singleFile, onCheckedChange = { singleFile = it })
                        Spacer(Modifier.width(4.dp))
                        Text("单文件下载模式 (single_file)", fontSize = 11.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = commentToTask, onCheckedChange = { commentToTask = it })
                        Spacer(Modifier.width(4.dp))
                        Text("识别注释自动转换为任务 (commenttotask)", fontSize = 11.sp)
                    }
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
                            type = type,
                            url = url.trim(),
                            branch = branch.trim().ifEmpty { "main" },
                            schedule = schedule.trim().ifEmpty { "0 0 * * *" },
                            whitelist = whitelist.trim(),
                            blacklist = blacklist.trim(),
                            dependences = dependences.trim(),
                            extensions = extensions.trim(),
                            alias = alias.trim(),
                            targetPath = if (targetPath.isBlank()) null else targetPath.trim(),
                            autoAddCron = autoAddCron,
                            autoDelCron = autoDelCron,
                            proxy = if (proxy == "none" && proxyUrl.isBlank()) null else proxy,
                            proxyUrl = if (proxyUrl.isBlank()) null else proxyUrl.trim(),
                            authToken = if (authToken.isBlank()) null else authToken.trim(),
                            sparsePath = if (sparsePath.isBlank()) null else sparsePath.trim(),
                            singleFile = singleFile,
                            repoDirName = if (repoDirName.isBlank()) null else repoDirName.trim(),
                            commentToTask = commentToTask
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

@Composable
private fun ScrollableColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .wrapContentHeight(),
        verticalArrangement = verticalArrangement,
        content = content
    )
}
