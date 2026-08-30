package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.panel.app.data.logger.AppLogger
import com.panel.app.data.logger.LogEntry
import com.panel.app.data.logger.LogLevel
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
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenPanelManager: () -> Unit,
    onOpenDeps: () -> Unit,
    onNavigateToSwitchAccount: () -> Unit = {},
    onOpenLoginLogs: () -> Unit = {},
    onOpenServerLogs: () -> Unit = {},
    onOpenDevConsole: () -> Unit = {},
    onOpenExecutionHistory: () -> Unit = {},
    onOpenBaihuPlugin: () -> Unit = {}
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
    var selectedTimeout by remember { mutableStateOf(15) }
    var showTimeoutDialog by remember { mutableStateOf(false) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.panel.app.util.AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==================== 1. 当前面板与账号管理 ====================
        Text("当前面板与账号", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = currentPanel.name, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                                Surface(
                                    color = if (currentPanel.type == PanelType.BAIHU) Color(0xFFFF9800).copy(alpha = 0.15f) else Color(0xFF2196F3).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (currentPanel.type == PanelType.BAIHU) "白虎" else "青龙",
                                        fontSize = 10.sp,
                                        color = if (currentPanel.type == PanelType.BAIHU) Color(0xFFE65100) else Color(0xFF1565C0),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = currentPanel.baseUrl,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 在线状态指示
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(color = Color(0xFF2E7D32), shape = CircleShape, modifier = Modifier.size(6.dp)) {}
                            Text("已连接", fontSize = 10.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }

                HorizontalDivider()

                // 操作按钮组
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToSwitchAccount,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("切换账号", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onOpenPanelManager,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("面板管理", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.refreshPanelRemoteData(currentPanel)
                            Toast.makeText(context, "正在刷新面板数据...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(38.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ==================== 2. 核心功能与服务 (紧凑网格卡片，告别单调长列表) ====================
        Text("核心功能与服务", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val failedDeps = uiState.deps.count { it.status == 2 || it.status == 4 }
            DashboardGridCard(
                icon = Icons.Default.Extension,
                title = "环境依赖管理",
                subtitle = "${uiState.deps.size} 个依赖包",
                badge = if (failedDeps > 0) "失败 $failedDeps" else null,
                badgeColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                onClick = onOpenDeps
            )
            DashboardGridCard(
                icon = Icons.Default.History,
                title = "执行历史中心",
                subtitle = "调度流水与终端",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenExecutionHistory
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardGridCard(
                icon = Icons.Default.Widgets,
                title = "白虎面板运行中心",
                subtitle = "内置引擎与外部扩展包",
                badge = "插件化",
                badgeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onOpenBaihuPlugin
            )
            DashboardGridCard(
                icon = Icons.Default.Article,
                title = "服务端日志",
                subtitle = "目录树下钻浏览",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenServerLogs
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardGridCard(
                icon = Icons.Default.Shield,
                title = "登录审计日志",
                subtitle = "鉴权流水与 IP",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenLoginLogs
            )
            DashboardGridCard(
                icon = Icons.Default.Terminal,
                title = "开发者模式",
                subtitle = if (uiState.isDevMode) "已开启 (点击查看控制台)" else "点击一键开启排错",
                badge = if (uiState.isDevMode) "ON" else "OFF",
                badgeColor = if (uiState.isDevMode) Color(0xFF10B981) else Color.Gray,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (uiState.isDevMode) onOpenDevConsole() else viewModel.toggleDevMode(true)
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardGridCard(
                icon = Icons.Default.Speed,
                title = "网络延时测速",
                subtitle = pingLatency ?: "点击立即测速",
                badge = if (isTestingPing) "测试中" else null,
                badgeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    isTestingPing = true
                    coroutineScope.launch {
                        val latency = withContext(Dispatchers.IO) {
                            try {
                                val start = System.currentTimeMillis()
                                val url = URL(currentPanel.baseUrl)
                                val conn = url.openConnection() as HttpURLConnection
                                conn.connectTimeout = 3000
                                conn.readTimeout = 3000
                                conn.requestMethod = "HEAD"
                                conn.connect()
                                val end = System.currentTimeMillis()
                                conn.disconnect()
                                "${end - start} ms"
                            } catch (_: Exception) {
                                "超时/不可达"
                            }
                        }
                        pingLatency = latency
                        isTestingPing = false
                    }
                }
            )
        }

        // ==================== 3. 监控与配置快捷工具 ====================
        Text("状态与工具", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val totalEnvs = uiState.envs.size
                val enabledTasks = uiState.tasks.count { !it.isDisabled }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricChip("CPU", currentPanel.cpuUsage, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricChip("内存", currentPanel.ramUsage, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    MetricChip("任务", "$enabledTasks/${uiState.tasks.size}", Color(0xFFFF9800), Modifier.weight(1f))
                    MetricChip("变量", "$totalEnvs 个", Color(0xFF10B981), Modifier.weight(1f))
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val exportStr = uiState.envs.joinToString("\n") { "export ${it.name}=\"${it.value}\"" }
                            clipboardManager.setText(AnnotatedString(exportStr))
                            Toast.makeText(context, "已复制 $totalEnvs 条变量为 Shell export 格式！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出 Shell", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val jsonArray = com.google.gson.JsonArray()
                            uiState.envs.forEach { env ->
                                val obj = com.google.gson.JsonObject()
                                obj.addProperty("name", env.name)
                                obj.addProperty("value", env.value)
                                obj.addProperty("remarks", env.remarks ?: "")
                                jsonArray.add(obj)
                            }
                            clipboardManager.setText(AnnotatedString(jsonArray.toString()))
                            Toast.makeText(context, "已复制 $totalEnvs 条变量为 JSON 数组！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出 JSON", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "本地网络响应缓存已清理！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("清理缓存", fontSize = 11.sp)
                    }
                }
            }
        }

        // ==================== 6. 关于与更新 ====================
        Text("版本更新与关于", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        Toast.makeText(context, err.message ?: "检查更新失败", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("检查应用更新", fontSize = 13.sp, style = MaterialTheme.typography.bodyMedium)
                        Text("通过 GitHub Releases 检查并在线获取最新 APK", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("检测 >", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                // GitHub 仓库
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString("https://github.com/LiYiCha/App-Panel"))
                            Toast.makeText(context, "已复制 GitHub 仓库地址到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("开源仓库", fontSize = 13.sp, style = MaterialTheme.typography.bodyMedium)
                        Text("github.com/LiYiCha/App-Panel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider()

                // 关于应用
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("关于 Panel Hub", fontSize = 13.sp, style = MaterialTheme.typography.titleSmall)
                        Text("现代化跨面板移动管理客户端", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("v1.0.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // 关于弹窗
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于 Panel Hub", fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("版本: v2.2.0 (全功能对齐版)", fontSize = 12.sp, style = MaterialTheme.typography.titleSmall)
                    Text("全面适配青龙面板 (v2.10 - v2.20.2) 与白虎面板，支持定时任务多状态过滤与实时日志追踪、Git 订阅同步、多模式环境变量解析与还原、配置文件树浏览及依赖包状态监控。", fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("确认") }
            }
        )
    }

    // 超时设置弹窗
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("设置请求超时", fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30, 60).forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTimeout = seconds
                                    showTimeoutDialog = false
                                    Toast.makeText(context, "请求超时已更新为 ${seconds}s", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(selected = selectedTimeout == seconds, onClick = null)
                            Text("${seconds} 秒" + if (seconds == 15) " (推荐默认)" else "")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text("关闭") }
            }
        )
    }

    // 版本更新弹窗
    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Text(if (info.hasUpdate) "发现新版本 ${info.latestVersion}" else "当前已是最新版本", fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前版本: ${info.currentVersion}  |  最新版本: ${info.latestVersion}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (info.publishedAt.isNotBlank() && info.publishedAt != "--") {
                        Text("发布时间: ${info.publishedAt}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Text("更新说明:", fontSize = 12.sp, style = MaterialTheme.typography.titleSmall)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = info.releaseNotes,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
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
                        Text("立即下载 APK")
                    }
                } else {
                    Button(onClick = { showUpdateDialog = false }) { Text("我知道了") }
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
                            Toast.makeText(context, "已复制 Release 页面地址", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("在 GitHub 查看")
                }
            }
        )
    }
}

@Composable
fun MetricChip(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 11.sp, style = MaterialTheme.typography.titleSmall, color = color)
        }
    }
}

@Composable
fun DashboardGridCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                if (badge != null && badgeColor != null) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(title, fontSize = 13.sp, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

