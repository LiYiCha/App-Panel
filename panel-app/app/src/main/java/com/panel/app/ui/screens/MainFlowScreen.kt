package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.PanelType
import com.panel.app.data.model.UnifiedTask
import com.panel.app.script.LocalScriptFeature
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.components.TwoFactorVerifyDialog
import com.panel.app.ui.viewmodel.MainViewModel

enum class BottomNavScreen(val title: String, val icon: ImageVector) {
    Tasks("任务列表", Icons.Default.TaskAlt),
    Envs("环境变量", Icons.Default.DataObject),
    Scripts("配置与脚本", Icons.Default.Folder),
    LOCAL("本地", Icons.Default.PhoneAndroid),
    Settings("系统设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFlowScreen(
    viewModel: MainViewModel,
    onNavigateToLogin: () -> Unit = {},
    onOpenPanelManager: () -> Unit,
    onOpenTaskDetail: (String) -> Unit,
    onOpenLogScreen: (String) -> Unit,
    onOpenScriptEditorScreen: (String) -> Unit,
    onOpenDepsScreen: () -> Unit,
    onOpenLoginLogsScreen: () -> Unit = {},
    onOpenServerLogsScreen: () -> Unit = {},
    onOpenDashboardScreen: () -> Unit = {},
    onOpenBackupScreen: () -> Unit = {},
    onOpenDevConsoleScreen: () -> Unit = {},
    onOpenLogHistoryScreen: () -> Unit = {},
    onOpenSystemSettingsScreen: () -> Unit = {},
    onOpenPermissionsScreen: () -> Unit = {},
    onOpenSubscriptionsScreen: () -> Unit = {},
    onOpenAppsScreen: () -> Unit = {},
    onOpenLocalScriptsScreen: () -> Unit = {},
    onOpenOtherSettingsScreen: () -> Unit = {},
    onOpenEnvRules: () -> Unit = {},
    localScriptEnabled: Boolean = false,
    localScriptFeature: LocalScriptFeature? = null,
    onOpenLocalEditor: (String) -> Unit = {},
    onOpenLocalConsole: (String) -> Unit = {},
    onNavigateToCreateScript: () -> Unit = {},
    /** 引导页预览专用：透传给任务列表展示的假数据，仅展示不落库 */
    previewTasks: List<UnifiedTask>? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab = uiState.currentTab

    // 配置与脚本子分段 Tab (0: 脚本文件, 1: 配置文件)
    var scriptsSubTab by rememberSaveable { mutableIntStateOf(0) }

    // 任务排序（状态提升到顶栏：顶栏排序图标 + 下拉菜单，与批量/新建同一入口层）
    var taskSortType by remember { mutableStateOf(TaskSortType.DEFAULT) }
    var showTaskSortMenu by remember { mutableStateOf(false) }

    // 顶部右上角统一动作触发状态（不占用页面主体垂直空间）
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var showCreateEnvDialog by remember { mutableStateOf(false) }
    var showImportEnvDialog by remember { mutableStateOf(false) }
    var showCreateScriptDialog by remember { mutableStateOf(false) }
    var showDirectoryDrawer by remember { mutableStateOf(false) }

    val panelList = uiState.panels
    val currentPanel = panelList.getOrNull(uiState.selectedPanelIndex)

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // 面板 token 过期 + 两步验证（青龙 420 / 白虎 require_otp）：
    // 自愈链路挂起 otpPendingPanel 后，在此全局弹窗继续验证码流程，
    // 验证成功 submitOtp 会保存新凭据并自动重新刷新数据
    if (uiState.otpPendingPanel != null) {
        TwoFactorVerifyDialog(
            panelName = uiState.otpPendingPanel!!.name,
            isVerifying = uiState.isAuthenticating,
            onDismiss = { viewModel.cancelOtp() },
            onConfirm = { code, onError ->
                viewModel.submitOtp(code) { success, msg ->
                    if (!success) onError(msg)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {},
                title = {
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = selectedTab.title,
                            fontSize = 17.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    // 统一置于顶部右上角，绝不占用内容主体高度；图标按钮统一带下方精简文言
                    when (selectedTab) {
                        BottomNavScreen.Tasks -> {
                            // 多维排序（从筛选行收进顶栏）
                            TaskSortMenu(
                                current = taskSortType,
                                expanded = showTaskSortMenu,
                                onExpandedChange = { showTaskSortMenu = it },
                                onSelect = { taskSortType = it }
                            )
                            ActionButtonSmall(
                                icon = Icons.Default.Checklist,
                                label = if (uiState.isTaskBatchMode) "完成" else "批量",
                                tint = if (uiState.isTaskBatchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                onClick = { viewModel.setTaskBatchMode(!uiState.isTaskBatchMode) }
                            )
                            ActionButtonSmall(
                                icon = Icons.Default.Add,
                                label = "新建",
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = { showCreateTaskDialog = true }
                            )
                        }
                        BottomNavScreen.Envs -> {
                            ActionButtonSmall(
                                icon = Icons.Default.Tune,
                                label = "规则",
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = onOpenEnvRules
                            )
                            ActionButtonSmall(
                                icon = Icons.Default.Checklist,
                                label = if (uiState.isEnvBatchMode) "完成" else "批量",
                                tint = if (uiState.isEnvBatchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                onClick = { viewModel.setEnvBatchMode(!uiState.isEnvBatchMode) }
                            )
                            ActionButtonSmall(
                                icon = Icons.Default.PostAdd,
                                label = "导入",
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = { showImportEnvDialog = true }
                            )
                            ActionButtonSmall(
                                icon = Icons.Default.Add,
                                label = "新建",
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = { showCreateEnvDialog = true }
                            )
                        }
                        BottomNavScreen.Scripts -> {
                            if (scriptsSubTab == 0) {
                                ActionButtonSmall(
                                    icon = Icons.Default.Add,
                                    label = "新建",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = { showCreateScriptDialog = true }
                                )
                            } else {
                                ActionButtonSmall(
                                    icon = Icons.Default.FolderOpen,
                                    label = "目录",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = { showDirectoryDrawer = true }
                                )
                                ActionButtonSmall(
                                    icon = Icons.Default.Save,
                                    label = "保存",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        viewModel.saveConfigFile(uiState.selectedConfigFile, uiState.configContent)
                                    }
                                )
                            }
                        }
                        BottomNavScreen.LOCAL -> {
                            // 本地模式动作入口内联在各 Tab 内容中，顶栏不额外放按钮
                        }
                        BottomNavScreen.Settings -> {
                            ActionButtonSmall(
                                icon = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                label = "主题",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = { viewModel.toggleTheme() }
                            )
                            ActionButtonSmall(
                                icon = Icons.Default.Dns,
                                label = "面板",
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = onOpenPanelManager
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationDock(
                current = selectedTab,
                entries = if (localScriptEnabled) BottomNavScreen.entries
                    else BottomNavScreen.entries.filter { it != BottomNavScreen.LOCAL },
                onSelect = { viewModel.selectTab(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 10.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                BottomNavScreen.Tasks -> {
                    TasksScreen(
                        viewModel = viewModel,
                        showCreateDialog = showCreateTaskDialog,
                        onDismissCreateDialog = { showCreateTaskDialog = false },
                        onOpenTaskDetail = onOpenTaskDetail,
                        onOpenLog = onOpenLogScreen,
                        sortType = taskSortType,
                        onSortTypeChange = { taskSortType = it },
                        previewTasks = previewTasks
                    )
                }

                BottomNavScreen.Envs -> {
                    EnvsScreen(
                        viewModel = viewModel,
                        showCreateDialog = showCreateEnvDialog,
                        onDismissCreateDialog = { showCreateEnvDialog = false },
                        showImportDialog = showImportEnvDialog,
                        onDismissImportDialog = { showImportEnvDialog = false }
                    )
                }

                BottomNavScreen.Scripts -> {
                    TabRow(selectedTabIndex = scriptsSubTab, modifier = Modifier.fillMaxWidth()) {
                        Tab(
                            selected = scriptsSubTab == 0,
                            onClick = { scriptsSubTab = 0 },
                            text = { Text("脚本文件 (${uiState.scriptTree.size})", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = scriptsSubTab == 1,
                            onClick = { scriptsSubTab = 1 },
                            text = { Text("配置文件", fontSize = 12.sp) }
                        )
                    }

                    if (scriptsSubTab == 0) {
                        ScriptsScreen(
                            viewModel = viewModel,
                            showCreateDialog = showCreateScriptDialog,
                            onDismissCreateDialog = { showCreateScriptDialog = false },
                            onNavigateToCreateScript = onNavigateToCreateScript,
                            onOpenScriptEditor = onOpenScriptEditorScreen
                        )
                    } else {
                        ConfigEditorScreen(
                            viewModel = viewModel,
                            showDirectoryDrawer = showDirectoryDrawer,
                            onDismissDirectoryDrawer = { showDirectoryDrawer = false }
                        )
                    }
                }

                BottomNavScreen.LOCAL -> {
                    // 内嵌渲染本地模式中心页（embedded：无自身页面壳，顶栏与 Dock 由本层提供）
                    localScriptFeature?.CenterContent(
                        initialTab = 0,
                        onOpenEditor = onOpenLocalEditor,
                        onOpenConsole = onOpenLocalConsole
                    )
                }

                BottomNavScreen.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenPanelManager = onOpenPanelManager,
                        onOpenDeps = onOpenDepsScreen,
                        onNavigateToSwitchAccount = onNavigateToLogin,
                        onOpenLoginLogs = onOpenLoginLogsScreen,
                        onOpenServerLogs = onOpenServerLogsScreen,
                        onOpenDashboard = onOpenDashboardScreen,
                        onOpenBackup = onOpenBackupScreen,
                        onOpenLogHistory = onOpenLogHistoryScreen,
                        onOpenSystemSettings = onOpenSystemSettingsScreen,
                        onOpenSubscriptionsScreen = onOpenSubscriptionsScreen,
                        onOpenApps = onOpenAppsScreen,
                        onOpenLocalScripts = onOpenLocalScriptsScreen,
                        onOpenOtherSettings = onOpenOtherSettingsScreen,
                        onOpenDevConsole = onOpenDevConsoleScreen,
                        localScriptEnabled = localScriptEnabled
                    )
                }
            }
        }
    }
}

/**
 * 浮动 Dock 导航：底部悬浮胶囊条（大圆角 + 阴影 + 描边），与内容区视觉分离。
 * 选中项展开为「主色胶囊 + 图标 + 文字」，未选中仅图标；切换时胶囊宽度有过渡动画。
 */
@Composable
private fun NavigationDock(
    current: BottomNavScreen,
    entries: List<BottomNavScreen>,
    onSelect: (BottomNavScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            entries.forEach { screen ->
                val selected = screen == current
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(screen) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    if (selected) {
                        Text(
                            text = screen.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/** 顶栏任务排序入口：图标 + 下拉菜单（六种排序，当前项高亮打勾） */
@Composable
private fun TaskSortMenu(
    current: TaskSortType,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (TaskSortType) -> Unit
) {
    Box {
        ActionButtonSmall(
            icon = Icons.Default.SwapVert,
            label = "排序",
            tint = if (current != TaskSortType.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            onClick = { onExpandedChange(true) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            TaskSortType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            type.label,
                            fontSize = 12.sp,
                            fontWeight = if (current == type) FontWeight.Bold else FontWeight.Normal,
                            color = if (current == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        if (current == type) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Spacer(Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        onSelect(type)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}
