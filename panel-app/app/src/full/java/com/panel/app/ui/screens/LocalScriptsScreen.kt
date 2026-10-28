package com.panel.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.panel.corescript.store.LocalScriptStore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 脚本 Tab 内容：脚本库按文件夹分组展示（新建/导入/运行/编辑/移动/删除）。
 * 文件夹为虚拟分组（归属存元数据，文件仍平铺，保证运行时同目录模块解析不变）。
 * 由 ScriptCenterScreen 承载，自身不带页面壳。
 */
@Composable
fun ScriptsTabContent(
    onOpenEditor: (String) -> Unit,
    onOpenConsole: (String) -> Unit
) {
    val context = LocalContext.current

    var scripts by remember { mutableStateOf(LocalScriptKit.store.list()) }
    fun refresh() { scripts = LocalScriptKit.store.list() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var createLang by remember { mutableStateOf(ScriptLang.PYTHON) }
    var createName by remember { mutableStateOf("") }
    var createFolder by remember { mutableStateOf("") }
    var deletingScript by remember { mutableStateOf<LocalScript?>(null) }
    // 移动目标脚本（弹出选择文件夹对话框）
    var movingScript by remember { mutableStateOf<LocalScript?>(null) }
    // 新建/删除文件夹对话框
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var deletingFolder by remember { mutableStateOf<String?>(null) }
    // 导入方式选择对话框（单个脚本文件 / 脚本包 zip）
    var showImportDialog by remember { mutableStateOf(false) }

    // 折叠的文件夹集合（默认全部展开）
    var collapsedFolders by remember { mutableStateOf(setOf<String>()) }
    fun toggleFolder(folder: String) {
        collapsedFolders = if (folder in collapsedFolders) collapsedFolders - folder else collapsedFolders + folder
    }

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

    val totalSize = remember(scripts) { scripts.sumOf { it.sizeBytes } }
    var folderNames by remember { mutableStateOf(LocalScriptKit.store.folders()) }
    fun refreshAll() {
        scripts = LocalScriptKit.store.list()
        folderNames = LocalScriptKit.store.folders()
    }

    // 脚本包 zip 导入：批量导入 + 文件夹归属合并
    val zipImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importScriptsZip(context, uri) { imported, skipped ->
            refreshAll()
            val msg = buildString {
                append("已导入 $imported 个脚本")
                if (skipped > 0) append("，跳过 $skipped 项")
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 脚本包 zip 导出：SAF 保存对话框
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) exportScriptsZip(context, uri)
    }
    val uncategorized = remember(scripts) { scripts.filter { it.folder.isBlank() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // 汇总卡片：图标 tile + 数量统计 + 动作按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("脚本库", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${scripts.size} 个脚本 · ${folderNames.size} 个文件夹 · 共 ${formatSize(totalSize)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ActionButtonSmall(
                    icon = Icons.Default.CreateNewFolder,
                    label = "文件夹",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = { showNewFolderDialog = true }
                )
                Spacer(Modifier.width(8.dp))
                ActionButtonSmall(
                    icon = Icons.Default.PostAdd,
                    label = "导入",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = { showImportDialog = true }
                )
                Spacer(Modifier.width(8.dp))
                ActionButtonSmall(
                    icon = Icons.Default.FileDownload,
                    label = "导出",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        exportLauncher.launch("panelhub_scripts_$stamp.zip")
                    }
                )
                Spacer(Modifier.width(8.dp))
                ActionButtonSmall(
                    icon = Icons.Default.Add,
                    label = "新建",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showCreateDialog = true }
                )
            }
        }

        if (scripts.isEmpty()) {
            EmptyHint(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // 文件夹分组
                folderNames.filter { it.isNotBlank() }.forEach { folder ->
                    val groupScripts = scripts.filter { it.folder == folder }
                    val collapsed = folder in collapsedFolders
                    item(key = "folder_$folder") {
                        FolderHeader(
                            folder = folder,
                            count = groupScripts.size,
                            collapsed = collapsed,
                            onToggle = { toggleFolder(folder) },
                            onDelete = { deletingFolder = folder }
                        )
                    }
                    if (!collapsed) {
                        items(groupScripts, key = { "f_${folder}_${it.name}" }) { script ->
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
                                onDelete = { deletingScript = script },
                                onMove = { movingScript = script }
                            )
                        }
                    }
                }
                // 未分类
                if (uncategorized.isNotEmpty()) {
                    if (folderNames.isNotEmpty()) {
                        item(key = "folder_uncat") {
                            FolderHeader(
                                folder = "",
                                count = uncategorized.size,
                                collapsed = UNGROUPED_KEY in collapsedFolders,
                                onToggle = { toggleFolder(UNGROUPED_KEY) },
                                onDelete = null
                            )
                        }
                    }
                    if (folderNames.isEmpty() || UNGROUPED_KEY !in collapsedFolders) {
                        items(uncategorized, key = { "u_${it.name}" }) { script ->
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
                                onDelete = { deletingScript = script },
                                onMove = { movingScript = script }
                            )
                        }
                    }
                }
            }
        }

        Text(
            "点卡片菜单可移动到文件夹；文件夹仅归类展示，不影响脚本运行与模块引用。",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }

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
                    FolderPickerField(
                        selectedFolder = createFolder,
                        folders = folderNames,
                        onSelect = { createFolder = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalScriptKit.store.create(createName, "", createLang).onSuccess { file ->
                        if (createFolder.isNotBlank()) {
                            LocalScriptKit.store.moveTo(file.name, createFolder)
                        }
                        showCreateDialog = false
                        createName = ""
                        createFolder = ""
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

    // 移动到文件夹对话框
    movingScript?.let { script ->
        var target by remember(script.name) { mutableStateOf(script.folder) }
        AlertDialog(
            onDismissRequest = { movingScript = null },
            title = { Text("移动 ${script.name}", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderPickerField(
                        selectedFolder = target,
                        folders = folderNames,
                        onSelect = { target = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalScriptKit.store.moveTo(script.name, target)
                    movingScript = null
                    refresh()
                }) { Text("移动") }
            },
            dismissButton = {
                TextButton(onClick = { movingScript = null }) { Text("取消") }
            }
        )
    }

    // 新建文件夹对话框
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("新建文件夹", fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("文件夹名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newFolderName.isNotBlank(),
                    onClick = {
                        LocalScriptKit.store.createFolder(newFolderName).onSuccess {
                            showNewFolderDialog = false
                            newFolderName = ""
                            refreshAll()
                        }.onFailure { e ->
                            Toast.makeText(context, e.message ?: "创建失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("取消") }
            }
        )
    }

    // 删除文件夹确认：组内脚本移回未分类
    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("删除文件夹", fontSize = 15.sp) },
            text = { Text("删除「$folder」？组内脚本将移回未分类，不会删除脚本文件。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    LocalScriptKit.store.deleteFolder(folder)
                    deletingFolder = null
                    refresh()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) { Text("取消") }
            }
        )
    }

    // 删除脚本确认
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

    // 导入方式选择：单个脚本文件 / 脚本包 zip
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入脚本", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportOptionRow(
                        icon = Icons.Default.Description,
                        title = "脚本文件",
                        subtitle = "单个 .py / .js 文件",
                        onClick = {
                            showImportDialog = false
                            importLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    ImportOptionRow(
                        icon = Icons.Default.FolderZip,
                        title = "脚本包（zip）",
                        subtitle = "批量导入，含文件夹归属；同名脚本将被覆盖",
                        onClick = {
                            showImportDialog = false
                            zipImportLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }
}

private const val UNGROUPED_KEY = "__ungrouped__"

/** 文件夹分组头：图标 + 名称 + 数量 + 折叠 + 删除（未分类组不可删） */
@Composable
private fun FolderHeader(
    folder: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                folder.ifBlank { "未分类" },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$count",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
            Spacer(Modifier.weight(1f))
            onDelete?.let {
                IconButton(onClick = it, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除文件夹",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 文件夹选择字段：现有文件夹单选（含未分类），支持内联输入新名称 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderPickerField(
    selectedFolder: String,
    folders: List<String>,
    onSelect: (String) -> Unit
) {
    var addingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (addingNew) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新文件夹名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onSelect(newName.trim())
                        addingNew = false
                        newName = ""
                    }
                ) { Text("确定") }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedFolder.isBlank(),
                    onClick = { onSelect("") },
                    label = { Text("未分类", fontSize = 11.sp) }
                )
                folders.filter { it.isNotBlank() }.forEach { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { onSelect(folder) },
                        label = { Text(folder, fontSize = 11.sp) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { addingNew = true },
                    label = { Text("+ 新文件夹", fontSize = 11.sp) }
                )
            }
        }
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

/** 导出全部脚本为 zip：脚本文件 + .folders.json（文件夹归属元数据） */
private fun exportScriptsZip(context: Context, uri: Uri) {
    val scripts = LocalScriptKit.store.list()
    if (scripts.isEmpty()) {
        Toast.makeText(context, "没有可导出的脚本", Toast.LENGTH_SHORT).show()
        return
    }
    val count = runCatching {
        val out = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法写入所选位置")
        out.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                scripts.forEach { script ->
                    zip.putNextEntry(ZipEntry(script.name))
                    script.file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                val metaJson = LocalScriptKit.store.exportMetaJson()
                if (metaJson.isNotBlank()) {
                    zip.putNextEntry(ZipEntry(LocalScriptStore.META_FILE))
                    zip.write(metaJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
        scripts.size
    }.fold(
        onSuccess = { it },
        onFailure = { e ->
            Toast.makeText(context, "导出失败: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            -1
        }
    )
    if (count >= 0) Toast.makeText(context, "已导出 $count 个脚本", Toast.LENGTH_SHORT).show()
}

/**
 * 从 zip 导入脚本包：条目平铺取文件名、同名覆盖、非脚本条目跳过，
 * .folders.json 中的文件夹归属经 mergeFolders 合并。
 * basename 平铺 + normalizeName 校验双保险，天然规避 zip-slip 路径穿越。
 */
private fun importScriptsZip(context: Context, uri: Uri, onDone: (Int, Int) -> Unit) {
    var imported = 0
    var skipped = 0
    val folderEntries = mutableMapOf<String, String>()
    val ok = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取所选文件")
        input.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        // 同时处理 / 与 \ 分隔，只取末段文件名
                        val base = entry.name.substringAfterLast('/').substringAfterLast('\\')
                        if (base == LocalScriptStore.META_FILE) {
                            parseFolderMap(zip.readBytes().toString(Charsets.UTF_8))
                                .forEach { (name, folder) -> folderEntries[name] = folder }
                        } else {
                            val lang = ScriptLang.fromFileName(base)
                            val normalized = lang?.let { LocalScriptStore.normalizeName(base, it) }
                            if (normalized == null) {
                                skipped++
                            } else {
                                val content = readBounded(zip)
                                if (content == null) {
                                    skipped++
                                } else {
                                    val file = LocalScriptKit.store.fileFor(normalized)
                                    val result = if (file.exists()) {
                                        runCatching { file.writeText(content) }
                                    } else {
                                        LocalScriptKit.store.create(normalized, content, lang).map { }
                                    }
                                    result.onSuccess { imported++ }.onFailure { skipped++ }
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }.isSuccess
    if (!ok) {
        Toast.makeText(context, "读取脚本包失败", Toast.LENGTH_SHORT).show()
        onDone(0, 0)
        return
    }
    // 文件全部落盘后再合并归属：本次导入与已存在的同名脚本都生效
    if (folderEntries.isNotEmpty()) LocalScriptKit.store.mergeFolders(folderEntries)
    onDone(imported, skipped)
}

/** 限量读取当前 entry 文本内容；超出上限返回 null（多余字节由 closeEntry 跳过） */
private fun readBounded(zip: ZipInputStream): String? {
    val buffer = ByteArray(8192)
    var total = 0
    val out = ByteArrayOutputStream()
    while (true) {
        val n = zip.read(buffer)
        if (n <= 0) break
        total += n
        if (total > MAX_IMPORT_BYTES) return null
        out.write(buffer, 0, n)
    }
    return out.toString("UTF-8")
}

/** 解析 zip 内的 .folders.json：兼容新旧格式，键按语言规范化后返回 脚本名→文件夹 */
private fun parseFolderMap(json: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val obj = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return result
    fun putEntry(key: String, folder: String) {
        val lang = ScriptLang.fromFileName(key) ?: return
        val normalized = LocalScriptStore.normalizeName(key, lang) ?: return
        if (folder.isNotBlank()) result[normalized] = folder
    }
    val mapObj = obj.optJSONObject("map")
    if (mapObj != null) {
        // 新格式 {folders:[], map:{script: folder}}
        mapObj.names()?.let { names ->
            for (i in 0 until names.length()) {
                val k = names.getString(i)
                putEntry(k, mapObj.optString(k, ""))
            }
        }
    } else {
        // 旧版 flat 格式 {script: folder}
        obj.names()?.let { names ->
            for (i in 0 until names.length()) {
                val k = names.getString(i)
                putEntry(k, obj.optString(k, ""))
            }
        }
    }
    return result
}

/** 导入方式选项行（对话框内） */
@Composable
private fun ImportOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScriptCard(
    script: LocalScript,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    val timeText = remember(script.lastModified) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(script.lastModified))
    }
    var menuOpen by remember { mutableStateOf(false) }
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
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移动到文件夹", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuOpen = false
                            onMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
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
