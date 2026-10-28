package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.PanelInstance
import com.panel.app.data.model.PanelType
import com.panel.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 现代化紧凑型控制台/设置主页。
 * 遵循"一页即全览，深层下沉二级页"设计准则，核心面板状态与高频二级入口在一屏内完全呈现。
 * 应用侧杂项（通知/缓存/更新/加速等）已收敛至「应用设置 → 其他」二级页（OtherSettingsScreen）。
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
    onOpenLogHistory: () -> Unit = {},
    onOpenSystemSettings: () -> Unit = {},
    onOpenSubscriptionsScreen: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenLocalScripts: () -> Unit = {},
    onOpenOtherSettings: () -> Unit = {},
    onOpenDevConsole: () -> Unit = {},
    localScriptEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentPanel = uiState.panels.getOrNull(uiState.selectedPanelIndex)
        ?: PanelInstance("baihu-default", "白虎面板", PanelType.BAIHU, "http://127.0.0.1:5700", isLocalServer = true)

    var pingLatency by remember { mutableStateOf<String?>(null) }
    var isReachable by remember { mutableStateOf<Boolean?>(null) }
    var remoteDashboard by remember { mutableStateOf<com.panel.app.data.model.PanelDashboard?>(null) }

    // 实时探测远端连通性与拉取远端真实运行指标
    LaunchedEffect(currentPanel.id, currentPanel.baseUrl) {
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

        viewModel.loadDashboard { res ->
            remoteDashboard = res.getOrNull()
        }
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
                        else if (isOnline) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = if (isReachable == null) MaterialTheme.colorScheme.outline
                                else if (isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                shape = CircleShape,
                                modifier = Modifier.size(5.dp)
                            ) {}
                            Text(
                                text = if (isReachable == null) "探测中"
                                else if (isOnline) "在线 (${pingLatency ?: "正常"})" else "离线",
                                fontSize = 9.sp,
                                color = if (isReachable == null) MaterialTheme.colorScheme.onSurfaceVariant
                                else if (isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
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
                    CompactMetricChip("任务", "$enabledTasks/${uiState.tasks.size}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    CompactMetricChip("变量", "$totalEnvs 个", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
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

                    IconButton(
                        onClick = {
                            viewModel.refreshPanelRemoteData(currentPanel)
                            Toast.makeText(context, "正在刷新面板数据...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    Text("刷新", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    var showLogoutConfirm by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showLogoutConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                    Text("退出", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)

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

        // ==================== 2. 面板功能（面板侧二级页面，两列布局；应用侧条目归入下方「应用设置」） ====================
        Text("面板功能", fontSize = 11.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

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
                title = "历史日志",
                desc = "任务执行流水记录",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenLogHistory
            )
        }

        // Row 2
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernNavTile(
                icon = Icons.Default.Extension,
                title = "环境依赖管理",
                desc = "${uiState.deps.size} 个依赖包",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenDeps
            )
            ModernNavTile(
                icon = Icons.Default.SyncAlt,
                title = "仓库订阅",
                desc = "添加/同步定时仓库源",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenSubscriptionsScreen
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
                title = "登录日志",
                desc = "登录历史与 IP",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenLoginLogs
            )
        }

        // Row 4: 系统配置（调试日志是应用侧功能，已归入下方「应用设置」组）
        ModernNavTile(
            icon = Icons.Default.Tune,
            title = "系统配置",
            desc = if (currentPanel.type == PanelType.BAIHU) "宿主机硬件与调度" else "并发上限与日志清理",
            badge = null,
            badgeColor = null,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSystemSettings
        )

        // 应用市场（白虎专属：open2api /apps，一键部署/更新脚本应用）
        if (currentPanel.type == PanelType.BAIHU) {
            ModernNavTile(
                icon = Icons.Default.Storefront,
                title = "应用市场",
                desc = "一键部署与更新脚本应用",
                badge = null,
                badgeColor = null,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenApps
            )
        }

        // ==================== 3. 应用设置（full：本地模式 + 其他；standard：仅其他） ====================
        Text("应用设置", fontSize = 11.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (localScriptEnabled) {
                ModernNavTile(
                    icon = Icons.Default.Code,
                    title = "本地模式",
                    desc = "脚本 · 依赖 · 记录",
                    badge = null,
                    badgeColor = null,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLocalScripts
                )
            }
            ModernNavTile(
                icon = Icons.Default.Settings,
                title = "其他",
                desc = "通知 · 缓存 · 加速 · 更新",
                badge = null,
                badgeColor = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenOtherSettings
            )
        }
        // 调试模式开启时：设置主页直达日志入口（快速查看，无需进二级页）
        if (uiState.isDevMode) {
            ModernNavTile(
                icon = Icons.Default.BugReport,
                title = "调试日志",
                desc = "记录中 · 点击快速查看",
                badge = "ON",
                badgeColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenDevConsole
            )
        }
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
fun QinglongSystemSettingsCard(viewModel: MainViewModel) {
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
                                Toast.makeText(context, if (v == null) "已清除日志自动清理限制" else "日志保留天数已设置为 $v 天", Toast.LENGTH_SHORT).show()
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
fun BaihuSystemSettingsCard(dashboard: com.panel.app.data.model.PanelDashboard?) {
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
