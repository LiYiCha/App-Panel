package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
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
                            // 多维排序（从筛选行收进顶栏）
                            TaskSortMenu(
                                current = taskSortType,
                                expanded = showTaskSortMenu,
                                onExpandedChange = { showTaskSortMenu = it },
                                onSelect = { taskSortType = it }
                            )
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
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
                        label = { Text(text = screen.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    TasksScreen(
                        viewModel = viewModel,
                        showCreateDialog = showCreateTaskDialog,
                        onDismissCreateDialog = { showCreateTaskDialog = false },
                        onOpenTaskDetail = onOpenTaskDetail,
                        onOpenLog = onOpenLogScreen,
                        sortType = taskSortType,
                        onSortTypeChange = { taskSortType = it }
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

/** 顶栏任务排序入口：图标 + 下拉菜单（六种排序，当前项高亮打勾） */
@Composable
private fun TaskSortMenu(
    current: TaskSortType,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (TaskSortType) -> Unit
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Default.SwapVert,
                contentDescription = "排序",
                tint = if (current != TaskSortType.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
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
