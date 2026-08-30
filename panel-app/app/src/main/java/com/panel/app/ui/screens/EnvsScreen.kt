package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.UnifiedEnv
import com.panel.app.data.parser.UniversalEnvParser
import com.panel.app.ui.viewmodel.MainViewModel

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
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshCurrentPanel() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredEnvs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredEnvs, key = { it.id }) { env ->
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
                                onDelete = { deletingEnv = env }
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
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = env.selected,
                    onCheckedChange = { onSelect() },
                    modifier = Modifier.padding(end = 4.dp).size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(text = env.name, fontSize = 13.sp, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            color = if (env.enabled) androidx.compose.ui.graphics.Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (env.enabled) "已启用" else "已禁用",
                                fontSize = 9.sp,
                                color = if (env.enabled) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (!isBatchMode) {
                        Switch(
                            checked = env.enabled,
                            onCheckedChange = onToggleEnable,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = env.value,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "备注: ${env.remarks ?: "无"}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        IconButton(onClick = onSubEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = "分段修改", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
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
    var name by remember { mutableStateOf(env.name) }
    var value by remember { mutableStateOf(env.value) }
    var remarks by remember { mutableStateOf(env.remarks ?: "") }
    var enabled by remember { mutableStateOf(env.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑环境变量", fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("变量名称 (Name)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("变量完整值 (全明文)", fontSize = 11.sp)
                    TextButton(onClick = onOpenSubItemEditor) {
                        Text("分段拆解修改", fontSize = 11.sp)
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("备注 (Remarks)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(env.copy(name = name, value = value, remarks = remarks, enabled = enabled)) }) {
                Text("保存", fontSize = 12.sp)
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("修改单字段，合成时自动保留其他字段：", fontSize = 11.sp)
                pairs.forEachIndexed { index, pair ->
                    OutlinedTextField(
                        value = pair.second,
                        onValueChange = { newVal -> pairs[index] = Pair(pair.first, newVal) },
                        label = { Text("修改单字段: ${pair.first}", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
    onImport: (String, Boolean) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var splitAt by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("智能多格式环境变量导入", fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("支持 export、多行、@ 多账号、&# 实体与 URL 编码：", fontSize = 11.sp)
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("粘贴例如：\nexport JD_COOKIE=\"pt_key=AA...;pt_pin=u1;\"\nJD_COOKIE=cookie1@cookie2", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("包含 @ 符号时拆解为多个账号", fontSize = 11.sp)
                    Switch(checked = splitAt, onCheckedChange = { splitAt = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(rawText, splitAt) }) {
                Text("智能解析并导入", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 12.sp) }
        }
    )
}
