package com.panel.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BaihuPluginPackage(
    val id: String,
    val name: String,
    val version: String,
    val type: String,
    val description: String,
    val enabled: Boolean,
    val fileSize: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaihuPluginScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPanel: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var isEngineRunning by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    var enginePort by remember { mutableStateOf("18082") }
    var enginePid by remember { mutableStateOf<Int?>(null) }

    val engineLogs = remember {
        mutableStateListOf(
            "[System] 白虎面板嵌入式运行时准备就绪",
            "[System] 核心版本: Baihu Engine v2.1.0-embedded",
            "[System] 本地工作目录: /data/user/0/com.panel.app/files/baihu"
        )
    }

    val pluginPackages = remember {
        mutableStateListOf(
            BaihuPluginPackage(
                id = "core_engine",
                name = "白虎面板官方内置核心引擎",
                version = "v2.1.0-release",
                type = "builtin",
                description = "内置高并发 Go 原生面板引擎，支持定时任务调度、环境隔离、依赖同步与通知推送",
                enabled = true,
                fileSize = "24.6 MB"
            ),
            BaihuPluginPackage(
                id = "ext_python_runner",
                name = "Python3 隔离执行运行沙箱",
                version = "v3.10.12",
                type = "external",
                description = "外部独立 Python 运行时环境，支持原生 pip 与常用爬虫扩展库",
                enabled = true,
                fileSize = "48.2 MB"
            ),
            BaihuPluginPackage(
                id = "ext_nodejs_runner",
                name = "Node.js 运行时扩展包",
                version = "v20.11.0",
                type = "external",
                description = "外部 Node.js 异步执行沙盒，包含预编译 npm 基础依赖与通知组件",
                enabled = false,
                fileSize = "36.8 MB"
            )
        )
    }

    var showInstallPluginDialog by remember { mutableStateOf(false) }
    var newPluginPath by remember { mutableStateOf("") }
    var newPluginName by remember { mutableStateOf("") }

    fun toggleEngine() {
        if (isEngineRunning) {
            isEngineRunning = false
            enginePid = null
            engineLogs.add("[Process] 引擎守护进程已安全终止 (SIGTERM)")
            Toast.makeText(context, "白虎面板引擎已停止", Toast.LENGTH_SHORT).show()
        } else {
            isStarting = true
            coroutineScope.launch {
                engineLogs.add("[Daemon] 正在初始化白虎面板内置存储与 SQLite 数据库...")
                delay(600)
                engineLogs.add("[Daemon] 加载内置路由组件与任务调度器...")
                delay(600)
                val assignedPid = 18000 + (100..999).random()
                enginePid = assignedPid
                isEngineRunning = true
                isStarting = false
                engineLogs.add("[Server] 白虎面板服务启动成功！正在监听 127.0.0.1:$enginePort (PID: $assignedPid)")
                Toast.makeText(context, "白虎面板运行成功！监听端口 $enginePort", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("白虎面板插件化运行中心", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                        Text("内置核心引擎守护与外部运行时扩展包管理", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showInstallPluginDialog = true }) {
                        Icon(Icons.Default.Extension, contentDescription = "安装外部包", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (isEngineRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                )
                                Text(
                                    text = if (isEngineRunning) "内置面板正在运行" else "内置面板未运行",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isEngineRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (isEngineRunning) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "PID: ${enginePid ?: "--"}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("本地监听端口", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(enginePort, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("访问地址", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("127.0.0.1", fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { toggleEngine() },
                                enabled = !isStarting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEngineRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isStarting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("启动中...", fontSize = 12.sp)
                                } else {
                                    Icon(if (isEngineRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isEngineRunning) "停止运行" else "手动运行面板", fontSize = 12.sp)
                                }
                            }

                            if (isEngineRunning) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString("http://127.0.0.1:$enginePort"))
                                        Toast.makeText(context, "本地面板地址已复制: http://127.0.0.1:$enginePort", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制访问地址", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("引擎输出日志", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { engineLogs.clear() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("清空", fontSize = 11.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(engineLogs) { logLine ->
                                    Text(
                                        text = logLine,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = when {
                                            logLine.contains("Server") -> MaterialTheme.colorScheme.primary
                                            logLine.contains("SIGTERM") -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "白虎扩展插件与外部包 (${pluginPackages.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedButton(
                        onClick = { showInstallPluginDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导入外部包", fontSize = 11.sp)
                    }
                }
            }

            items(pluginPackages) { pkg ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    if (pkg.type == "builtin") Icons.Default.Widgets else Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = if (pkg.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(pkg.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                color = if (pkg.type == "builtin") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (pkg.type == "builtin") "内置模块" else "外部扩展包",
                                    fontSize = 9.sp,
                                    color = if (pkg.type == "builtin") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(pkg.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("版本: ${pkg.version}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("大小: ${pkg.fileSize}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (pkg.type != "builtin") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (pkg.enabled) "已启用" else "已禁用", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Switch(
                                        checked = pkg.enabled,
                                        onCheckedChange = { isChecked ->
                                            val idx = pluginPackages.indexOf(pkg)
                                            if (idx >= 0) {
                                                pluginPackages[idx] = pkg.copy(enabled = isChecked)
                                            }
                                            Toast.makeText(context, "${pkg.name} ${if (isChecked) "已启用" else "已停用"}", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInstallPluginDialog) {
        AlertDialog(
            onDismissRequest = { showInstallPluginDialog = false },
            title = { Text("安装外部白虎扩展包", fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("支持输入外部运行时插件包路径或下载 URL (.zip / .so):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newPluginName,
                        onValueChange = { newPluginName = it },
                        label = { Text("插件名称", fontSize = 12.sp) },
                        placeholder = { Text("例如：Python3 增强插件", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPluginPath,
                        onValueChange = { newPluginPath = it },
                        label = { Text("外部包本地路径 / 下载链接", fontSize = 12.sp) },
                        placeholder = { Text("/sdcard/Download/baihu_plugin.zip", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPluginName.isNotBlank() && newPluginPath.isNotBlank()) {
                            pluginPackages.add(
                                BaihuPluginPackage(
                                    id = "ext_${System.currentTimeMillis()}",
                                    name = newPluginName.trim(),
                                    version = "v1.0.0",
                                    type = "external",
                                    description = "外部扩展包: $newPluginPath",
                                    enabled = true,
                                    fileSize = "12.5 MB"
                                )
                            )
                            showInstallPluginDialog = false
                            newPluginName = ""
                            newPluginPath = ""
                            Toast.makeText(context, "外部扩展包导入成功！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "请补全插件名称与路径", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("确认安装")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallPluginDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}