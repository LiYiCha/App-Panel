package com.panel.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 本地运行中心：本地脚本域单页整合（Tab：脚本 | 依赖 | 运行记录）。
 * 编辑器与运行控制台仍为独立路由，从中心页跳入。
 * 下载加速镜像为应用级设置，已在设置页「其他」二级页管理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptCenterScreen(
    initialTab: Int = 0,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenConsole: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 2)) { 3 }
    val tabs = listOf("脚本", "依赖", "运行记录")

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
                        Text("本地模式", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("在设备上运行 JS / Python 脚本，无需登录面板", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> ScriptsTabContent(
                        onOpenEditor = onOpenEditor,
                        onOpenConsole = onOpenConsole
                    )
                    1 -> DepsTabContent()
                    else -> RunHistoryTabContent(
                        onOpenRun = onOpenConsole
                    )
                }
            }
        }
    }
}
