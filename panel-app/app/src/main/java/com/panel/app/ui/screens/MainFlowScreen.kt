package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.panel.app.ui.viewmodel.MainViewModel

enum class BottomNavScreen(val title: String, val icon: ImageVector) {
    Tasks("任务列表", Icons.Default.TaskAlt),
    Envs("环境变量", Icons.Default.DataObject),
    Scripts("配置与脚本", Icons.Default.Folder),
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
    onNavigateToCreateScript: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab = uiState.currentTab

    // 调度子分段 Tab (0: 定时任务)
    var tasksSubTab by rememberSaveable { mutableIntStateOf(0) }
    // 配置与脚本子分段 Tab (0: 脚本文件, 1: 配置文件)
    var scriptsSubTab by rememberSaveable { mutableIntStateOf(0) }

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
                    // 统一置于顶部右上角，绝不占用内容主体高度
                    when (selectedTab) {
                        BottomNavScreen.Tasks -> {
                            TextButton(
                                onClick = { viewModel.setTaskBatchMode(!uiState.isTaskBatchMode) }
                            ) {
                                Text(
                                    text = if (uiState.isTaskBatchMode) "完成" else "批量",
                                    fontSize = 13.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    color = if (uiState.isTaskBatchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { showCreateTaskDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "新建任务")
                            }
                        }
                        BottomNavScreen.Envs -> {
                            TextButton(
                                onClick = { viewModel.setEnvBatchMode(!uiState.isEnvBatchMode) }
                            ) {
                                Text(
                                    text = if (uiState.isEnvBatchMode) "完成" else "批量",
                                    fontSize = 13.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    color = if (uiState.isEnvBatchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { showImportEnvDialog = true }) {
                                Icon(Icons.Default.PostAdd, contentDescription = "智能导入")
                            }
                            IconButton(onClick = { showCreateEnvDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "新建变量")
                            }
                        }
                        BottomNavScreen.Scripts -> {
                            if (scriptsSubTab == 0) {
                                IconButton(onClick = { showCreateScriptDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "新建脚本")
                                }
                            } else {
                                IconButton(onClick = { showDirectoryDrawer = true }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "文件目录")
                                }
                                IconButton(onClick = {
                                    viewModel.saveConfigFile(uiState.selectedConfigFile, uiState.configContent)
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "保存配置")
                                }
                            }
                        }
                        BottomNavScreen.Settings -> {
                            IconButton(onClick = { viewModel.toggleTheme() }) {
                                Icon(
                                    if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "切换主题",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onOpenPanelManager) {
                                Icon(Icons.Default.Dns, contentDescription = "面板管理")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                BottomNavScreen.entries.forEach { screen ->
                    val isSelected = selectedTab == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(screen) },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = { Text(text = screen.title, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                BottomNavScreen.Tasks -> {
                    val runningCount = uiState.tasks.count { it.isRunning }
                    val disabledCount = uiState.tasks.count { it.isDisabled }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 统计摘要行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.tasks.isNotEmpty()) {
                                val chips = listOf(
                                    Triple("全部", uiState.tasks.size, MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer),
                                    if (runningCount > 0) Triple("运行中", runningCount, Color(0xFFE8F5E9) to Color(0xFF2E7D32)) else null,
                                    if (disabledCount > 0) Triple("已禁用", disabledCount, MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer) else null
                                ).filterNotNull()
                                chips.forEach { (label, count, colorPair) ->
                                    val bg = colorPair.first
                                    val fg = colorPair.second
                                    Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                                            Text(label, fontSize = 11.sp, color = fg)
                                            Spacer(Modifier.width(4.dp))
                                            Text(count.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
                                        }
                                    }
                                }
                            }
                        }
                        // 过滤 TabRow
                        TabRow(selectedTabIndex = tasksSubTab, containerColor = MaterialTheme.colorScheme.surface) {
                            Tab(selected = tasksSubTab == 0, onClick = { tasksSubTab = 0 }, text = { Text("全部", fontSize = 12.sp) })
                            Tab(selected = tasksSubTab == 1, onClick = { tasksSubTab = 1 }, text = { Text("运行中", fontSize = 12.sp) })
                            Tab(selected = tasksSubTab == 2, onClick = { tasksSubTab = 2 }, text = { Text("已禁用", fontSize = 12.sp) })
                        }
                    }

                    TasksScreen(
                        viewModel = viewModel,
                        showCreateDialog = showCreateTaskDialog,
                        onDismissCreateDialog = { showCreateTaskDialog = false },
                        onOpenTaskDetail = onOpenTaskDetail,
                        onOpenLog = onOpenLogScreen,
                        currentSubTab = tasksSubTab
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
                        onOpenDevConsole = onOpenDevConsoleScreen,
                        onOpenLogHistory = onOpenLogHistoryScreen,
                        onOpenSystemSettings = onOpenSystemSettingsScreen,
                        onOpenPermissions = onOpenPermissionsScreen,
                        onOpenSubscriptionsScreen = onOpenSubscriptionsScreen
                    )
                }
            }
        }
    }
}
