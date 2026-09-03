package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.UnifiedEnv
import com.panel.app.data.parser.UniversalEnvParser
import com.google.gson.Gson
import com.panel.app.ui.viewmodel.MainViewModel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvsScreen(
    viewModel: MainViewModel,
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    showImportDialog: Boolean = false,
    onDismissImportDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val envList = uiState.envs

    var searchQuery by remember { mutableStateOf("") }
    var editingEnv by remember { mutableStateOf<UnifiedEnv?>(null) }
    var subItemEditingEnv by remember { mutableStateOf<UnifiedEnv?>(null) }
    var deletingEnv by remember { mutableStateOf<UnifiedEnv?>(null) }
    var exportingEnvIds by remember { mutableStateOf<List<String>?>(null) }
    var sortedEnvs by remember(envList) { mutableStateOf(envList.toList()) }
    var dragOverIndex by remember { mutableStateOf<Int?>(null) }

    val fetchScope = rememberCoroutineScope()

    LaunchedEffect(showCreateDialog) {
        if (showCreateDialog) {
            editingEnv = UnifiedEnv("new_" + System.currentTimeMillis(), "", "", "")
            onDismissCreateDialog()
        }
    }

    val filteredEnvs = remember(searchQuery, envList) {
        if (searchQuery.isEmpty()) envList
        else envList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            (it.remarks?.contains(searchQuery, ignoreCase = true) == true) ||
            it.value.contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedEnvs = remember(uiState.envs) { uiState.envs.filter { it.selected } }
    val allSelected = remember(filteredEnvs, selectedEnvs) { filteredEnvs.isNotEmpty() && filteredEnvs.all { it.selected } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索变量名称或备注...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            maxLines = 1
        )

        // 顶部批量操作栏 (置于顶部，绝不占用主体列表空间)
        if (uiState.isEnvBatchMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { viewModel.selectAllEnvs(it) },
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (selectedEnvs.isEmpty()) "全选" else "已选 ${selectedEnvs.size}",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val ids = selectedEnvs.map { it.id }
                        IconButton(onClick = { viewModel.batchToggleEnvs(ids, true) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "启用", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.batchToggleEnvs(ids, false) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "禁用", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.batchDeleteEnvs(ids) }, enabled = ids.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { exportingEnvIds = ids },
                            enabled = ids.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "导出 JSON", tint = if (ids.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshEnvs() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredEnvs.isEmpty()) {
                    // 空状态必须自己可滚动：PullToRefreshBox 依赖嵌套滚动分发，
                    // 内容不可滚动时下拉手势产生不了滚动增量，列表为空就刷不动
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DataObject, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (searchQuery.isEmpty()) "暂无环境变量，可点击上方新建或智能导入" else "未匹配到相关环境变量", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        items(sortedEnvs, key = { it.id }) { env ->
                            val currentIndex = sortedEnvs.indexOf(env)
                            EnvCard(
                                env = env,
                                isBatchMode = uiState.isEnvBatchMode,
                                onSelect = { viewModel.toggleEnvSelection(env.id) },
                                onToggleEnable = { newStatus -> viewModel.toggleEnv(env.id, newStatus) },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(env.value))
                                    Toast.makeText(context, "全量明文变量已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                                },
                                onEdit = { editingEnv = env },
                                onSubEdit = { subItemEditingEnv = env },
                                onPin = { viewModel.pinEnv(env.id, !env.isPinned) },
                                onDelete = { deletingEnv = env },
                                onDragStart = {
                                    dragOverIndex = currentIndex
                                },
                                onDragOver = {
                                    dragOverIndex = currentIndex
                                },
                                onDragEnd = {
                                    val fromIdx = sortedEnvs.indexOf(env)
                                    val toIdx = dragOverIndex ?: run { dragOverIndex = null; return@EnvCard }
                                    if (fromIdx != toIdx && fromIdx != -1) {
                                        sortedEnvs = sortedEnvs.toMutableList().apply {
                                            add(toIdx, removeAt(fromIdx))
                                        }
                                        // 同步到服务端
                                        viewModel.moveEnvToServer(env.id, fromIdx, toIdx)
                                    }
                                    dragOverIndex = null
                                },
                                isDragging = dragOverIndex == currentIndex
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. 编辑环境变量 Modal
    editingEnv?.let { targetEnv ->
        EditEnvDialog(
            env = targetEnv,
            onDismiss = { editingEnv = null },
            onSave = { updated ->
                viewModel.saveEnv(updated)
                editingEnv = null
            },
            onOpenSubItemEditor = {
                subItemEditingEnv = targetEnv
                editingEnv = null
            }
        )
    }

    // 2. 变量分段/子项拆解修改器 (修复单字段安全保存)
    subItemEditingEnv?.let { targetEnv ->
        SubItemEditorDialog(
            env = targetEnv,
            onDismiss = { subItemEditingEnv = null },
            onSave = { assembledVal ->
                val updated = targetEnv.copy(value = assembledVal)
                viewModel.saveEnv(updated)
                subItemEditingEnv = null
            }
        )
    }

    // 3. 删除确认 Modal
    deletingEnv?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingEnv = null },
            title = { Text("删除环境变量", fontSize = 15.sp) },
            text = { Text("确定要删除环境变量 [${target.name}] 吗？", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEnv(target.id)
                        deletingEnv = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确定删除", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEnv = null }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }

    // 4. 智能多格式导入 Modal
    if (showImportDialog) {
        SmartEnvImportDialog(
            onDismiss = onDismissImportDialog,
            onImport = { rawText, splitAt ->
                val parsed = UniversalEnvParser.parseText(rawText, splitAt)
                if (parsed.isNotEmpty()) {
                    viewModel.addEnvs(parsed)
                    onDismissImportDialog()
                } else {
                    Toast.makeText(context, "未识别到有效的 KEY=VALUE 文本！", Toast.LENGTH_SHORT).show()
                }
            },
            onFetchRules = { url ->
                fetchScope.launch {
                    runCatching {
                        val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
                        val request = Request.Builder().url(url).get().build()
                        client.newCall(request).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val rules = Gson().fromJson(body, Array<UniversalEnvParser.Rule>::class.java).toList()
                                UniversalEnvParser.loadCustomRules(rules)
                                Toast.makeText(context, "解析规则已更新：${rules.size} 条", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "加载规则失败：HTTP ${resp.code}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.onFailure { e ->
                        Toast.makeText(context, "加载规则异常：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // 5. 导出 JSON Modal
    exportingEnvIds?.let { ids ->
        val envs = uiState.envs.filter { it.id in ids }
        val json = envs.joinToString(",\n") { e ->
            """  {"name": "${e.name.replace("\"", "\\\"")}",
               "value": "${e.value.replace("\"", "\\\"")}",
               "remarks": "${(e.remarks ?: "").replace("\"", "\\\"")}",
               "enabled": ${e.enabled}}"""
        }
        AlertDialog(
            onDismissRequest = { exportingEnvIds = null },
            title = { Text("导出环境变量 JSON", fontSize = 15.sp) },
            text = {
                Column {
                    Text("共 ${envs.size} 个变量，已导出为 JSON 格式：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = "[\n$json\n]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString("[\n$json\n]"))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            exportingEnvIds = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制 JSON", fontSize = 12.sp)
                    }
                    TextButton(onClick = { exportingEnvIds = null }) { Text("关闭", fontSize = 12.sp) }
                }
            }
        )
    }
}

@Composable
fun EnvCard(
    env: UnifiedEnv,
    isBatchMode: Boolean = false,
    onSelect: () -> Unit = {},
    onToggleEnable: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onSubEdit: () -> Unit,
    onPin: () -> Unit = {},
    onDelete: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragOver: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isDragging: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isDragging) 2.dp else 0.8.dp,
                color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽把手：仅把手可水平拖拽触发排序，垂直滚动由LazyColumn处理
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(isBatchMode) {
                        if (isBatchMode) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { onDragStart() },
                            onHorizontalDrag = { _, _ -> },
                            onDragEnd = { onDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "拖拽排序",
                    tint = if (isBatchMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (isBatchMode) {
                Checkbox(
                    checked = env.selected,
                    onCheckedChange = { onSelect() },
                    modifier = Modifier.padding(end = 4.dp).size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // 第一行：变量名（14sp 加粗）、状态微型徽标、备注标签、开关
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
                            text = env.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = if (env.enabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (env.enabled) "启用" else "禁用",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (env.enabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (!env.remarks.isNullOrBlank()) {
                            Text(
                                text = "(${env.remarks})",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    if (!isBatchMode) {
                        Switch(
                            checked = env.enabled,
                            onCheckedChange = onToggleEnable,
                            modifier = Modifier.scale(0.7f).height(24.dp)
                        )
                    }
                }

                // 第二行：值（12sp 等宽代码字体、点击复制）+ 紧凑行内操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCopy() }
                    ) {
                        Text(
                            text = env.value.trimStart('=').ifEmpty { "(空值)" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EnvsActionButton(icon = Icons.Default.Tune, label = "分段", tint = MaterialTheme.colorScheme.primary, onClick = onSubEdit)
                        EnvsActionButton(icon = Icons.Default.ContentCopy, label = "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onCopy)
                        EnvsActionButton(icon = Icons.Default.Edit, label = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onEdit)
                        if (!isBatchMode) {
                            EnvsActionButton(
                                icon = if (env.isPinned) Icons.Default.Pin else Icons.Default.PushPin,
                                label = if (env.isPinned) "已置顶" else "置顶",
                                tint = if (env.isPinned) androidx.compose.ui.graphics.Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onPin
                            )
                        }
                        EnvsActionButton(icon = Icons.Default.Delete, label = "删除", tint = MaterialTheme.colorScheme.error, onClick = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
fun EditEnvDialog(
    env: UnifiedEnv,
    onDismiss: () -> Unit,
    onSave: (UnifiedEnv) -> Unit,
    onOpenSubItemEditor: () -> Unit
) {
    val isCreate = env.id.isEmpty() || env.id.startsWith("new_")
    var name by remember { mutableStateOf(env.name) }
    var value by remember { mutableStateOf(env.value) }
    var remarks by remember { mutableStateOf(env.remarks ?: "") }
    var enabled by remember { mutableStateOf(env.enabled) }

    val nameError = remember(name) {
        if (name.isNotBlank() && name.first().isDigit()) {
            "提示：以数字开头的变量名在部分 Linux Shell 中可能无法被脚本 export，但面板支持存储"
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreate) "新建环境变量" else "编辑环境变量", fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("变量名称 (Name)", fontSize = 11.sp) },
                    supportingText = if (nameError != null) {
                        { Text(nameError, fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("变量完整值 (全明文)", fontSize = 11.sp)
                    if (!isCreate) {
                        TextButton(onClick = onOpenSubItemEditor) {
                            Text("分段拆解修改", fontSize = 11.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("变量值 (Value)", fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                    maxLines = 8
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("备注 (Remarks)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(env.copy(name = name.trim(), value = value, remarks = remarks, enabled = enabled))
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (isCreate) "创建" else "保存", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 12.sp) }
        }
    )
}

@Composable
fun SubItemEditorDialog(
    env: UnifiedEnv,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val pairs = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(UniversalEnvParser.parseSubItems(env.name, env.value))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("变量分段子项快捷修改器", fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("修改单字段，合成时自动保留其他字段：", fontSize = 11.sp)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    itemsIndexed(pairs) { index, pair ->
                        OutlinedTextField(
                            value = pair.second,
                            onValueChange = { newVal -> pairs[index] = Pair(pair.first, newVal) },
                            label = { Text("修改单字段: ${pair.first}", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 8,
                            singleLine = false
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(UniversalEnvParser.assembleSubItems(env.name, pairs, env.value)) }) {
                Text("拼合并保存", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 12.sp) }
        }
    )
}

@Composable
fun SmartEnvImportDialog(
    onDismiss: () -> Unit,
    onImport: (String, Boolean) -> Unit,
    onFetchRules: (String) -> Unit = {}
) {
    var rawText by remember { mutableStateOf("") }
    var splitAt by remember { mutableStateOf(true) }
    var showRulesInput by remember { mutableStateOf(false) }
    var rulesUrl by remember { mutableStateOf("") }
    var rulesLoading by remember { mutableStateOf(false) }
    var rulesError by remember { mutableStateOf<String?>(null) }

    fun handleFetchRules() {
        if (rulesUrl.isBlank()) return
        rulesLoading = true
        rulesError = null
        onFetchRules(rulesUrl)
        rulesLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("智能多格式环境变量导入", fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("支持 export、多行、@ 多账号、&# 实体、URL Query 与自定义解析规则：", fontSize = 11.sp)
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("粘贴例如：\nexport JD_COOKIE=\"pt_key=AA...;pt_pin=u1;\"\nJD_COOKIE=cookie1@cookie2\napp=mdwz&dataEncStr=MzA4MUEy", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("包含 @ 符号时拆解为多个账号", fontSize = 11.sp)
                    Switch(
                        checked = splitAt,
                        onCheckedChange = { splitAt = it },
                        modifier = Modifier.scale(0.75f)
                    )
                }
                Divider(thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showRulesInput = !showRulesInput }, enabled = !rulesLoading) {
                        Text(if (showRulesInput) "收起规则加载" else "从网络加载解析规则", fontSize = 11.sp)
                    }
                    if (rulesLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    if (rulesError != null) {
                        Text(rulesError!!, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (showRulesInput) {
                    OutlinedTextField(
                        value = rulesUrl,
                        onValueChange = { rulesUrl = it },
                        placeholder = { Text("输入 JSON 规则 URL，格式：[{\"keyRegex\":\".*\",\"splitChar\":\"&\"}]") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = rulesError != null
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        if (rulesUrl.isNotBlank() && rawText.isBlank()) {
                            handleFetchRules()
                        } else {
                            onImport(rawText, splitAt)
                        }
                    },
                    enabled = !rulesLoading
                ) {
                    Text(if (rulesLoading) "加载中..." else if (rulesUrl.isNotBlank() && rawText.isBlank()) "加载规则" else "智能解析并导入", fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 12.sp) }
        }
    )
}

@Composable
private fun EnvsActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        }
        Text(label, fontSize = 8.sp, color = tint)
    }
}
