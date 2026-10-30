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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.panel.app.ui.components.SearchPillField
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 脚本 Tab 内容：脚本库按多级文件夹路径树形展示（新建/导入/运行/编辑/移动/删除）。
 * 文件夹为虚拟分组（归属存元数据，文件仍平铺，保证运行时同目录模块解析不变）。
 * 由 ScriptCenterScreen 承载，自身不带页面壳。
 */
@Composable
fun ScriptsTabContent(
    onOpenEditor: (String) -> Unit,
    onOpenConsole: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scripts by remember { mutableStateOf(LocalScriptKit.store.list()) }
    // 文件夹树（深度优先前序，节点含 path/depth/子文件夹/直属脚本）
    var folderTree by remember { mutableStateOf(LocalScriptKit.store.folderTree()) }
    fun refresh() {
        scripts = LocalScriptKit.store.list()
        folderTree = LocalScriptKit.store.folderTree()
    }

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
    var newFolderParent by remember { mutableStateOf("") }
    var deletingFolder by remember { mutableStateOf<String?>(null) }
    // 导入方式选择对话框（单个脚本文件 / 脚本包 zip）
    var showImportDialog by remember { mutableStateOf(false) }
    // 顶部动作菜单（新建/文件夹/导入/导出收敛）
    var actionsMenuOpen by remember { mutableStateOf(false) }
    // 统计详情弹窗（工具栏「统计」触发）
    var showStatsDialog by remember { mutableStateOf(false) }

    // 折叠的文件夹集合（按路径记住，默认全部展开）
    var collapsedFolders by remember { mutableStateOf(setOf<String>()) }
    fun toggleFolder(folder: String) {
        collapsedFolders = if (folder in collapsedFolders) collapsedFolders - folder else collapsedFolders + folder
    }

    // 搜索关键字：非空时按名称过滤并强制展开全部分组
    var query by remember { mutableStateOf("") }
    // 重命名对话框：脚本 / 文件夹
    var renamingScript by remember { mutableStateOf<LocalScript?>(null) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    // 长按拖拽移动：被拖脚本 / 指针根坐标 / 命中文件夹 key / 可见文件夹行矩形 / 列表矩形 / 浮层根偏移
    var draggingScript by remember { mutableStateOf<LocalScript?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dropTargetKey by remember { mutableStateOf<String?>(null) }
    val headerBounds = remember { mutableStateMapOf<String, Rect>() }
    val rowCoords = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayRoot by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()

    fun reportHeaderBounds(key: String, rect: Rect?) {
        if (rect == null) headerBounds.remove(key) else headerBounds[key] = rect
    }

    /** 被拖行内坐标 → 根坐标（行随列表滚动实时更新，行被回收时退回上次结果） */
    fun toRoot(name: String, local: Offset): Offset? =
        rowCoords[name]?.takeIf { it.isAttached }?.localToRoot(local)

    /**
     * 落点命中：优先手指正压住的文件夹行；否则取手指 y 下方最近的可见文件夹行；
     * 下方没有时取上方最近的一行（未分类根区同样参与判定）。
     */
    fun hitFolder(p: Offset): String? {
        headerBounds.entries.firstOrNull { it.value.contains(p) }?.let { return it.key }
        headerBounds.entries.filter { it.value.top >= p.y }
            .minByOrNull { it.value.top }?.let { return it.key }
        return headerBounds.entries.filter { it.value.bottom <= p.y }
            .maxByOrNull { it.value.bottom }?.key
    }

    fun dragModifierFor(script: LocalScript): Modifier = Modifier
        .onGloballyPositioned { rowCoords[script.name] = it }
        .pointerInput(script.name) {
            // 拖拽期间手指接近列表上下边缘自动滚动，越近速度越快（回调非挂起，用协程调 scrollBy）
            val edgeZone = 56.dp.toPx()
            val maxStep = 24.dp.toPx()
            fun autoScroll(p: Offset) {
                val bounds = listBounds ?: return
                val delta = when {
                    p.y < bounds.top -> -maxStep
                    p.y < bounds.top + edgeZone ->
                        -maxStep * ((bounds.top + edgeZone - p.y) / edgeZone).coerceAtLeast(0.2f)
                    p.y > bounds.bottom -> maxStep
                    p.y > bounds.bottom - edgeZone ->
                        maxStep * ((p.y - (bounds.bottom - edgeZone)) / edgeZone).coerceAtLeast(0.2f)
                    else -> 0f
                }
                if (delta != 0f) scope.launch { listState.scrollBy(delta) }
            }
            detectDragGesturesAfterLongPress(
                onDragStart = { start ->
                    draggingScript = script
                    val root = toRoot(script.name, start) ?: start
                    dragPointer = root
                    dropTargetKey = hitFolder(root)
                    autoScroll(root)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val root = toRoot(script.name, change.position) ?: dragPointer
                    dragPointer = root
                    dropTargetKey = hitFolder(root)
                    autoScroll(root)
                },
                onDragEnd = {
                    val dragged = draggingScript
                    val key = dropTargetKey
                    draggingScript = null
                    dropTargetKey = null
                    if (dragged != null && key != null) {
                        val folder = if (key == UNGROUPED_KEY) "" else key
                        if (folder != dragged.folder) {
                            LocalScriptKit.store.moveTo(dragged.name, folder)
                            refresh()
                        }
                    }
                },
                onDragCancel = {
                    draggingScript = null
                    dropTargetKey = null
                }
            )
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
    // 全部文件夹路径（树的前序列表，供各选择器使用）
    val folderPaths = remember(folderTree) { folderTree.map { it.path } }

    // 脚本包 zip 导入：批量导入 + 文件夹归属合并
    val zipImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importScriptsZip(context, uri) { imported, skipped ->
            refresh()
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
    val searching = query.isNotBlank()
    val visibleScripts = remember(scripts, query) {
        val q = query.trim()
        if (q.isEmpty()) scripts else scripts.filter { it.name.contains(q, ignoreCase = true) }
    }
    val visibleUncategorized = remember(visibleScripts) { visibleScripts.filter { it.folder.isBlank() } }

    // 树形行序列：文件夹按深度缩进（每级 14dp），脚本缩进到所属文件夹层级；
    // 搜索时强制展开并隐藏无命中子树；收起父节点时隐藏整棵子树；未分类固定在根层。
    val rowItems = remember(visibleScripts, collapsedFolders, folderTree, searching) {
        val nodeByPath = folderTree.associateBy { it.path }
        fun parentOf(path: String) = path.substringBeforeLast('/', "")
        fun subtreeHasMatch(path: String): Boolean {
            val node = nodeByPath[path] ?: return false
            if (visibleScripts.any { parentOf(it.folder) == path }) return true
            return node.subFolders.any { subtreeHasMatch(it) }
        }
        val rows = mutableListOf<RowItem>()
        fun walk(path: String) {
            val node = nodeByPath[path] ?: return
            if (searching && !subtreeHasMatch(path)) return
            val direct = visibleScripts.filter { parentOf(it.folder) == path }
            val collapsed = !searching && path in collapsedFolders
            rows += RowItem.FolderRow(path, path, node.depth, direct.size, collapsed)
            if (!collapsed) {
                direct.forEach { rows += RowItem.ScriptRow(it, node.depth + 1) }
                node.subFolders.forEach { walk(it) }
            }
        }
        folderTree.filter { it.depth == 0 }.forEach { walk(it.path) }
        if (visibleUncategorized.isNotEmpty()) {
            if (folderTree.isEmpty()) {
                visibleUncategorized.forEach { rows += RowItem.ScriptRow(it, 0) }
            } else {
                val collapsed = !searching && UNGROUPED_KEY in collapsedFolders
                rows += RowItem.FolderRow(UNGROUPED_KEY, "", 0, visibleUncategorized.size, collapsed)
                if (!collapsed) visibleUncategorized.forEach { rows += RowItem.ScriptRow(it, 1) }
            }
        }
        rows
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayRoot = it.localToRoot(Offset.Zero) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // 单行工具栏：搜索框为主体，行尾「统计」按钮（详情弹窗）+ ⋮ 菜单（新建/文件夹/导入/导出）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchPillField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索脚本名称",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { showStatsDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("统计", fontSize = 11.sp)
                }
                Box {
                    IconButton(onClick = { actionsMenuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "操作", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = actionsMenuOpen, onDismissRequest = { actionsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("新建脚本", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                actionsMenuOpen = false
                                showCreateDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("新建文件夹", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                actionsMenuOpen = false
                                showNewFolderDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导入脚本", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                actionsMenuOpen = false
                                showImportDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出脚本包（zip）", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                actionsMenuOpen = false
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                exportLauncher.launch("panelhub_scripts_$stamp.zip")
                            }
                        )
                    }
                }
            }

            if (scripts.isEmpty()) {
                EmptyHint(modifier = Modifier.weight(1f))
            } else if (visibleScripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("无匹配脚本", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { listBounds = it.boundsInRoot() },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        rowItems,
                        key = { row ->
                            when (row) {
                                is RowItem.FolderRow -> "folder_${row.key}"
                                is RowItem.ScriptRow -> "script_${row.script.name}"
                            }
                        }
                    ) { row ->
                        when (row) {
                            is RowItem.FolderRow -> Box(Modifier.padding(start = (row.depth * 14).dp)) {
                                FolderHeader(
                                    folderKey = row.key,
                                    folder = row.path,
                                    count = row.count,
                                    collapsed = row.collapsed,
                                    highlight = dropTargetKey == row.key,
                                    onBounds = ::reportHeaderBounds,
                                    onToggle = { toggleFolder(row.key) },
                                    onDelete = if (row.key == UNGROUPED_KEY) null
                                    else ({ deletingFolder = row.path }),
                                    onRename = if (row.key == UNGROUPED_KEY) null
                                    else ({ renamingFolder = row.path })
                                )
                            }
                            is RowItem.ScriptRow -> Box(Modifier.padding(start = (row.depth * 14).dp)) {
                                ScriptCard(
                                    script = row.script,
                                    isDragging = draggingScript?.name == row.script.name,
                                    dragModifier = dragModifierFor(row.script),
                                    onEdit = { onOpenEditor(row.script.name) },
                                    onRun = {
                                        if (Build.VERSION.SDK_INT >= 33 &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            pendingRunScript = row.script
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            startRun(context, row.script, onOpenConsole)
                                        }
                                    },
                                    onRename = { renamingScript = row.script },
                                    onDelete = { deletingScript = row.script },
                                    onMove = { movingScript = row.script }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 拖拽期间跟随手指的浮层小片；原行半透明占位
        draggingScript?.let { s ->
            Surface(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .offset {
                        IntOffset(
                            (dragPointer.x - overlayRoot.x).roundToInt() + 24,
                            (dragPointer.y - overlayRoot.y).roundToInt() - 20
                        )
                    },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    // 统计详情弹窗：打开时读取当前最新状态逐行展示
    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = { Text("统计", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("脚本 ${scripts.size} 个", fontSize = 12.sp)
                    Text("文件夹 ${folderTree.size} 个", fontSize = 12.sp)
                    Text("未分类 ${visibleUncategorized.size} 个", fontSize = 12.sp)
                    Text("总容量 ${formatSize(totalSize)}", fontSize = 12.sp)
                    if (searching) {
                        Text("搜索命中 ${visibleScripts.size} 个", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) { Text("确定", fontSize = 12.sp) }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建脚本", fontSize = 15.sp) },
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
                        folders = folderPaths,
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

    // 移动到文件夹对话框（带缩进的层级路径列表）
    movingScript?.let { script ->
        var target by remember(script.name) { mutableStateOf(script.folder) }
        AlertDialog(
            onDismissRequest = { movingScript = null },
            title = { Text("移动 ${script.name}", fontSize = 15.sp) },
            text = {
                PathOptionList(
                    selected = target,
                    paths = folderPaths,
                    rootLabel = "未分类",
                    onSelect = { target = it }
                )
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

    // 脚本重命名对话框
    renamingScript?.let { script ->
        var newName by remember(script.name) { mutableStateOf(script.name) }
        AlertDialog(
            onDismissRequest = { renamingScript = null },
            title = { Text("重命名脚本", fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新文件名（扩展名需与类型一致）", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalScriptKit.store.rename(script.name, newName).onSuccess {
                        renamingScript = null
                        refresh()
                    }.onFailure { e ->
                        Toast.makeText(context, e.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renamingScript = null }) { Text("取消") }
            }
        )
    }

    // 文件夹重命名对话框：名称 + 带缩进的层级路径列表（选择新父目录，默认原位置）
    renamingFolder?.let { folder ->
        var newName by remember(folder) { mutableStateOf(folder.substringAfterLast('/')) }
        var newParent by remember(folder) { mutableStateOf(folder.substringBeforeLast('/', "")) }
        AlertDialog(
            onDismissRequest = { renamingFolder = null },
            title = { Text("重命名文件夹", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新文件夹名称", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "所在目录（可改父目录，子文件夹一并迁移）",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PathOptionList(
                        selected = newParent,
                        paths = folderPaths.filter { it != folder && !it.startsWith("$folder/") },
                        rootLabel = "根目录",
                        onSelect = { newParent = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val newPath = if (newParent.isEmpty()) newName.trim() else "$newParent/${newName.trim()}"
                        LocalScriptKit.store.renameFolder(folder, newPath).onSuccess {
                            renamingFolder = null
                            refresh()
                        }.onFailure { e ->
                            Toast.makeText(context, e.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renamingFolder = null }) { Text("取消") }
            }
        )
    }

    // 新建文件夹对话框：名称 + 父目录选择（默认根目录）
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("新建文件夹", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("文件夹名称", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("父目录", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PathOptionList(
                        selected = newFolderParent,
                        paths = folderPaths,
                        rootLabel = "根目录",
                        onSelect = { newFolderParent = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newFolderName.isNotBlank(),
                    onClick = {
                        LocalScriptKit.store.createFolder(newFolderName, newFolderParent).onSuccess {
                            showNewFolderDialog = false
                            newFolderName = ""
                            newFolderParent = ""
                            refresh()
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

    // 删除文件夹确认：整棵子树删除，树下脚本移回未分类
    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("删除文件夹", fontSize = 15.sp) },
            text = { Text("删除「$folder」及其子文件夹？树下脚本将移回未分类，不会删除脚本文件。", fontSize = 12.sp) },
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
                        subtitle = "批量导入，同名脚本将被覆盖",
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

/** 列表行：文件夹节点（key 为路径，未分类为 UNGROUPED_KEY）或脚本卡片 */
private sealed interface RowItem {
    data class FolderRow(
        val key: String,
        val path: String,
        val depth: Int,
        val count: Int,
        val collapsed: Boolean
    ) : RowItem

    data class ScriptRow(val script: LocalScript, val depth: Int) : RowItem
}

/** 文件夹分组头：图标 + 名称 + 数量 + ⋮（重命名）+ 删除 + 折叠；highlight 为拖拽命中态 */
@Composable
private fun FolderHeader(
    folderKey: String,
    folder: String,
    count: Int,
    collapsed: Boolean,
    highlight: Boolean,
    onBounds: (String, Rect?) -> Unit,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    onRename: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }
    // 拖拽命中区域登记：组头离开组合时清除
    DisposableEffect(folderKey) {
        onDispose { onBounds(folderKey, null) }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBounds(folderKey, it.boundsInRoot()) }
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(8.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                folder.ifBlank { "未分类" },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            onRename?.let { rename ->
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "文件夹操作",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名文件夹", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuOpen = false
                                rename()
                            }
                        )
                    }
                }
            }
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

/** 带缩进的层级路径单选列表（根目录/未分类置顶，按路径深度缩进） */
@Composable
private fun PathOptionList(
    selected: String,
    paths: List<String>,
    rootLabel: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PathOptionRow(
            label = rootLabel,
            depth = 0,
            selected = selected.isEmpty(),
            onClick = { onSelect("") }
        )
        paths.forEach { path ->
            PathOptionRow(
                label = path,
                depth = path.count { it == '/' },
                selected = selected == path,
                onClick = { onSelect(path) }
            )
        }
    }
}

/** 路径选项行：单选圆点 + 缩进路径文本 */
@Composable
private fun PathOptionRow(
    label: String,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(start = (depth * 14).dp)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
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

/** 紧凑脚本行：类型小图标 + 名称 + 大小 + ⋮ 菜单；点击=编辑，长按=拖拽移动 */
@Composable
private fun ScriptCard(
    script: LocalScript,
    isDragging: Boolean,
    dragModifier: Modifier,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDragging) 0.3f else 1f)
            .then(dragModifier)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onEdit() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (script.lang == ScriptLang.PYTHON) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (script.lang == ScriptLang.PYTHON) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                script.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatSize(script.sizeBytes),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("运行", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            menuOpen = false
                            onRun()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("编辑", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuOpen = false
                            onRename()
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
        Text("暂无脚本", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("点右上角菜单可新建或导入脚本", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Text(
            "文件夹仅归类展示，不影响脚本运行与模块引用；长按行可拖拽到分组，或点卡片菜单移动",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
