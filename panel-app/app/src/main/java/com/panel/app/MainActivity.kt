package com.panel.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.panel.app.data.model.PanelType
import com.panel.app.script.LocalScriptFeature
import com.panel.app.ui.screens.*
import com.panel.app.ui.theme.PanelAppTheme
import com.panel.app.ui.viewmodel.MainUiState
import com.panel.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var localScriptFeature: LocalScriptFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            PanelAppTheme(darkTheme = uiState.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(viewModel = viewModel, uiState = uiState, localScriptFeature = localScriptFeature)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(viewModel: MainViewModel, uiState: MainUiState, localScriptFeature: LocalScriptFeature) {
    val navController = rememberNavController()
    val appContext = LocalContext.current

    val hasLoggedInPanel = uiState.panels.any {
        !it.token.isNullOrEmpty() || (!it.username.isNullOrEmpty() && !it.password.isNullOrEmpty())
    }

    LaunchedEffect(uiState.isDatabaseReady, hasLoggedInPanel) {
        if (uiState.isDatabaseReady && !hasLoggedInPanel) {
            // 首次启动先走引导页，已看过（或重看后退出）直接进登录页
            val guideRoute = if (GuidePrefs.isCompleted(appContext)) "login" else "guide"
            navController.navigate(guideRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    // 自动重登确认失败（密码错误等认证类原因，非网络）：回到该面板的登录页让用户重新认证
    LaunchedEffect(uiState.reloginPanelId) {
        val pid = uiState.reloginPanelId ?: return@LaunchedEffect
        viewModel.clearForceRelogin()
        navController.navigate("login?panelId=$pid") { launchSingleTop = true }
    }

    fun safePopBack() {
        if (navController.previousBackStackEntry != null && navController.currentDestination?.route != "main_flow") {
            navController.popBackStack()
        } else {
            navController.navigate("main_flow") {
                popUpTo("main_flow") { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = "main_flow") {
        composable(
            route = "guide?replay={replay}",
            arguments = listOf(
                navArgument("replay") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val replay = backStackEntry.arguments?.getBoolean("replay") ?: false
            GuideScreen(
                replay = replay,
                localScriptEnabled = localScriptFeature.enabled,
                viewModel = viewModel,
                onFinish = {
                    if (replay) {
                        // 设置页重看：原路返回，不再改写启动跳转
                        navController.popBackStack()
                    } else {
                        navController.navigate("login") { popUpTo(0) { inclusive = true } }
                    }
                },
                onBack = if (replay) ({ navController.popBackStack() }) else null
            )
        }

        composable(
            route = "login?panelId={panelId}",
            arguments = listOf(
                navArgument("panelId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val panelId = backStackEntry.arguments?.getString("panelId")
            val editingPanel = remember(panelId, uiState.panels) {
                if (panelId != null) uiState.panels.firstOrNull { it.id == panelId } else null
            }
            LoginScreen(
                viewModel = viewModel,
                editPanel = editingPanel,
                onBack = if (navController.previousBackStackEntry != null) {
                    { safePopBack() }
                } else null,
                onEnterLocalMode = localScriptFeature.localModeRoute?.let { route ->
                    { navController.navigate(route) }
                },
                onLoginSuccess = {
                    navController.navigate("main_flow") { popUpTo("login") { inclusive = true } }
                }
            )
        }

        composable("main_flow") {
            MainFlowScreen(
                viewModel = viewModel,
                onNavigateToLogin = { navController.navigate("login") },
                onOpenPanelManager = { navController.navigate("panel_manager") },
                onOpenTaskDetail = { taskId -> navController.navigate("task_detail/$taskId") },
                onOpenLogScreen = { taskNameOrId ->
                    val encoded = Uri.encode(taskNameOrId)
                    navController.navigate("log_viewer?title=$encoded&taskId=$encoded")
                },
                onOpenScriptEditorScreen = { scriptPath ->
                    val encoded = Uri.encode(scriptPath)
                    navController.navigate("standalone_editor/$encoded")
                },
                onOpenDepsScreen = { navController.navigate("standalone_deps") },
                onOpenLoginLogsScreen = { navController.navigate("login_logs") },
                onOpenServerLogsScreen = { navController.navigate("server_logs") },
                onOpenDashboardScreen = { navController.navigate("dashboard") },
                onOpenBackupScreen = { navController.navigate("backup_restore") },
                onOpenDevConsoleScreen = { navController.navigate("developer_console") },
                onOpenLogHistoryScreen = { navController.navigate("execution_history") },
                onOpenSystemSettingsScreen = { navController.navigate("system_settings") },
                onOpenPermissionsScreen = { navController.navigate("app_permissions") },
                onOpenSubscriptionsScreen = { navController.navigate("subscriptions") },
                onOpenAppsScreen = { navController.navigate("baihu_apps") },
                onOpenLocalScriptsScreen = {
                    // standard 变体未启用本地脚本，入口不响应
                    if (localScriptFeature.enabled) navController.navigate("local_center")
                },
                onOpenOtherSettingsScreen = { navController.navigate("other_settings") },
                onOpenEnvRules = { navController.navigate("env_rules") },
                localScriptEnabled = localScriptFeature.enabled,
                localScriptFeature = localScriptFeature,
                onOpenLocalEditor = { name ->
                    navController.navigate("local_editor/${Uri.encode(name)}")
                },
                onOpenLocalConsole = { runId ->
                    navController.navigate("local_console/$runId")
                },
                onNavigateToCreateScript = { navController.navigate("create_script") }
            )
        }

        composable("baihu_apps") {
            BaihuAppsScreen(
                viewModel = viewModel,
                onBack = { safePopBack() }
            )
        }

        composable("create_script") {
            val existingDirs = extractDirectories(uiState.scriptTree)
            CreateScriptScreen(
                viewModel = viewModel,
                existingDirs = existingDirs,
                onBack = { safePopBack() }
            )
        }

        composable(
            route = "log_viewer?title={title}&taskId={taskId}&path={path}&running={running}",
            arguments = listOf(
                navArgument("title") { type = NavType.StringType; defaultValue = "执行日志" },
                navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("running") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "执行日志")
            val taskId = backStackEntry.arguments?.getString("taskId")?.let { Uri.decode(it) }
            val path = backStackEntry.arguments?.getString("path")?.let { Uri.decode(it) }
            val running = backStackEntry.arguments?.getBoolean("running") ?: false

            LogViewerScreen(
                title = title,
                taskId = taskId ?: "",
                logPath = path ?: "",
                taskRunning = running,
                viewModel = viewModel,
                onBack = { safePopBack() }
            )
        }

        composable("login_logs") {
            LoginLogsScreen(viewModel = viewModel, onBack = { safePopBack() })
        }

        composable("dashboard") {
            DashboardScreen(viewModel = viewModel, onBack = { safePopBack() })
        }

        composable("backup_restore") {
            BackupRestoreScreen(viewModel = viewModel, onBack = { safePopBack() })
        }

        // 分段规则页：轻量转场（水平滑入 1/3 屏 + 淡入，约 200ms）
        composable(
            route = "env_rules",
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(animationSpec = tween(200))
            }
        ) {
            EnvRulesScreen(viewModel = viewModel, onBack = { safePopBack() })
        }

        composable("server_logs") {
            ServerLogsScreen(
                viewModel = viewModel,
                onBack = { safePopBack() },
                onOpenLogViewer = { title, path ->
                    val encTitle = Uri.encode(title)
                    val encPath = Uri.encode(path)
                    navController.navigate("log_viewer?title=$encTitle&path=$encPath")
                }
            )
        }

        composable("developer_console") {
            DeveloperConsoleScreen(viewModel = viewModel, onBack = { safePopBack() })
        }

        composable("panel_manager") {
            PanelManagerScreen(
                viewModel = viewModel,
                onBack = { safePopBack() },
                onNavigateToAddPanel = { navController.navigate("login") },
                onNavigateToSwitchAccount = { panel ->
                    navController.navigate("login?panelId=${panel.id}")
                },
                onAllPanelsDeleted = {
                    navController.navigate("login") { popUpTo("panel_manager") { inclusive = true } }
                }
            )
        }

        composable(
            route = "execution_history?taskId={taskId}",
            arguments = listOf(
                navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.takeIf { it.isNotBlank() && it != "null" }
            ExecutionHistoryScreen(
                taskId = taskId,
                viewModel = viewModel,
                onBack = { safePopBack() },
                onOpenLogViewer = { title, logPath, logTaskId, isRunning ->
                    val encTitle = Uri.encode(title)
                    val encPath = Uri.encode(logPath)
                    val encTaskId = Uri.encode(logTaskId)
                    navController.navigate("log_viewer?title=$encTitle&path=$encPath&taskId=$encTaskId&running=$isRunning")
                }
            )
        }

        composable(
            route = "task_detail/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            CompactTaskDetailScreen(
                taskId = taskId,
                viewModel = viewModel,
                onBack = { safePopBack() },
                onOpenScriptEditor = { scriptPath ->
                    val encoded = Uri.encode(scriptPath)
                    navController.navigate("standalone_editor/$encoded")
                },
                onOpenLog = { title, tId ->
                    val encTitle = Uri.encode(title)
                    val encId = Uri.encode(tId)
                    navController.navigate("log_viewer?title=$encTitle&taskId=$encId")
                },
                onOpenHistory = { tId ->
                    navController.navigate("execution_history?taskId=$tId")
                }
            )
        }

        composable(
            route = "standalone_editor/{scriptName}",
            arguments = listOf(navArgument("scriptName") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawArg = backStackEntry.arguments?.getString("scriptName") ?: ""
            val scriptName = Uri.decode(rawArg)
            val uiStateValue = viewModel.uiState.collectAsState()
            var scriptContent by remember(scriptName) {
                mutableStateOf(uiStateValue.value.scriptViewerCache[scriptName] ?: "")
            }
            val loadError = uiStateValue.value.scriptViewerError
                ?.takeIf { uiStateValue.value.scriptViewerErrorPath == scriptName }
            LaunchedEffect(scriptName) {
                if (uiStateValue.value.scriptViewerCache[scriptName].isNullOrBlank()) {
                    viewModel.readScript(scriptName) { content -> scriptContent = content }
                } else {
                    scriptContent = uiStateValue.value.scriptViewerCache[scriptName] ?: ""
                }
            }
            StandaloneScriptEditorScreen(
                scriptName = scriptName,
                initialContent = scriptContent,
                loadError = loadError,
                onRetry = {
                    viewModel.readScript(scriptName) { content -> scriptContent = content }
                },
                viewModel = viewModel,
                onSave = { updatedContent -> viewModel.saveScript(scriptName, updatedContent) },
                onAddToTask = { name, command, schedule -> viewModel.createTask(name, command, schedule) },
                onBack = { safePopBack() }
            )
        }

        composable("standalone_deps") {
            StandaloneDepsScreen(viewModel = viewModel, onBack = { safePopBack() })
        }

        // 本地脚本路由由变体 feature 注册（full：完整路由；standard：空实现）
        localScriptFeature.installRoutes(
            nav = this,
            openRoute = { route -> navController.navigate(route) },
            popBack = { safePopBack() }
        )

        composable("system_settings") {
            val currentPanel = uiState.panels.getOrNull(uiState.selectedPanelIndex)
            SystemSettingsScreen(
                viewModel = viewModel,
                panelType = currentPanel?.type ?: PanelType.BAIHU,
                dashboard = viewModel.getCachedDashboard(),
                onBack = { safePopBack() }
            )
        }

        composable("other_settings") {
            OtherSettingsScreen(
                viewModel = viewModel,
                onBack = { safePopBack() },
                onOpenPermissions = { navController.navigate("app_permissions") },
                onOpenDevLog = { navController.navigate("developer_console") },
                onReplayGuide = { navController.navigate("guide?replay=true") }
            )
        }

        composable("app_permissions") {
            AppPermissionsScreen(onBack = { safePopBack() })
        }

        composable("subscriptions") {
            SubscriptionsScreen(viewModel = viewModel, onBack = { safePopBack() })
        }
    }
}
