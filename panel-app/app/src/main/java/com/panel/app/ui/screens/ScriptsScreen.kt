package com.panel.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.ScriptNode
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.components.SearchPillField
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(
    viewModel: MainViewModel,
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    onNavigateToCreateScript: () -> Unit,
    onOpenScriptEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var deletingNode by remember { mutableStateOf<ScriptNode?>(null) }
    var renamingNode by remember { mutableStateOf<ScriptNode?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showNewScriptDialog by remember { mutableStateOf(false) }
    var importedFileName by remember { mutableStateOf("") }
    var importedFileContent by remember { mutableStateOf("") }
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val pickedName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":") ?: "imported_script.js"

            val ext = pickedName.substringAfterLast('.', "").lowercase()
            val binaryExts = setOf("apk", "zip", "rar", "7z", "tar", "gz", "dex", "so", "bin", "exe", "png", "jpg", "jpeg", "webp", "gif", "mp4", "mp3", "pdf")

            if (binaryExts.contains(ext)) {
                Toast.makeText(context, "不支持导入二进制文件 ($ext)，请选择代码文本文件", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            val readContent = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()

            if (readContent == null) {
                Toast.makeText(context, "读取本地文件失败", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            importedFileName = pickedName
            importedFileContent = readContent
            showImportConfirmDialog = true
        }
    }

    var taskAddingNode by remember { mutableStateOf<ScriptNode?>(null) }
    var taskAddingName by remember { mutableStateOf("") }
    var taskAddingCommand by remember { mutableStateOf("") }
    var taskAddingSchedule by remember { mutableStateOf("0 8 * * *") }

    // 搜索过滤与规范排序后的脚本树 (文件夹在上方，最新修改在最前)
    val filteredTree = remember(uiState.scriptTree, searchQuery) {
        sortScriptNodes(filterScriptTree(uiState.scriptTree, searchQuery))
    }

    val visibleFlatNodes = remember(filteredTree, uiState.expandedScriptFolders, searchQuery) {
        flattenScriptTree(filteredTree, uiState.expandedScriptFolders, searchQuery)
    }

    // 当输入搜索词时，自动展开全部匹配节点所在的层级
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            val allDirPaths = mutableSetOf<String>()
            fun collectDirs(nodes: List<ScriptNode>) {
                for (n in nodes) {
                    if (n.isDir) {
                        allDirPaths.add(n.path)
                        n.children?.let { collectDirs(it) }
                    }
                }
            }
            collectDirs(filteredTree)
            viewModel.setScriptFoldersExpanded(allDirPaths)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // 顶部药丸形搜索栏（三页统一组件）
        SearchPillField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "搜索脚本文件或目录名称..."
        )

        // 2. 脚本与目录虚拟列表 (树形结构扁平化，按需复用视口条目，彻底杜绝大量脚本展开引发的 ANR)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refreshScripts() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (visibleFlatNodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "暂无脚本文件" else "无匹配脚本或目录",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(visibleFlatNodes, key = { it.node.path }) { flatNode ->
                        FlatScriptRowItem(
                            flatNode = flatNode,
                            onToggleExpand = { viewModel.toggleScriptFolderExpanded(it) },
                            onOpenEditor = onOpenScriptEditor,
                            onDelete = { deletingNode = it },
                            onRename = { renamingNode = it },
                            onAddToTask = { node ->
                                viewModel.readScript(node.path) { content ->
                                    // parseScriptComment 返回 Pair(名字, cron)，勿反解构
                                    val (parsedName, cron) = parseScriptComment(content, node.name)
                                    taskAddingName = parsedName
                                    taskAddingCommand = "task ${node.path}"
                                    taskAddingSchedule = cron
                                    taskAddingNode = node
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 3. 现代底部新建与导入操作抽屉 (彻底消除多层弹窗套弹窗与繁琐跳转)
    if (showCreateDialog) {
        ModalBottomSheet(
            onDismissRequest = onDismissCreateDialog,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "新建与导入",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismissCreateDialog, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }

                // 卡片 1: 新建脚本文件 (快速后缀选择 + 创建后直接进入全屏编辑器)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismissCreateDialog()
                            showNewScriptDialog = true
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("新建脚本文件", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("快速命名，点选后缀与模板，创建后直接进入全屏编辑器编写", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                }

                // 卡片 2: 导入本地代码
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismissCreateDialog()
                            filePickerLauncher.launch("*/*")
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("导入本地代码", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("一键调起文件管理器，导入 .js / .py / .sh / .ts 并直接打开", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                }

                // 卡片 3: 新建文件夹
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismissCreateDialog()
                            showFolderDialog = true
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("新建文件夹", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("在指定目录下快速创建子目录整理脚本", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (showNewScriptDialog) {
        CreateScriptFastDialog(
            existingDirs = extractDirectories(uiState.scriptTree),
            onDismiss = { showNewScriptDialog = false },
            onConfirm = { fullPath, content ->
                showNewScriptDialog = false
                viewModel.createScript(fullPath, content) { success, msg ->
                    if (success) {
                        Toast.makeText(context, "脚本 [$fullPath] 已创建并打开", Toast.LENGTH_SHORT).show()
                        onOpenScriptEditor(fullPath)
                    } else {
                        Toast.makeText(context, "创建失败: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showImportConfirmDialog) {
        ImportScriptConfirmDialog(
            initialFileName = importedFileName,
            existingDirs = extractDirectories(uiState.scriptTree),
            onDismiss = { showImportConfirmDialog = false },
            onConfirm = { fullPath ->
                showImportConfirmDialog = false
                viewModel.createScript(fullPath, importedFileContent) { success, msg ->
                    if (success) {
                        Toast.makeText(context, "脚本 [$fullPath] 导入成功并打开", Toast.LENGTH_SHORT).show()
                        onOpenScriptEditor(fullPath)
                    } else {
                        Toast.makeText(context, "导入失败: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // 4. 新建文件夹弹窗
    if (showFolderDialog) {
        CreateFolderDialog(
            existingDirs = extractDirectories(uiState.scriptTree),
            onDismiss = { showFolderDialog = false },
            onConfirm = { folderName, parentDir ->
                val fullPath = if (parentDir.isEmpty()) folderName else "$parentDir/$folderName"
                viewModel.createDirectory(fullPath)
                showFolderDialog = false
                Toast.makeText(context, "文件夹 [$fullPath] 已创建", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 4.5 重命名弹窗
    if (renamingNode != null) {
        val node = renamingNode!!
        val parentDir = if (node.path.contains('/')) node.path.substringBeforeLast('/') else ""
        var newName by remember(node.path) { mutableStateOf(node.name) }
        AlertDialog(
            onDismissRequest = { renamingNode = null },
            title = { Text(if (node.isDir) "重命名文件夹" else "重命名脚本", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("所在目录：${parentDir.ifEmpty { "根目录 (/)" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新名称", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "仅支持同目录重命名；跨目录请复制后在目标目录重建。",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty() && trimmed != node.name) {
                            val newPath = if (parentDir.isEmpty()) trimmed else "$parentDir/$trimmed"
                            viewModel.renameScript(node.path, newPath)
                        }
                        renamingNode = null
                    },
                    enabled = newName.trim().isNotEmpty() && newName.trim() != node.name
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingNode = null }) { Text("取消") }
            }
        )
    }

    // 4.6 从脚本快速添加到定时任务弹窗
    if (taskAddingNode != null) {
        val node = taskAddingNode!!
        val cronDesc = remember(taskAddingSchedule) {
            com.panel.app.util.CronExpressionDescriber.describe(taskAddingSchedule)
        }
        AlertDialog(
            onDismissRequest = { taskAddingNode = null },
            title = { Text("添加任务", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("从脚本 [${node.name}] 创建定时任务：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = taskAddingName,
                        onValueChange = { taskAddingName = it },
                        label = { Text("任务名称", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskAddingCommand,
                        onValueChange = { taskAddingCommand = it },
                        label = { Text("执行命令", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskAddingSchedule,
                        onValueChange = { taskAddingSchedule = it },
                        label = { Text("定时规则 (Cron)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (cronDesc.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(cronDesc, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (taskAddingName.isNotBlank() && taskAddingCommand.isNotBlank()) {
                            viewModel.createTask(taskAddingName, taskAddingCommand, taskAddingSchedule)
                            Toast.makeText(context, "已成功添加任务 [$taskAddingName]", Toast.LENGTH_SHORT).show()
                            taskAddingNode = null
                        }
                    },
                    enabled = taskAddingName.isNotBlank() && taskAddingCommand.isNotBlank()
                ) {
                    Text("确认添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskAddingNode = null }) { Text("取消") }
            }
        )
    }

    // 5. 删除文件/文件夹确认弹窗
    if (deletingNode != null) {
        AlertDialog(
            onDismissRequest = { deletingNode = null },
            title = { Text(if (deletingNode!!.isDir) "删除文件夹" else "删除脚本文件", fontSize = 15.sp) },
            text = { Text("确定要删除 [${deletingNode!!.path}] 吗？", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScript(deletingNode!!.path)
                        deletingNode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingNode = null }) { Text("取消") }
            }
        )
    }
}

data class FlatScriptNode(
    val node: ScriptNode,
    val depth: Int,
    val isExpanded: Boolean
)

fun flattenScriptTree(
    nodes: List<ScriptNode>,
    expandedFolders: Set<String>,
    searchQuery: String = "",
    depth: Int = 0
): List<FlatScriptNode> {
    val list = mutableListOf<FlatScriptNode>()
    for (node in nodes) {
        val isExpanded = expandedFolders.contains(node.path) || (searchQuery.isNotEmpty() && node.isDir)
        list.add(FlatScriptNode(node, depth, isExpanded))
        if (node.isDir && isExpanded && !node.children.isNullOrEmpty()) {
            list.addAll(flattenScriptTree(node.children, expandedFolders, searchQuery, depth + 1))
        }
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlatScriptRowItem(
    flatNode: FlatScriptNode,
    onToggleExpand: (String) -> Unit = {},
    onOpenEditor: (String) -> Unit,
    onDelete: (ScriptNode) -> Unit,
    onRename: (ScriptNode) -> Unit = {},
    onAddToTask: (ScriptNode) -> Unit = {}
) {
    val node = flatNode.node
    val level = flatNode.depth
    val isExpanded = flatNode.isExpanded

    // 行内容（目录/文件共用渲染）；仅文件行包右滑删除
    val rowContent: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDir) {
                        onToggleExpand(node.path)
                    } else {
                        onOpenEditor(node.path)
                    }
                },
            shape = RoundedCornerShape(6.dp),
            // 目录行必须不透明：半透明底色会让 SwipeToDismissBox 的红底/恢复残留透出来，
            // 表现为"一行里同时看到侧滑状态与未侧滑状态"
            color = if (node.isDir) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 每级一根竖向引导线（12dp 一格），替代纯缩进，深层目录也能看清归属
                    repeat(level) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = if (node.isDir) {
                            if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
                        } else {
                            when {
                                node.name.endsWith(".py") -> Icons.Default.Code
                                node.name.endsWith(".js") -> Icons.Default.Javascript
                                node.name.endsWith(".sh") -> Icons.Default.Terminal
                                else -> Icons.Default.Description
                            }
                        },
                        contentDescription = null,
                        tint = if (node.isDir) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = node.name,
                        fontSize = 12.sp,
                        fontWeight = if (node.isDir) FontWeight.SemiBold else null,
                        fontFamily = if (node.isDir) FontFamily.Default else FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (node.size != null && !node.isDir) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = node.size,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!node.isDir) {
                        RowActionButton(
                            icon = Icons.Default.AddAlarm,
                            label = "定时",
                            tint = MaterialTheme.colorScheme.secondary,
                            onClick = { onAddToTask(node) }
                        )
                    }
                    RowActionButton(
                        icon = Icons.Default.DriveFileRenameOutline,
                        label = "重命名",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onRename(node) }
                    )
                }
            }
        }
    }

    // 目录与文件统一右滑删除（都有确认弹窗）。
    // 注意 confirmValueChange 必须放行 Settled：若对所有值都返回 false，
    // 状态会永久卡在滑动方向上 —— 这正是之前"没滑也显示删除红底"的根因。
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) {
                true
            } else {
                onDelete(node)
                false
            }
        }
    )
    // 防御：SwipeToDismissBoxState 是 rememberSaveable，LazyColumn 回收/恢复
    // 可能带回 currentValue / targetValue / offset 任意一种残留 —— 只查 currentValue
    // 不够（target 残留会让红底常驻，透过半透明目录行显形，看起来像"侧滑与未侧滑两个状态"）。
    // 组合时无条件归位，对正常行是 no-op。
    LaunchedEffect(node.path) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
            dismissState.targetValue != SwipeToDismissBoxValue.Settled
        ) {
            // snapTo 同时把偏移归零
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // 只在真正滑开（含拖动中）时显示红底。不能用 dismissDirection 判断：
            // Material3 1.3 里它恒为非 null，红底会常驻在行底下，透过半透明文件夹行显形
            val isOpen = dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val color = if (isOpen) MaterialTheme.colorScheme.errorContainer else Color.Transparent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "右滑删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    ) {
        rowContent()
    }
}

/** 树行内「图标 + 下方小字」 */
@Composable
private fun RowActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 8.sp, color = tint)
    }
}

// 递归排序：文件夹在上方（isDir=true 优先），同级按添加/修改时间最新的排在最前（mtime 倒序）
fun sortScriptNodes(nodes: List<ScriptNode>): List<ScriptNode> {
    return nodes.map { node ->
        if (node.children != null) {
            node.copy(children = sortScriptNodes(node.children))
        } else {
            node
        }
    }.sortedWith(
        compareByDescending<ScriptNode> { it.isDir }
            .thenByDescending { it.mtime ?: 0L }
            .thenBy { it.name.lowercase() }
    )
}

// 递归过滤脚本树
fun filterScriptTree(nodes: List<ScriptNode>, query: String): List<ScriptNode> {
    if (query.isBlank()) return nodes
    val result = mutableListOf<ScriptNode>()
    for (node in nodes) {
        if (node.isDir) {
            val filteredChildren = filterScriptTree(node.children ?: emptyList(), query)
            if (filteredChildren.isNotEmpty() || node.name.contains(query, ignoreCase = true)) {
                result.add(node.copy(children = filteredChildren))
            }
        } else {
            if (node.name.contains(query, ignoreCase = true)) {
                result.add(node)
            }
        }
    }
    return result
}

// 提取脚本注释中嵌入的 Cron 与名称 (仅扫描文件头前 8KB，避免大文件扫描造成卡顿或 OOM)
fun parseScriptComment(content: String, fileName: String): Pair<String, String> {
    val defaultName = fileName.substringBeforeLast(".")
    val header = if (content.length > 8192) content.substring(0, 8192) else content
    val cronRegex = Regex("""(?:cron|\bcronexp)\s*[:=]?\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val envRegex = Regex("""new\s+Env\s*\(\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
    
    val parsedCron = cronRegex.find(header)?.groupValues?.get(1) ?: "0 8 * * *"
    val parsedName = envRegex.find(header)?.groupValues?.get(1) ?: defaultName
    return Pair(parsedName, parsedCron)
}

// 递归提取所有已有目录供选择
fun extractDirectories(nodes: List<ScriptNode>): List<String> {
    val dirs = mutableListOf<String>()
    fun traverse(list: List<ScriptNode>) {
        for (item in list) {
            if (item.isDir) {
                dirs.add(item.path)
                item.children?.let { traverse(it) }
            }
        }
    }
    traverse(nodes)
    return dirs
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderDialog(
    existingDirs: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var selectedParentDir by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    val dirOptions = remember(existingDirs) {
        listOf("根目录 (/)" to "") + existingDirs.map { "$it/" to it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称 (例如 scripts / utils)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = it }
                ) {
                    OutlinedTextField(
                        value = dirOptions.firstOrNull { it.second == selectedParentDir }?.first ?: "根目录 (/)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("所在父级目录 (选择已有目录)", fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false }
                    ) {
                        dirOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 12.sp) },
                                onClick = {
                                    selectedParentDir = value
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (folderName.isNotBlank()) {
                    onConfirm(folderName.trim(), selectedParentDir)
                }
            }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScriptFastDialog(
    existingDirs: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (fullPath: String, content: String) -> Unit
) {
    var rawName by remember { mutableStateOf("") }
    var selectedExt by remember { mutableStateOf(".js") }
    var selectedParentDir by remember { mutableStateOf("") }
    var isDirExpanded by remember { mutableStateOf(false) }
    var selectedTemplateIndex by remember { mutableIntStateOf(0) }

    val dirOptions = remember(existingDirs) {
        listOf("根目录 (/)" to "") + existingDirs.map { "$it/" to it }
    }

    val extensions = listOf(".js", ".py", ".sh", ".ts")
    val templateOptions = listOf("空白文件", "标准任务模版", "网络请求模版")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("新建脚本文件", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { input ->
                        rawName = input
                        val lower = input.lowercase()
                        val matchedExt = extensions.firstOrNull { lower.endsWith(it) }
                        if (matchedExt != null) {
                            selectedExt = matchedExt
                        }
                    },
                    label = { Text("脚本名称 (如 my_task)", fontSize = 12.sp) },
                    placeholder = { Text("无需强行输入后缀", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 快速选择后缀 Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("后缀：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    extensions.forEach { ext ->
                        val isSelected = selectedExt == ext
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedExt = ext
                                if (rawName.contains('.')) {
                                    val base = rawName.substringBeforeLast('.')
                                    if (base.isNotBlank()) rawName = "$base$ext"
                                }
                            },
                            label = { Text(ext, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // 目标目录下拉选择
                ExposedDropdownMenuBox(
                    expanded = isDirExpanded,
                    onExpandedChange = { isDirExpanded = it }
                ) {
                    OutlinedTextField(
                        value = dirOptions.firstOrNull { it.second == selectedParentDir }?.first ?: "根目录 (/)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("保存位置", fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDirExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = isDirExpanded,
                        onDismissRequest = { isDirExpanded = false }
                    ) {
                        dirOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 12.sp) },
                                onClick = {
                                    selectedParentDir = value
                                    isDirExpanded = false
                                }
                            )
                        }
                    }
                }

                // 模版选择
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("初始模版：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        templateOptions.forEachIndexed { idx, title ->
                            FilterChip(
                                selected = selectedTemplateIndex == idx,
                                onClick = { selectedTemplateIndex = idx },
                                label = { Text(title, fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var trimmed = rawName.trim()
                    if (trimmed.isEmpty()) trimmed = "script"
                    val finalFileName = if (trimmed.contains('.') && !trimmed.endsWith('.')) trimmed else "${trimmed.trimEnd('.')}$selectedExt"
                    val fullPath = if (selectedParentDir.isEmpty()) finalFileName else "$selectedParentDir/$finalFileName"

                    val content = when (selectedTemplateIndex) {
                        1 -> when (selectedExt) {
                            ".py" -> "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n\"\"\"\nnew Env('定时任务');\ncron: 0 8 * * *\n\"\"\"\n\ndef main():\n    print(\"Hello from Python!\")\n\nif __name__ == '__main__':\n    main()\n"
                            ".sh" -> "#!/usr/bin/env bash\n# new Env('定时任务')\n# cron: 0 8 * * *\n\necho \"Hello from Shell!\"\n"
                            else -> "/**\n * 任务名称: 定时任务\n * cron: 0 8 * * *\n */\n\nconsole.log(\"Hello from Node.js!\");\n"
                        }
                        2 -> when (selectedExt) {
                            ".py" -> "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\nimport urllib.request\nimport json\n\ndef fetch():\n    req = urllib.request.Request(\"https://api.github.com\")\n    with urllib.request.urlopen(req) as resp:\n        print(resp.read().decode('utf-8'))\n\nif __name__ == '__main__':\n    fetch()\n"
                            ".sh" -> "#!/usr/bin/env bash\n\ncurl -s -X GET \"https://api.github.com\"\n"
                            else -> "const https = require('https');\n\nhttps.get('https://api.github.com', (res) => {\n  let data = '';\n  res.on('data', chunk => data += chunk);\n  res.on('end', () => console.log(data));\n});\n"
                        }
                        else -> ""
                    }

                    onConfirm(fullPath, content)
                }
            ) {
                Text("创建并编写")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScriptConfirmDialog(
    initialFileName: String,
    existingDirs: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (fullPath: String) -> Unit
) {
    var fileName by remember(initialFileName) { mutableStateOf(initialFileName) }
    var selectedParentDir by remember { mutableStateOf("") }
    var isDirExpanded by remember { mutableStateOf(false) }

    val dirOptions = remember(existingDirs) {
        listOf("根目录 (/)" to "") + existingDirs.map { "$it/" to it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入本地代码", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("已读取本地代码文件，请确认保存名称及位置：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("脚本文件名", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = isDirExpanded,
                    onExpandedChange = { isDirExpanded = it }
                ) {
                    OutlinedTextField(
                        value = dirOptions.firstOrNull { it.second == selectedParentDir }?.first ?: "根目录 (/)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("保存位置", fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDirExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = isDirExpanded,
                        onDismissRequest = { isDirExpanded = false }
                    ) {
                        dirOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 12.sp) },
                                onClick = {
                                    selectedParentDir = value
                                    isDirExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = fileName.trim().ifEmpty { initialFileName }
                val fullPath = if (selectedParentDir.isEmpty()) clean else "$selectedParentDir/$clean"
                onConfirm(fullPath)
            }) {
                Text("导入并打开")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
