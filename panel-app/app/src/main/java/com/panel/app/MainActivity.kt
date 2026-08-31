package com.panel.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.panel.app.ui.screens.*
import com.panel.app.ui.theme.PanelAppTheme
import com.panel.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()

            PanelAppTheme(darkTheme = uiState.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val hasLoggedInPanel = uiState.panels.any { !it.token.isNullOrEmpty() || (!it.username.isNullOrEmpty() && !it.password.isNullOrEmpty()) }
                    val startDest = "main_flow"

                    LaunchedEffect(uiState.isDatabaseReady, hasLoggedInPanel) {
                        if (uiState.isDatabaseReady && !hasLoggedInPanel) {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDest
                    ) {
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
                                    { navController.popBackStack() }
                                } else null,
                                onLoginSuccess = {
                                    navController.navigate("main_flow") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("main_flow") {
                            MainFlowScreen(
                                viewModel = viewModel,
                                onNavigateToLogin = {
                                    navController.navigate("login")
                                },
                                onOpenPanelManager = {
                                    navController.navigate("panel_manager")
                                },
                                onOpenTaskDetail = { taskId ->
                                    navController.navigate("task_detail/$taskId")
                                },
                                onOpenLogScreen = { taskName ->
                                    val encoded = Uri.encode(taskName)
                                    navController.navigate("log_viewer?title=$encoded&taskId=$encoded")
                                },
                                onOpenScriptEditorScreen = { scriptPath ->
                                    val encoded = Uri.encode(scriptPath)
                                    navController.navigate("standalone_editor/$encoded")
                                },
                                onOpenDepsScreen = {
                                    navController.navigate("standalone_deps")
                                },
                                onOpenLoginLogsScreen = {
                                    navController.navigate("login_logs")
                                },
                                onOpenServerLogsScreen = {
                                    navController.navigate("server_logs")
                                },
                                onOpenDashboardScreen = {
                                    navController.navigate("dashboard")
                                },
                                onOpenBackupScreen = {
                                    navController.navigate("backup_restore")
                                },
                                onOpenDevConsoleScreen = {
                                    navController.navigate("developer_console")
                                },
                                onOpenExecutionHistoryScreen = {
                                    navController.navigate("execution_history")
                                },
                                onNavigateToCreateScript = {
                                    navController.navigate("create_script")
                                }
                            )
                        }

                        composable("create_script") {
                            val existingDirs = extractAllDirectories(uiState.scriptTree)
                            CreateScriptScreen(
                                viewModel = viewModel,
                                existingDirs = existingDirs,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "log_viewer?title={title}&taskId={taskId}&path={path}",
                            arguments = listOf(
                                navArgument("title") { type = NavType.StringType; defaultValue = "执行日志" },
                                navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "执行日志")
                            val taskId = backStackEntry.arguments?.getString("taskId")?.let { Uri.decode(it) }
                            val path = backStackEntry.arguments?.getString("path")?.let { Uri.decode(it) }

                            LogViewerScreen(
                                title = title,
                                taskId = taskId ?: "",
                                logPath = path ?: "",
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("execution_history") {
                            ExecutionHistoryScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenLogViewer = { title, taskId ->
                                    val encTitle = Uri.encode(title)
                                    val encId = Uri.encode(taskId)
                                    navController.navigate("log_viewer?title=$encTitle&taskId=$encId")
                                }
                            )
                        }

                        composable("login_logs") {
                            LoginLogsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("backup_restore") {
                            BackupRestoreScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("server_logs") {
                            ServerLogsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenLogViewer = { title, path ->
                                    val encTitle = Uri.encode(title)
                                    val encPath = Uri.encode(path)
                                    navController.navigate("log_viewer?title=$encTitle&path=$encPath")
                                }
                            )
                        }

                        composable("developer_console") {
                            DeveloperConsoleScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("panel_manager") {
                            PanelManagerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToAddPanel = {
                                    navController.navigate("login")
                                },
                                onNavigateToSwitchAccount = { panel ->
                                    navController.navigate("login?panelId=${panel.id}")
                                },
                                onAllPanelsDeleted = {
                                    navController.navigate("login") {
                                        popUpTo("panel_manager") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "task_detail/{taskId}",
                            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                            TaskDetailScreen(
                                taskId = taskId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenLogViewer = { title, tId, logPath ->
                                    val encTitle = Uri.encode(title)
                                    if (logPath.isNotBlank()) {
                                        val encPath = Uri.encode(logPath)
                                        navController.navigate("log_viewer?title=$encTitle&path=$encPath")
                                    } else {
                                        val encId = Uri.encode(tId)
                                        navController.navigate("log_viewer?title=$encTitle&taskId=$encId")
                                    }
                                }
                            )
                        }

                        composable(
                            route = "standalone_log/{taskName}",
                            arguments = listOf(navArgument("taskName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val rawArg = backStackEntry.arguments?.getString("taskName") ?: ""
                            val taskIdentifier = Uri.decode(rawArg)
                            val allTasks = viewModel.uiState.collectAsState().value.tasks
                            val displayName = allTasks.firstOrNull { it.id == taskIdentifier }?.name ?: taskIdentifier
                            var logContent by remember(taskIdentifier) { mutableStateOf("正在拉取实时执行日志...") }
                            LaunchedEffect(taskIdentifier) {
                                viewModel.getTaskLog(taskIdentifier) { log ->
                                    logContent = log
                                }
                            }
                            StandaloneLogScreen(
                                taskName = displayName,
                                initialLog = logContent,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "standalone_editor/{scriptName}",
                            arguments = listOf(navArgument("scriptName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val rawArg = backStackEntry.arguments?.getString("scriptName") ?: ""
                            val scriptName = Uri.decode(rawArg)
                            var scriptContent by remember(scriptName) { mutableStateOf("") }
                            LaunchedEffect(scriptName) {
                                viewModel.readScript(scriptName) { content ->
                                    scriptContent = content
                                }
                            }
                            StandaloneScriptEditorScreen(
                                scriptName = scriptName,
                                initialContent = scriptContent,
                                onSave = { updatedContent ->
                                    viewModel.saveScript(scriptName, updatedContent)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("standalone_deps") {
                            StandaloneDepsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
