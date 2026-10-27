package com.panel.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
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
import androidx.core.content.ContextCompat
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.util.LocalScriptKit
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.runner.ScriptRunnerService
import com.panel.corescript.store.LocalScript
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地脚本页：管理设备私有目录内的 .py / .js 脚本，
 * 支持新建、文件导入、编辑与本地引擎运行（区别于远程面板脚本）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScriptsScreen(
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenConsole: (String) -> Unit,
    onOpenDeps: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var scripts by remember { mutableStateOf(LocalScriptKit.store.list()) }
    fun refresh() { scripts = LocalScriptKit.store.list() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var createLang by remember { mutableStateOf(ScriptLang.PYTHON) }
    var createName by remember { mutableStateOf("") }
    var deletingScript by remember { mutableStateOf<LocalScript?>(null) }

    // 通知权限：Android 13+ 前台服务通知需要授权（拒绝也允许运行，仅通知不可见）
    var pendingRunScript by remember { mutableStateOf<LocalScript?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingRunScript?.let { script ->
            pendingRunScript = null
            startRun(context, script, onOpenConsole)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importScript(context, uri) { imported ->
            if (imported != null) {
                refresh()
                Toast.makeText(context, "已导入 ${imported.name}", Toast.LENGTH_SHORT).show()
            }
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
                title = {
                    Column {
                        Text("本地脚本", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("在设备上运行 JS / Python 脚本", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = Icons.Default.Psychology,
                        label = "依赖",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onOpenDeps
                    )
                    ActionButtonSmall(
                        icon = Icons.Default.PostAdd,
                        label = "导入",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { importLauncher.launch(arrayOf("*/*")) }
                    )
                    ActionButtonSmall(
                        icon = Icons.Default.Add,
                        label = "新建",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { showCreateDialog = true }
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
            if (scripts.isEmpty()) {
                EmptyHint(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(scripts, key = { it.name }) { script ->
                        ScriptCard(
                            script = script,
                            onEdit = { onOpenEditor(script.name) },
                            onRun = {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingRunScript = script
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    startRun(context, script, onOpenConsole)
                                }
                            },
                            onDelete = { deletingScript = script }
                        )
                    }
                }
            }

            Text(
                "脚本保存在应用私有目录；运行输出自动落盘可在控制台回看。Python 依赖在「依赖」页安装。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }

    // 新建对话框
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建本地脚本", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScriptLang.entries.forEach { lang ->
                            FilterChip(
                                selected = createLang == lang,
                                onClick = { createLang = lang },
                                label = { Text(lang.label, fontSize = 12.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        label = { Text("文件名（扩展名自动补全）", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalScriptKit.store.create(createName, "", createLang).onSuccess { file ->
                        showCreateDialog = false
                        createName = ""
                        refresh()
                        onOpenEditor(file.name)
                    }.onFailure { e ->
                        Toast.makeText(context, e.message ?: "创建失败", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("创建并编辑") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    // 删除确认
    deletingScript?.let { script ->
        AlertDialog(
            onDismissRequest = { deletingScript = null },
            title = { Text("删除脚本", fontSize = 15.sp) },
            text = { Text("确定删除 ${script.name}？该操作不可恢复。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    LocalScriptKit.store.delete(script.file)
                    deletingScript = null
                    refresh()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingScript = null }) { Text("取消") }
            }
        )
    }
}

private fun startRun(context: Context, script: LocalScript, onOpenConsole: (String) -> Unit) {
    val runId = ScriptRunnerService.start(context, script.lang, script.file)
    onOpenConsole(runId)
}

/** 从系统文件选择器导入脚本：按扩展名识别语言，限制单文件大小 */
private fun importScript(context: Context, uri: Uri, onDone: (LocalScript?) -> Unit) {
    val meta = context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) to (if (cursor.isNull(1)) 0L else cursor.getLong(1)) else null
    }
    val displayName = meta?.first
    val sizeBytes = meta?.second ?: 0L
    val lang = displayName?.let { ScriptLang.fromFileName(it) }
    if (displayName == null || lang == null) {
        Toast.makeText(context, "仅支持导入 .py / .js 文件", Toast.LENGTH_SHORT).show()
        onDone(null)
        return
    }
    if (sizeBytes > MAX_IMPORT_BYTES) {
        Toast.makeText(context, "文件过大（上限 ${MAX_IMPORT_BYTES / 1024 / 1024}MB）", Toast.LENGTH_SHORT).show()
        onDone(null)
        return
    }
    val content = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()
    if (content == null) {
        Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
        onDone(null)
        return
    }
    LocalScriptKit.store.create(displayName, content, lang).fold(
        onSuccess = { file -> onDone(LocalScriptKit.store.list().firstOrNull { it.name == file.name }) },
        onFailure = { e ->
            Toast.makeText(context, e.message ?: "导入失败", Toast.LENGTH_SHORT).show()
            onDone(null)
        }
    )
}

private const val MAX_IMPORT_BYTES = 5L * 1024 * 1024

@Composable
private fun ScriptCard(
    script: LocalScript,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(script.lastModified) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(script.lastModified))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onEdit() },
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (script.lang == ScriptLang.PYTHON) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (script.lang == ScriptLang.PYTHON) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    script.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${script.lang.label} · ${formatSize(script.sizeBytes)} · $timeText",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRun, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "运行", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(12.dp))
        Text("还没有本地脚本", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("点击右上角「新建」或「导入」开始", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
