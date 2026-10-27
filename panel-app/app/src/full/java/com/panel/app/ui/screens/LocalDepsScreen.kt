package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.util.LocalScriptKit
import com.panel.corescript.deps.PyDepInfo
import kotlinx.coroutines.launch

/**
 * Python 运行时依赖管理：安装 / 卸载纯 Python 包（PyPI wheel），
 * 运行脚本时该目录自动注入 sys.path。原生扩展包需构建期预置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDepsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    var deps by remember { mutableStateOf(LocalScriptKit.pyDeps.list()) }
    fun refresh() { deps = LocalScriptKit.pyDeps.list() }

    var showInstallDialog by remember { mutableStateOf(false) }
    var depName by remember { mutableStateOf("") }
    var installProgress by remember { mutableStateOf<String?>(null) }
    var deletingDep by remember { mutableStateOf<PyDepInfo?>(null) }

    val totalSize = remember(deps) { deps.sumOf { it.sizeBytes } }

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
                        Text("本地 Python 依赖", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("${deps.size} 个包 · 共 ${formatSize(totalSize)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = Icons.Default.Add,
                        label = "安装",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { showInstallDialog = true }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            if (deps.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("暂无运行时依赖", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("requests 等基础包已随应用预置", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(deps, key = { it.dirName }) { dep ->
                        DepCard(
                            dep = dep,
                            onDelete = { deletingDep = dep }
                        )
                    }
                }
            }

            Text(
                "仅支持纯 Python 包（*-any.whl）；含 C 扩展的包无法在设备上安装，需构建期预置。安装的包对本机所有 Python 脚本生效。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }

    // 安装对话框
    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { if (installProgress == null) showInstallDialog = false },
            title = { Text("安装 Python 依赖", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = depName,
                        onValueChange = { depName = it },
                        label = { Text("包名（如 rich、httpx）", fontSize = 11.sp) },
                        singleLine = true,
                        enabled = installProgress == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    installProgress?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = installProgress == null && depName.isNotBlank(),
                    onClick = {
                        installProgress = "开始安装…"
                        scope.launch {
                            LocalScriptKit.pyDeps.install(depName) { progress -> installProgress = progress }
                                .onSuccess { info ->
                                    Toast.makeText(context, "已安装 ${info.name} ${info.version}", Toast.LENGTH_SHORT).show()
                                    showInstallDialog = false
                                    depName = ""
                                    refresh()
                                }
                                .onFailure { e ->
                                    Toast.makeText(context, e.message ?: "安装失败", Toast.LENGTH_LONG).show()
                                }
                            installProgress = null
                        }
                    }
                ) { Text("安装") }
            },
            dismissButton = {
                TextButton(
                    enabled = installProgress == null,
                    onClick = { showInstallDialog = false }
                ) { Text("取消") }
            }
        )
    }

    // 卸载确认
    deletingDep?.let { dep ->
        AlertDialog(
            onDismissRequest = { deletingDep = null },
            title = { Text("卸载依赖", fontSize = 15.sp) },
            text = { Text("确定卸载 ${dep.name} ${dep.version}？", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        LocalScriptKit.pyDeps.uninstall(dep.dirName).onSuccess {
                            Toast.makeText(context, "已卸载 ${dep.name}", Toast.LENGTH_SHORT).show()
                            refresh()
                        }.onFailure { e ->
                            Toast.makeText(context, e.message ?: "卸载失败", Toast.LENGTH_SHORT).show()
                        }
                        deletingDep = null
                    }
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingDep = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DepCard(dep: PyDepInfo, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dep.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "v${dep.version} · ${formatSize(dep.sizeBytes)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "卸载", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
