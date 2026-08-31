package com.panel.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.ScriptNode
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(
    viewModel: MainViewModel,
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    onNavigateToCreateScript: () -> Unit,
    onOpenScriptEditor: (String) -> Unit,
    onUploadScript: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var deletingNode by remember { mutableStateOf<ScriptNode?>(null) }
    var renamingNode by remember { mutableStateOf<ScriptNode?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }

    // 搜索过滤与规范排序后的脚本树 (文件夹在上方，最新修改在最前)
    val filteredTree = remember(uiState.scriptTree, searchQuery) {
        sortScriptNodes(filterScriptTree(uiState.scriptTree, searchQuery))
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 顶部满宽搜索框 (实时检索脚本与文件夹)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索脚本文件或目录名称...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(14.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            maxLines = 1
        )

        // 2. 脚本与目录树形列表 (带下拉手势刷新，进入二级页面返回进度不丢失)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refreshCurrentPanel() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredTree.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "暂无脚本文件 (下拉可刷新)" else "未匹配到相关脚本或目录",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredTree, key = { it.path }) { rootNode ->
                        ScriptTreeItem(
                            node = rootNode,
                            level = 0,
                            searchQuery = searchQuery,
                            expandedFolders = uiState.expandedScriptFolders,
                            onToggleExpand = { viewModel.toggleScriptFolderExpanded(it) },
                            onOpenEditor = onOpenScriptEditor,
                            onDelete = { deletingNode = it },
                            onRename = { renamingNode = it }
                        )
                    }
                }
            }
        }
    }

    // 3. 整合式新建/上传模态框 (现代极简卡片视觉，整合新建脚本、新建文件夹与从本地上传脚本)
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateDialog,
            title = { Text("脚本管理操作", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissCreateDialog()
                                onNavigateToCreateScript()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Column {
                                Text("新建脚本文件", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("在线编写 Python、Node.js 或 Shell 代码", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissCreateDialog()
                                showFolderDialog = true
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                            Column {
                                Text("新建文件夹", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("创建子目录用于对各类脚本文件归类", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissCreateDialog) { Text("取消") }
            }
        )
    }

    // 4. 新建文件夹弹窗 (满足用户：点击文件夹是弹窗，显示新增的文件夹和所在目录，可以选择)
    if (showFolderDialog) {
        CreateFolderStandaloneDialog(
            existingDirs = extractAllDirectories(uiState.scriptTree),
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
        var newName by remember(node.path) { mutableStateOf(node.name) }
        AlertDialog(
            onDismissRequest = { renamingNode = null },
            title = { Text(if (node.isDir) "重命名文件夹" else "重命名脚本", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("所在目录：${node.path.substringBeforeLast('/').ifEmpty { "根目录 (/)" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新名称", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 青龙的 rename 接口不允许跨目录，这里只允许改文件名
                    Text(
                        "仅支持同目录内重命名；如需移动到其他目录请先复制后在目标目录重建。",
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
                            val newPath = node.path.substringBeforeLast('/') + "/" + trimmed
                            viewModel.renameScript(node.path, newPath.removePrefix("/"))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptTreeItem(
    node: ScriptNode,
    level: Int,
    searchQuery: String = "",
    expandedFolders: Set<String> = emptySet(),
    onToggleExpand: (String) -> Unit = {},
    onOpenEditor: (String) -> Unit,
    onDelete: (ScriptNode) -> Unit,
    onRename: (ScriptNode) -> Unit = {}
) {
    val isExpanded = expandedFolders.contains(node.path) || (searchQuery.isNotEmpty() && node.isDir)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(node)
                false
            } else false
        }
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color = if (dismissState.dismissDirection != null) MaterialTheme.colorScheme.errorContainer else Color.Transparent
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
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (level * 16 + 8).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
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
                            tint = if (node.isDir) Color(0xFFFFA000) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = node.name,
                            fontSize = 13.sp,
                            fontFamily = if (node.isDir) FontFamily.Default else FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
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

                    IconButton(
                        onClick = { onRename(node) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "重命名", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (node.isDir && isExpanded && node.children != null) {
            node.children.forEach { child ->
                ScriptTreeItem(
                    node = child,
                    level = level + 1,
                    searchQuery = searchQuery,
                    expandedFolders = expandedFolders,
                    onToggleExpand = onToggleExpand,
                    onOpenEditor = onOpenEditor,
                    onDelete = onDelete,
                    onRename = onRename
                )
            }
        }
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

// 提取脚本注释中嵌入的 Cron 与名称
fun parseScriptCommentInfo(content: String, fileName: String): Pair<String, String> {
    val defaultName = fileName.substringBeforeLast(".")
    val cronRegex = Regex("""(?:cron|\bcronexp)\s*[:=]?\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val envRegex = Regex("""new\s+Env\s*\(\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
    
    val parsedCron = cronRegex.find(content)?.groupValues?.get(1) ?: "0 8 * * *"
    val parsedName = envRegex.find(content)?.groupValues?.get(1) ?: defaultName
    return Pair(parsedName, parsedCron)
}

// 新建模态框：支持【新建文件】、【新建文件夹】、【上传脚本】并自动解析 Cron 规则
@Composable
fun CreateScriptOrFolderDialog(
    existingDirs: List<String>,
    onDismiss: () -> Unit,
    onConfirmFile: (path: String, content: String, taskInfo: Triple<String, String, String>?) -> Unit,
    onConfirmDirectory: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf(0) } // 0: 新建文件, 1: 新建文件夹, 2: 上传脚本
    var name by remember { mutableStateOf("") }
    var parentDir by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    var alsoAddTask by remember { mutableStateOf(false) }
    var taskName by remember { mutableStateOf("") }
    var taskCommand by remember { mutableStateOf("") }
    var taskCron by remember { mutableStateOf("0 8 * * *") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val input = context.contentResolver.openInputStream(it)
                val text = input?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                val pickedName = it.lastPathSegment?.substringAfterLast('/') ?: "uploaded.js"
                name = pickedName
                content = text

                val (pName, pCron) = parseScriptCommentInfo(text, pickedName)
                taskName = pName
                taskCron = pCron
                taskCommand = "task $pickedName"
                alsoAddTask = true
                Toast.makeText(context, "脚本文件读取成功，已自动解析定时规则", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "读取脚本文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(content, name) {
        if (content.isNotBlank() && selectedType != 1) {
            val (pName, pCron) = parseScriptCommentInfo(content, name.ifEmpty { "task.js" })
            if (taskName.isEmpty()) taskName = pName
            if (taskCron == "0 8 * * *") taskCron = pCron
            taskCommand = "task ${if (parentDir.isNotBlank()) "$parentDir/$name" else name}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (selectedType) {
                    0 -> "新建脚本文件"
                    1 -> "新建文件夹"
                    else -> "上传本地脚本"
                },
                fontSize = 15.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabRow(selectedTabIndex = selectedType, modifier = Modifier.fillMaxWidth()) {
                    Tab(
                        selected = selectedType == 0,
                        onClick = { selectedType = 0 },
                        text = { Text("新建文件", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedType == 1,
                        onClick = { selectedType = 1 },
                        text = { Text("新建文件夹", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedType == 2,
                        onClick = {
                            selectedType = 2
                            filePickerLauncher.launch("*/*")
                        },
                        text = { Text("上传脚本", fontSize = 11.sp) }
                    )
                }

                if (selectedType == 0 || selectedType == 2) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("文件名称 (如 test.js / daily.py)", fontSize = 11.sp) },
                        placeholder = { Text("main.py") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = parentDir,
                        onValueChange = { parentDir = it },
                        label = { Text("所属父级目录 (可选)", fontSize = 11.sp) },
                        placeholder = { Text("留空为根目录") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selectedType == 0) {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("初始文件内容 (可选)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 100.dp)
                        )
                    }

                    // 自动解析与添加为定时任务开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { alsoAddTask = !alsoAddTask }
                    ) {
                        Checkbox(checked = alsoAddTask, onCheckedChange = { alsoAddTask = it })
                        Text("同时添加为定时任务 (自动解析 Cron)", fontSize = 11.sp)
                    }

                    if (alsoAddTask) {
                        OutlinedTextField(
                            value = taskName,
                            onValueChange = { taskName = it },
                            label = { Text("任务名称", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = taskCron,
                            onValueChange = { taskCron = it },
                            label = { Text("Cron 定时规则", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("文件夹名称 (如 utils / jd_task)", fontSize = 11.sp) },
                        placeholder = { Text("my_folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = parentDir,
                        onValueChange = { parentDir = it },
                        label = { Text("父级目录路径 (可选)", fontSize = 11.sp) },
                        placeholder = { Text("留空为根目录") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotEmpty()) {
                    val fullPath = if (parentDir.trim().isNotEmpty()) "${parentDir.trim().trimEnd('/')}/${name.trim().trimStart('/')}" else name.trim()
                    if (selectedType == 0 || selectedType == 2) {
                        val taskInfo = if (alsoAddTask) {
                            Triple(
                                taskName.ifEmpty { name.substringBeforeLast('.') },
                                taskCommand.ifEmpty { "task $fullPath" },
                                taskCron.ifEmpty { "0 8 * * *" }
                            )
                        } else null
                        onConfirmFile(fullPath, content, taskInfo)
                    } else {
                        onConfirmDirectory(fullPath)
                    }
                }
            }) {
                Text(if (selectedType == 1) "创建文件夹" else "确认提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// 递归提取所有已有目录供选择
fun extractAllDirectories(nodes: List<ScriptNode>): List<String> {
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
fun CreateFolderStandaloneDialog(
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
                        modifier = Modifier.fillMaxWidth().menuAnchor()
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
