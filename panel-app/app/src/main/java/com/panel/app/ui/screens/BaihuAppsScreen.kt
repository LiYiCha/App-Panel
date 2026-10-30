package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.remote.api.BaihuOpenApp
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.components.StatusBadge
import com.panel.app.ui.components.StatusTone
import com.panel.app.ui.viewmodel.MainViewModel

/**
 * 白虎应用市场（open2api /apps，Bearer OpenAPI Token）：
 * - 已装 Tab：应用列表 + 一键更新 / 回滚（有历史快照时）/ 自动更新开关
 * - 市场 Tab：可部署应用浏览 + 一键部署
 * 首次进入自动用 JWT 生成 OpenAPI Token，无需用户手动操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaihuAppsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var tab by remember { mutableIntStateOf(0) } // 0=已装 1=市场
    var installed by remember { mutableStateOf<List<BaihuOpenApp>?>(null) }
    var store by remember { mutableStateOf<List<BaihuOpenApp>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busyAppId by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        loadError = null
        val done: (Result<*>) -> Unit = {
            loading = false
            it.exceptionOrNull()?.let { e -> loadError = e.message }
        }
        if (tab == 0) viewModel.loadBaihuInstalledApps { r -> installed = r.getOrNull(); done(r) }
        else viewModel.loadBaihuStoreApps { r -> store = r.getOrNull(); done(r) }
    }

    LaunchedEffect(tab) { load() }

    fun runAppAction(appId: String, successMsg: String, action: (onResult: (Result<Boolean>) -> Unit) -> Unit) {
        if (busyAppId != null) return
        busyAppId = appId
        action { r ->
            busyAppId = null
            Toast.makeText(
                context,
                if (r.isSuccess) successMsg else r.exceptionOrNull()?.message ?: "操作失败",
                Toast.LENGTH_SHORT
            ).show()
            if (r.isSuccess && tab == 0) load()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用市场", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = Icons.Default.Refresh,
                        label = "刷新",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { load() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("已安装 (${installed?.size ?: 0})", fontSize = 12.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("市场", fontSize = 12.sp) })
            }

            val current = if (tab == 0) installed else store
            when {
                loading && current == null -> {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                }
                loadError != null && current == null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(loadError ?: "加载失败", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { load() }) { Text("重试", fontSize = 12.sp) }
                    }
                }
                else -> {
                    val apps = current.orEmpty()
                    if (apps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (tab == 0) "暂无应用" else "市场暂无应用",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(apps, key = { it.id ?: it.name ?: it.version ?: "idx" }) { app ->
                                BaihuAppCard(
                                    app = app,
                                    isMarket = tab == 1,
                                    busy = busyAppId != null && busyAppId == app.id,
                                    onUpdate = {
                                        val id = app.id ?: return@BaihuAppCard
                                        runAppAction(id, "更新已开始，请在面板查看进度") { result ->
                                            viewModel.updateBaihuApp(id, result)
                                        }
                                    },
                                    onRollback = {
                                        val id = app.id ?: return@BaihuAppCard
                                        runAppAction(id, "已回滚到上一版本") { result ->
                                            viewModel.rollbackBaihuApp(id, result)
                                        }
                                    },
                                    onToggleAutoUpdate = { enabled ->
                                        val id = app.id ?: return@BaihuAppCard
                                        runAppAction(id, if (enabled) "已开启自动更新" else "已关闭自动更新") { result ->
                                            viewModel.toggleBaihuAutoUpdate(id, enabled, result)
                                        }
                                    },
                                    onDeploy = {
                                        val id = app.id ?: return@BaihuAppCard
                                        runAppAction(id, "部署已开始，请在面板查看进度") { result ->
                                            viewModel.applyBaihuApp(app.homepage ?: "", result)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 应用卡片：已装模式展示 更新/回滚/自动更新开关；市场模式展示部署按钮 */
@Composable
private fun BaihuAppCard(
    app: BaihuOpenApp,
    isMarket: Boolean,
    busy: Boolean,
    onUpdate: () -> Unit,
    onRollback: () -> Unit,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onDeploy: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = app.name ?: "未命名应用",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    app.version?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                StatusBadge(
                    text = app.status ?: "未知",
                    tone = when (app.status) {
                        "running", "active" -> StatusTone.SECONDARY
                        "failed", "error" -> StatusTone.ERROR
                        else -> StatusTone.NEUTRAL
                    }
                )
            }

            app.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                app.author?.takeIf { it.isNotBlank() }?.let {
                    Text("by $it", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                app.tasks_count?.takeIf { it > 0 }?.let {
                    Text("$it 个任务", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isMarket && app.auto_update == true) {
                    Text("自动更新已开启", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
                }
                if (!isMarket && app.previous != null) {
                    Text("可回滚 (${app.previous.version ?: "上一版"})", fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("处理中...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (isMarket) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDeploy,
                        enabled = !app.homepage.isNullOrBlank(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("部署", fontSize = 11.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "更新于 ${app.updated_at ?: "--"}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ActionButtonSmall(
                            icon = Icons.Default.Update,
                            label = "更新",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onUpdate
                        )
                        if (app.previous != null) {
                            ActionButtonSmall(
                                icon = Icons.Default.History,
                                label = "回滚",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onRollback
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text("自动更新", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(
                                checked = app.auto_update == true,
                                onCheckedChange = onToggleAutoUpdate,
                                modifier = Modifier.height(22.dp).width(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
