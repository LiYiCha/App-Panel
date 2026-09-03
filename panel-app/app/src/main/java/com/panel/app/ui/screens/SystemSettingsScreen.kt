package com.panel.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.PanelDashboard
import com.panel.app.data.model.PanelType
import com.panel.app.ui.viewmodel.MainViewModel

/**
 * 系统高级配置二级页面（从设置页进入）。
 * 青龙/白虎面板分别展示不同的系统配置卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    viewModel: MainViewModel,
    panelType: PanelType,
    dashboard: PanelDashboard?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (panelType == PanelType.BAIHU) "白虎系统状态与配置" else "青龙系统高级配置",
                            fontSize = 15.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            if (panelType == PanelType.QINGLONG_V15 || panelType == PanelType.QINGLONG_V10) {
                QinglongSystemSettingsCard(viewModel)
            } else {
                BaihuSystemSettingsCard(dashboard)
            }
        }
    }
}
