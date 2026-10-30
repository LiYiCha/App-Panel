package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.BuildConfig
import com.panel.app.ui.viewmodel.MainViewModel
import com.panel.app.util.AppUpdateInfo
import com.panel.app.util.AppUpdateManager
import kotlinx.coroutines.launch

/**
 * 「其他」设置二级页：应用级杂项收敛地 —— 网络超时 / 清除缓存 /
 * 应用权限（含通知授权） / 下载加速镜像（紧凑选择行） / 调试日志 / 检查更新 / 关于。
 * 从设置页「应用设置 → 其他」进入，替代原先在设置主页平铺的长列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onOpenDevLog: () -> Unit = {},
    onReplayGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    BackHandler { onBack() }

    var selectedTimeout by remember { mutableIntStateOf(15) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // 缓存明细（分类识别，支持单独清理）
    var cacheEntries by remember { mutableStateOf<List<AppCacheEntry>>(emptyList()) }
    var showCacheDialog by remember { mutableStateOf(false) }
    fun refreshCacheEntries() {
        cacheEntries = collectAppCacheEntries(context)
    }
    LaunchedEffect(Unit) { refreshCacheEntries() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column {
                        Text("其他设置", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("通知 · 缓存 · 更新 · 加速", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ==================== 基础设置（分组行样式） ====================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    SettingsRow(
                        title = "网络超时",
                        subtitle = "面板超时",
                        value = "${selectedTimeout}s >",
                        onClick = { showTimeoutDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingsRow(
                        title = "清除缓存",
                        subtitle = "安装包 · 快照 · 临时文件",
                        value = "${formatBytes(cacheEntries.sumOf { it.sizeBytes })} 分类清理 >",
                        valueTint = MaterialTheme.colorScheme.error,
                        onClick = { showCacheDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingsRow(
                        title = "权限管理",
                        subtitle = "通知 · 安装 · 存储",
                        value = "管理 >",
                        onClick = onOpenPermissions
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingsRow(
                        title = "新手引导",
                        subtitle = "重看功能介绍",
                        value = "查看 >",
                        onClick = onReplayGuide
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 下载加速镜像：紧凑单行入口，点击弹出源选择
                    // （Python 依赖源已移至 本地运行-依赖 Tab 就近管理）
                    MirrorPickerRow(
                        label = "应用更新源",
                        subtitle = "更新 APK 下载源",
                        type = com.panel.app.util.MirrorManager.Type.UPDATE,
                        divider = {}
                    )
                }
            }

            // ==================== 调试 / 更新 / 关于 ====================
            Text("调试与更新", fontSize = 11.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    SettingsRow(
                        title = "调试日志",
                        subtitle = if (uiState.isDevMode) "点击查看日志" else "开启后可查看",
                        value = if (uiState.isDevMode) "已开启 >" else "点击开启 >",
                        onClick = {
                            if (uiState.isDevMode) onOpenDevLog() else viewModel.toggleDevMode(true)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingsRow(
                        title = "检查更新",
                        subtitle = "当前 v${BuildConfig.VERSION_NAME}",
                        value = if (isCheckingUpdate) null else "检测 >",
                        onClick = {
                            if (isCheckingUpdate) return@SettingsRow
                            coroutineScope.launch {
                                isCheckingUpdate = true
                                val res = AppUpdateManager.checkForUpdate()
                                isCheckingUpdate = false
                                res.onSuccess { info ->
                                    updateInfo = info
                                    showUpdateDialog = true
                                }.onFailure { err ->
                                    Toast.makeText(context, err.message ?: "检查更新失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        trailing = {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingsRow(
                        title = "关于烛龙",
                        subtitle = "版本与功能说明",
                        value = "详情 >",
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    // 超时设置弹窗
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("超时设置", fontSize = 15.sp) },
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
                            Text("$seconds 秒" + if (seconds == 15) " (推荐默认)" else "", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text("关闭", fontSize = 12.sp) }
            }
        )
    }

    // 缓存分类明细弹窗
    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("清除缓存", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    cacheEntries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, fontSize = 12.sp)
                                Text(
                                    "${entry.desc} · ${formatBytes(entry.sizeBytes)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                entry.clear()
                                refreshCacheEntries()
                                Toast.makeText(context, "已清理${entry.title}", Toast.LENGTH_SHORT).show()
                            }) { Text("清理", fontSize = 11.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    cacheEntries.forEach { it.clear() }
                    refreshCacheEntries()
                    Toast.makeText(context, "缓存已全部清除", Toast.LENGTH_SHORT).show()
                }) { Text("全部清理", fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showCacheDialog = false }) { Text("关闭", fontSize = 12.sp) }
            }
        )
    }

    // 关于弹窗
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于烛龙", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("版本: v${BuildConfig.VERSION_NAME} (正式版)", fontSize = 12.sp, style = MaterialTheme.typography.titleSmall)
                    Text("全面适配青龙面板 (v2.10 - v2.20.2) 与白虎面板。支持定时任务多状态联动、代码语法高亮与行号、Git 订阅同步、多模式环境变量解析与还原、配置文件树浏览及依赖包状态监控。", fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("确认", fontSize = 12.sp) }
            }
        )
    }

    // 版本更新弹窗（自治组件，见 SettingsDialogs.kt）
    if (showUpdateDialog && updateInfo != null) {
        // 关闭弹窗时刷新缓存明细：下载完成/取消会增删缓存目录中的 APK 文件
        UpdateCheckDialog(info = updateInfo!!, onDismiss = {
            showUpdateDialog = false
            refreshCacheEntries()
        })
    }
}

/** 设置分组行：标题 + 副标题居左，当前值/动作居右 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    value: String?,
    valueTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column {
                Text(title, fontSize = 12.sp)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        value?.let {
            Text(it, fontSize = 11.sp, color = valueTint)
        }
        trailing?.invoke()
    }
}
