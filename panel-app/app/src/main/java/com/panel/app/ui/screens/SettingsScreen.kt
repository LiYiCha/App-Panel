package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.PanelInstance
import com.panel.app.data.model.PanelType
import com.panel.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 现代化紧凑型控制台/设置主页。
 * 遵循“一页即全览，深层下沉二级页”设计准则，核心面板状态与高频二级入口在一屏内完全呈现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenPanelManager: () -> Unit,
    onOpenDeps: () -> Unit,
    onNavigateToSwitchAccount: () -> Unit = {},
    onOpenLoginLogs: () -> Unit = {},
    onOpenServerLogs: () -> Unit = {},
    onOpenDashboard: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenDevConsole: () -> Unit = {},
    onOpenExecutionHistory: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val currentPanel = uiState.panels.getOrNull(uiState.selectedPanelIndex)
        ?: PanelInstance("baihu-default", "白虎面板", PanelType.BAIHU, "http://127.0.0.1:5700", isLocalServer = true)

    var showAboutDialog by remember { mutableStateOf(false) }
    var pingLatency by remember { mutableStateOf<String?>(null) }
    var isTestingPing by remember { mutableStateOf(false) }
    var isReachable by remember { mutableStateOf<Boolean?>(null) }
    var remoteDashboard by remember { mutableStateOf<com.panel.app.data.model.PanelDashboard?>(null) }
    var selectedTimeout by remember { mutableStateOf(15) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showSystemSettingsPage by remember { mutableStateOf(false) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.panel.app.util.AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // 实时探测远端连通性与拉取远端真实运行指标
    LaunchedEffect(currentPanel.id, currentPanel.baseUrl) {
        isTestingPing = true
        val latency = withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                val url = java.net.URL(currentPanel.baseUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                conn.connect()
                val end = System.currentTimeMillis()
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..499) "${end - start} ms" else "异常 ($code)"
            } catch (_: Exception) {
                "超时/离线"
            }
        }
        pingLatency = latency
        isReachable = latency.endsWith("ms")
        isTestingPing = false

        viewModel.loadDashboard { res ->
            remoteDashboard = res.getOrNull()
        }
    }

    if (showSystemSettingsPage) {
        androidx.activity.compose.BackHandler { showSystemSettingsPage = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (currentPanel.type == PanelType.BAIHU) "白虎系统状态与配置" else "青龙系统高级配置",
                                fontSize = 15.sp,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${currentPanel.name} (${currentPanel.baseUrl})",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showSystemSettingsPage = false }) {
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
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentPanel.type == PanelType.QINGLONG_V15 || currentPanel.type == PanelType.QINGLONG_V10) {
                    QinglongSystemSettingsCard(viewModel = viewModel)
                } else {
                    BaihuSystemSettingsCard(dashboard = remoteDashboard)
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==================== 1. 当前面板信息卡片 (精炼紧凑 Header) ====================
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = currentPanel.name,
                                    fontSize = 14.sp,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = if (currentPanel.type == PanelType.BAIHU) Color(0xFFFF9800).copy(alpha = 0.15f) else Color(0xFF2196F3).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (currentPanel.type == PanelType.BAIHU) "白虎" else "青龙",
                                        fontSize = 9.sp,
                                        color = if (currentPanel.type == PanelType.BAIHU) Color(0xFFE65100) else Color(0xFF1565C0),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = currentPanel.baseUrl,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 在线连通状态
                    val isOnline = isReachable == true
                    Surface(
                        color = if (isReachable == null) MaterialTheme.colorScheme.surfaceVariant
                        else if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = if (isReachable == null) MaterialTheme.colorScheme.outline
                                else if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                                shape = CircleShape,
                                modifier = Modifier.size(5.dp)
                            ) {}
                            Text(
                                text = if (isReachable == null) "探测中"
                                else if (isOnline) "在线 (${pingLatency ?: "正常"})" else "离线",
                                fontSize = 9.sp,
                                color = if (isReachable == null) MaterialTheme.colorScheme.onSurfaceVariant
                                else if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }

                // 核心指标一览
                val totalEnvs = uiState.envs.size
                val enabledTasks = uiState.tasks.count { !it.isDisabled }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactMetricChip("CPU", remoteDashboard?.cpuUsage ?: currentPanel.cpuUsage, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    CompactMetricChip("内存", remoteDashboard?.memUsage ?: currentPanel.ramUsage, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    CompactMetricChip("任务", "$enabledTasks/${uiState.tasks.size}", Color(0xFF2E7D32), Modifier.weight(1f))
                    CompactMetricChip("变量", "$totalEnvs 个", Color(0xFF0288D1), Modifier.weight(1f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 操作按钮条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onNavigateToSwitchAccount,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("切换账号", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onOpenPanelManager,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.SettingsSuggest, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("面板管理", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.refreshPanelRemoteData(currentPanel)
                            Toast.makeText(context, "正在刷新面板数据...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(16.dp))
                    }

                    var showLogoutConfirm by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showLogoutConfirm = true },
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出", modifier = Modifier.size(16.dp))
                    }

                    if (showLogoutConfirm) {
                        AlertDialog(
                            onDismissRequest = { showLogoutConfirm = false },
                            title = { Text("退出登录", fontSize = 15.sp) },
                            text = {
                                Text("将清除 [${currentPanel.name}] 当前会话，下次需重新登录。", fontSize = 12.sp)
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showLogoutConfirm = false
                                        viewModel.logoutCurrentPanel { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("确认退出", fontSize = 12.sp)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消", fontSize = 12.sp) }
                            }
                        )
                    }
                }
            }
        }

        // ==================== 2. 功能中心 (8个二级页面/功能紧凑卡片，两列布局) ====================
        Text("功能与二级服务", fontSize = 11.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        val failedDeps = uiState.deps.count { it.status == 2 || it.status == 4 }

        // Row 1
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernNavTile(
                icon = Icons.Default.QueryStats,
                title = "面板仪表盘",
                desc = "趋势 · 排行 · 资源",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenDashboard
            )
            ModernNavTile(
                icon = Icons.Default.History,
                title = "执行历史中心",
                desc = "流水记录与终端",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenExecutionHistory
            )
        }

        // Row 2
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernNavTile(
                icon = Icons.Default.Extension,
                title = "环境依赖管理",
                desc = "${uiState.deps.size} 个依赖包",
                badge = if (failedDeps > 0) "失败 $failedDeps" else null,
                badgeColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                onClick = onOpenDeps
            )
            ModernNavTile(
                icon = Icons.Default.Article,
                title = "服务端日志",
                desc = "脚本文件日志",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenServerLogs
            )
        }

        // Row 3
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernNavTile(
                icon = Icons.Default.CloudUpload,
                title = "备份与恢复",
                desc = "任务 / 变量快照",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenBackup
            )
            ModernNavTile(
                icon = Icons.Default.Shield,
                title = "登录审计日志",
                desc = "鉴权历史与 IP",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenLoginLogs
            )
        }

        // Row 4
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernNavTile(
                icon = Icons.Default.Tune,
                title = "系统高级配置",
                desc = if (currentPanel.type == PanelType.BAIHU) "宿主机硬件与调度" else "并发上限与日志清理",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = { showSystemSettingsPage = true }
            )
            ModernNavTile(
                icon = Icons.Default.Terminal,
                title = "开发者控制台",
                desc = if (uiState.isDevMode) "已开启调试捕获" else "点击开启请求抓包",
                badge = if (uiState.isDevMode) "ON" else "OFF",
                badgeColor = if (uiState.isDevMode) Color(0xFF10B981) else Color.Gray,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (uiState.isDevMode) onOpenDevConsole() else viewModel.toggleDevMode(true)
                }
            )
        }

        // ==================== 3. 底部轻量通用设置卡片 ====================
        Text("常规设置", fontSize = 11.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                // 超时
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimeoutDialog = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("网络超时设置", fontSize = 12.sp)
                    Text("${selectedTimeout}s >", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 检查更新
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isCheckingUpdate) {
                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    val res = com.panel.app.util.AppUpdateManager.checkForUpdate()
                                    isCheckingUpdate = false
                                    res.onSuccess { info ->
                                        updateInfo = info
                                        showUpdateDialog = true
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "检查更新失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("检查应用更新", fontSize = 12.sp)
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("v1.0.0 (检测) >", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 关于
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("关于 Panel Hub", fontSize = 12.sp)
                    Text("详情 >", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }



    // 关于弹窗
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于 Panel Hub", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("版本: v2.3.0 (高聚合无感流转版)", fontSize = 12.sp, style = MaterialTheme.typography.titleSmall)
                    Text("全面适配青龙面板 (v2.10 - v2.20.2) 与白虎面板。支持定时任务多状态联动、代码语法高亮与行号、Git 订阅同步、多模式环境变量解析与还原、配置文件树浏览及依赖包状态监控。", fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("确认", fontSize = 12.sp) }
            }
        )
    }

    // 超时设置弹窗
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("设置请求超时", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 30, 60).forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTimeout = seconds
                                    showTimeoutDialog = false
                                    Toast.makeText(context, "超时已设置为 ${seconds}s", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = selectedTimeout == seconds, onClick = null)
                            Text("${seconds} 秒" + if (seconds == 15) " (推荐默认)" else "", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text("关闭", fontSize = 12.sp) }
            }
        )
    }

    // 版本更新弹窗
    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Text(if (info.hasUpdate) "发现新版本 ${info.latestVersion}" else "当前已是最新版本", fontSize = 15.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前: ${info.currentVersion}  |  最新: ${info.latestVersion}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (info.publishedAt.isNotBlank() && info.publishedAt != "--") {
                        Text("发布: ${info.publishedAt}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Text("更新说明:", fontSize = 11.sp, style = MaterialTheme.typography.titleSmall)
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = info.releaseNotes,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (info.hasUpdate && !info.downloadUrl.isNullOrEmpty()) {
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.downloadUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                clipboardManager.setText(AnnotatedString(info.downloadUrl))
                                Toast.makeText(context, "已复制下载链接到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                            showUpdateDialog = false
                        }
                    ) {
                        Text("立即下载 APK", fontSize = 12.sp)
                    }
                } else {
                    Button(onClick = { showUpdateDialog = false }) { Text("我知道了", fontSize = 12.sp) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.releasePageUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            clipboardManager.setText(AnnotatedString(info.releasePageUrl))
                            Toast.makeText(context, "已复制 Release 地址", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("在 GitHub 查看", fontSize = 12.sp)
                }
            }
        )
    }
}

/**
 * 现代化紧凑导航卡片 (高频二级入口)
 */
@Composable
private fun ModernNavTile(
    icon: ImageVector,
    title: String,
    desc: String,
    badge: String?,
    badgeColor: Color?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badge != null) {
                        Surface(
                            color = (badgeColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 8.sp,
                                color = badgeColor ?: MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactMetricChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 8.sp, color = color, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * 青龙面板系统设置二级内容。
 */
@Composable
private fun QinglongSystemSettingsCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf<Map<String, String>?>(null) }
    var isLoaded by remember { mutableStateOf(false) }
    var logDays by remember { mutableStateOf("") }
    var concurrency by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadQinglongSystemSettings { map ->
            isLoaded = true
            settings = map
            if (map != null) {
                logDays = map["logRemoveFrequency"]
                    ?: map["info.logRemoveFrequency"]
                    ?: map["config.logRemoveFrequency"]
                    ?: ""
                concurrency = map["cronConcurrency"]
                    ?: map["info.cronConcurrency"]
                    ?: map["config.cronConcurrency"]
                    ?: ""
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isLoaded) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (settings == null) {
            Text("当前青龙面板未提供系统配置接口或当前账号无配置权限", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("调度与清理设置", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = logDays,
                            onValueChange = { logDays = it.filter { c -> c.isDigit() } },
                            label = { Text("日志保留天数", fontSize = 11.sp) },
                            placeholder = { Text("例如: 7", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val v = logDays.toIntOrNull()
                                viewModel.saveLogRemoveFrequency(v)
                                Toast.makeText(context, if (v == null) "已清除日志自动清理限制" else "日志保留天数已设置为 ${v} 天", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("保存", fontSize = 12.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = concurrency,
                            onValueChange = { concurrency = it.filter { c -> c.isDigit() } },
                            label = { Text("任务最大并发数", fontSize = 11.sp) },
                            placeholder = { Text("例如: 5", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val v = concurrency.toIntOrNull()
                                viewModel.saveCronConcurrency(v)
                                Toast.makeText(context, if (v == null) "已解除任务最大并发限制" else "任务并发数已设置为 $v", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("保存", fontSize = 12.sp)
                        }
                    }

                    Text("提示：留空并点击保存表示不限制并发或不自动清理旧日志", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("系统维护与通知测试", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.sendTestNotify("系统测试通知", "来自 Panel Hub 移动端的通知连通性测试")
                                Toast.makeText(context, "已向青龙服务提交通知测试请求", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("测试系统通知", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.reloadQinglongSystem()
                                Toast.makeText(context, "已向青龙服务提交系统配置重载请求", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重载配置", fontSize = 11.sp)
                        }
                    }

                    val timezone = settings?.get("info.timezone")
                    if (!timezone.isNullOrBlank()) {
                        Text(
                            "服务时区：$timezone",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 白虎面板系统概览二级内容。
 */
@Composable
private fun BaihuSystemSettingsCard(dashboard: com.panel.app.data.model.PanelDashboard?) {
    Column(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dashboard == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            val platform = dashboard.resourceDetail["系统"] ?: dashboard.resourceDetail["platform"] ?: "Linux / Docker"
            val uptime = dashboard.resourceDetail["运行时长"] ?: dashboard.resourceDetail["uptime"] ?: "正常运行"
            val disk = dashboard.resourceDetail["磁盘占用"] ?: "--"
            val workers = dashboard.resourceDetail["Worker 数"] ?: "--"

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("白虎宿主机监控", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("系统平台", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(platform, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("运行时长", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(uptime, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CPU 使用率", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dashboard.cpuUsage ?: "--", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("物理内存使用", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dashboard.memUsage ?: "--", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("磁盘占用", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(disk, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("调度 Worker 数", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(workers, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
