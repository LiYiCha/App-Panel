package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(
    viewModel: MainViewModel,
    showDirectoryDrawer: Boolean = false,
    onDismissDirectoryDrawer: () -> Unit = {},
    showCreateConfigDialog: Boolean = false,
    onDismissCreateConfigDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeEditingFile by remember { mutableStateOf<String?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var isEditable by remember { mutableStateOf(false) }

    val filteredFiles = remember(searchQuery, uiState.configFiles) {
        if (searchQuery.isEmpty()) uiState.configFiles else {
            uiState.configFiles.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    // 视图模式 1: 编辑特定配置文件
    if (activeEditingFile != null) {
        val editingFile = activeEditingFile!!
        val lineCount = remember(editorContent) { editorContent.lines().size }
        // 行号整体作为一个 Text 渲染，避免为数千行配置（config.sh 常达数千行）逐个组合 Text
        val lineNumbersText = remember(lineCount) { (1..maxOf(lineCount, 1)).joinToString("\n") }
        // 行号与正文必须使用完全一致的 lineHeight，否则行号会与内容逐行错位
        val editorLineHeight = 15.sp
        val editorScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            activeEditingFile = null
                            isEditable = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回列表", modifier = Modifier.size(18.dp))
                    }
                    Text(text = "config / $editingFile", fontSize = 14.sp, style = MaterialTheme.typography.titleMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(editorContent))
                            Toast.makeText(context, "配置全文已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                    }
                    if (!isEditable) {
                        Button(
                            onClick = { isEditable = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("编辑", fontSize = 11.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isEditable = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("取消", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                viewModel.saveConfigFile(editingFile, editorContent)
                                isEditable = false
                                Toast.makeText(context, "[$editingFile] 保存命令已发送！", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("保存", fontSize = 11.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                // 行号与正文共用同一个滚动容器：滚动天然同步，行号严格对齐，
                // 同时为输入区提供"内容高度自适应"的测量环境
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(editorScrollState)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = lineNumbersText,
                            fontSize = 11.sp,
                            lineHeight = editorLineHeight,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(36.dp)
                                .padding(horizontal = 6.dp)
                        )

                        BasicTextField(
                            value = editorContent,
                            onValueChange = { if (isEditable) editorContent = it },
                            readOnly = !isEditable,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = editorLineHeight,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    } else {
        // 视图模式 2: 配置文件目录树与文件列表（对齐青龙/白虎官方文件管理）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索配置文件 (如 config.sh, extra.sh)...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshCurrentPanel() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (uiState.isLoading) "正在同步配置文件树..." else "暂无配置文件", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredFiles) { file ->
                            val isSh = file.endsWith(".sh")
                            val isJson = file.endsWith(".json")
                            val desc = when (file) {
                                "config.sh" -> "青龙核心环境变量与运行脚本主配置文件"
                                "extra.sh" -> "容器重启后自动执行的自定义扩展命令脚本"
                                "config.json" -> "白虎面板主系统与调度器核心配置文件"
                                "config.sample.sh" -> "官方配置样本模板文件"
                                else -> "系统服务端运行配置文件"
                            }

                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // readConfigFile 是异步的，必须走回调取内容，
                                        // 直接读 uiState.configContent 拿到的还是上一次的旧值
                                        viewModel.readConfigFile(file) { content ->
                                            editorContent = content
                                        }
                                        activeEditingFile = file
                                    },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = when {
                                                isSh -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                                isJson -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = when {
                                                        isSh -> Icons.Default.Terminal
                                                        isJson -> Icons.Default.DataObject
                                                        else -> Icons.Default.Description
                                                    },
                                                    contentDescription = null,
                                                    tint = when {
                                                        isSh -> MaterialTheme.colorScheme.secondary
                                                        isJson -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.primary
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(text = file, fontSize = 14.sp, style = MaterialTheme.typography.titleMedium)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isSh) "Shell" else if (isJson) "JSON" else "Conf",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(file))
                                                Toast.makeText(context, "文件名已复制", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                // 异步读取，必须走回调，否则打开的是上一次的旧内容
                                                viewModel.readConfigFile(file) { content ->
                                                    editorContent = content
                                                }
                                                activeEditingFile = file
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showCreateConfigDialog) {
            CreateConfigFileDialog(
                onDismiss = onDismissCreateConfigDialog,
                onConfirm = { fileName, fileContent ->
                    viewModel.saveConfigFile(fileName, fileContent)
                    activeEditingFile = fileName
                    editorContent = fileContent
                    isEditable = true
                    Toast.makeText(context, "配置文件 [$fileName] 已创建并进入编辑", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateConfigFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, content: String) -> Unit
) {
    var rawName by remember { mutableStateOf("") }
    var selectedExt by remember { mutableStateOf(".sh") }
    var selectedTemplateIndex by remember { mutableIntStateOf(0) }

    val extensions = listOf(".sh", ".json", ".conf", ".txt")
    val suggestions = listOf("extra.sh", "task_before.sh", "task_after.sh", "custom.json")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建配置文件", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("为面板创建自定义运行配置或系统扩展脚本：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = rawName,
                    onValueChange = { input ->
                        rawName = input
                        val lower = input.lowercase()
                        val matched = extensions.firstOrNull { lower.endsWith(it) }
                        if (matched != null) selectedExt = matched
                    },
                    label = { Text("配置文件名称 (如 extra.sh)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 推荐常用名称快速点击
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    suggestions.forEach { name ->
                        AssistChip(
                            onClick = {
                                rawName = name
                                val ext = "." + name.substringAfterLast('.')
                                if (ext in extensions) selectedExt = ext
                            },
                            label = { Text(name, fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }

                // 后缀选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("后缀：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    extensions.forEach { ext ->
                        FilterChip(
                            selected = selectedExt == ext,
                            onClick = {
                                selectedExt = ext
                                if (rawName.contains('.')) {
                                    val base = rawName.substringBeforeLast('.')
                                    if (base.isNotBlank()) rawName = "$base$ext"
                                }
                            },
                            label = { Text(ext, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // 初始模版
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("初始模版：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedTemplateIndex == 0,
                            onClick = { selectedTemplateIndex = 0 },
                            label = { Text("空白", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = selectedTemplateIndex == 1,
                            onClick = { selectedTemplateIndex = 1 },
                            label = { Text("Shell 扩展", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = selectedTemplateIndex == 2,
                            onClick = { selectedTemplateIndex = 2 },
                            label = { Text("JSON 配置", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                var clean = rawName.trim()
                if (clean.isEmpty()) clean = "custom"
                val finalName = if (clean.contains('.') && !clean.endsWith('.')) clean else "${clean.trimEnd('.')}$selectedExt"

                val content = when (selectedTemplateIndex) {
                    1 -> "#!/usr/bin/env bash\n## extra.sh / 扩展启动脚本\n## 容器重启时会自动执行此文件\n\n"
                    2 -> "{\n  \"version\": \"1.0.0\",\n  \"custom\": true\n}\n"
                    else -> ""
                }

                onConfirm(finalName, content)
            }) {
                Text("创建并编辑")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
