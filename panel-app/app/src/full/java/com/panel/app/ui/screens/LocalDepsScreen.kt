package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
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
import com.panel.app.ui.components.SearchPillField
import com.panel.app.util.LocalScriptKit
import com.panel.app.util.MirrorManager
import com.panel.corescript.deps.JsDepInfo
import com.panel.corescript.deps.PyDepInfo
import kotlinx.coroutines.launch

/**
 * 依赖 Tab 内容：Python / JS 运行时依赖管理（分区安装 / 卸载）。
 * Python：安装纯 Python 包（*-any.whl），运行时注入 sys.path；
 * JS：安装 npm 纯 JS 包（含依赖树），运行时注册为 require 模块。
 * 原生扩展包需构建期预置。由 ScriptCenterScreen 承载，自身不带页面壳。
 */
@Composable
fun DepsTabContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pyDeps by remember { mutableStateOf(LocalScriptKit.pyDeps.list()) }
    var jsDeps by remember { mutableStateOf(LocalScriptKit.jsDeps.list()) }
    fun refresh() {
        pyDeps = LocalScriptKit.pyDeps.list()
        jsDeps = LocalScriptKit.jsDeps.list()
    }

    // 安装目标：null 关闭；false Python；true JS
    var showInstallDialog by remember { mutableStateOf<Boolean?>(null) }
    var depName by remember { mutableStateOf("") }
    var installProgress by remember { mutableStateOf<String?>(null) }
    var deletingDep by remember { mutableStateOf<Any?>(null) }
    // 搜索过滤
    var searchQuery by remember { mutableStateOf("") }
    // 折叠的分组（"py"/"js"，默认全部展开；页面生命周期内记忆）
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }
    fun toggleGroup(key: String) {
        collapsedGroups = if (key in collapsedGroups) collapsedGroups - key else collapsedGroups + key
    }

    // 批量卸载选择模式（key 前缀区分来源："py:目录名" / "js:目录名"）
    var selecting by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    var showBatchDialog by remember { mutableStateOf(false) }
    fun toggleSelect(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }
    fun exitSelecting() {
        selecting = false
        selectedKeys = emptySet()
    }

    val pyTotal = remember(pyDeps) { pyDeps.sumOf { it.sizeBytes } }
    val jsTotal = remember(jsDeps) { jsDeps.sumOf { it.sizeBytes } }
    val query = searchQuery.trim()
    val filteredPy = remember(pyDeps, query) {
        if (query.isEmpty()) pyDeps else pyDeps.filter { it.name.contains(query, ignoreCase = true) }
    }
    val filteredJs = remember(jsDeps, query) {
        if (query.isEmpty()) jsDeps else jsDeps.filter { it.name.contains(query, ignoreCase = true) }
    }
    // 搜索命中时强制展开对应分组（忽略折叠态），清空后恢复
    val pyCollapsed = "py" in collapsedGroups && !(query.isNotEmpty() && filteredPy.isNotEmpty())
    val jsCollapsed = "js" in collapsedGroups && !(query.isNotEmpty() && filteredJs.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // 汇总卡片 / 选择模式操作条
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            if (selecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已选 ${selectedKeys.size} 项",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val all = filteredPy.map { "py:${it.dirName}" } + filteredJs.map { "js:${it.dirName}" }
                            selectedKeys = if (selectedKeys.size >= all.size) emptySet() else all.toSet()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("全选", fontSize = 11.sp) }
                    TextButton(
                        enabled = selectedKeys.isNotEmpty(),
                        onClick = { showBatchDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("卸载", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                    TextButton(
                        onClick = { exitSelecting() },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("取消", fontSize = 11.sp) }
                }
            } else {
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
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("运行时依赖", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Python ${pyDeps.size} 个（${formatSize(pyTotal)}） · JS ${jsDeps.size} 个（${formatSize(jsTotal)}）",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionButtonSmall(
                        icon = Icons.Default.Done,
                        label = "多选",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { selecting = true }
                    )
                }
            }
        }

        // 搜索框（常驻）
        SearchPillField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "搜索已安装依赖…",
            modifier = Modifier.padding(top = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ==================== 下载源（统一镜像行，位于两组分组卡上方） ====================
            item(key = "mirror_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("下载源", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        MirrorSourceChip(label = "PyPI", type = MirrorManager.Type.PYPI)
                        MirrorSourceChip(label = "npm", type = MirrorManager.Type.NPM)
                    }
                }
            }

            // ==================== Python 依赖（可折叠分组卡） ====================
            item(key = "py_card") {
                DepGroupCard(
                    langLabel = "PY",
                    title = "Python 依赖",
                    desc = "纯 Python 包，运行时注入 sys.path",
                    count = filteredPy.size,
                    collapsed = pyCollapsed,
                    primary = true,
                    onToggle = { toggleGroup("py") },
                    onInstall = { showInstallDialog = false }
                ) {
                    if (filteredPy.isEmpty()) {
                        SectionEmpty(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            text = if (query.isEmpty()) "暂无依赖（基础包已预置）" else "无匹配依赖"
                        )
                    } else {
                        filteredPy.forEachIndexed { index, dep ->
                            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            DepRow(
                                title = dep.name,
                                subtitle = "v${dep.version} · ${formatSize(dep.sizeBytes)}",
                                selecting = selecting,
                                selected = "py:${dep.dirName}" in selectedKeys,
                                onClick = { toggleSelect("py:${dep.dirName}") },
                                onLongClick = { selecting = true },
                                onDelete = { deletingDep = dep }
                            )
                        }
                    }
                }
            }

            // ==================== JS 依赖（可折叠分组卡） ====================
            item(key = "js_card") {
                DepGroupCard(
                    langLabel = "JS",
                    title = "JS 依赖（npm）",
                    desc = "纯 JS 包，require('包名') 引用",
                    count = filteredJs.size,
                    collapsed = jsCollapsed,
                    primary = false,
                    onToggle = { toggleGroup("js") },
                    onInstall = { showInstallDialog = true }
                ) {
                    if (filteredJs.isEmpty()) {
                        SectionEmpty(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            text = if (query.isEmpty()) "暂无 JS 依赖" else "无匹配 JS 依赖"
                        )
                    } else {
                        filteredJs.forEachIndexed { index, dep ->
                            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            DepRow(
                                title = dep.name,
                                subtitle = buildString {
                                    if (!dep.isTopLevel) append("传递依赖 · ")
                                    append("v${dep.version} · ${formatSize(dep.sizeBytes)}")
                                    if (dep.isTopLevel && dep.depCount > 0) append(" · 含 ${dep.depCount} 个依赖")
                                },
                                selecting = selecting,
                                selected = "js:${dep.dirName}" in selectedKeys,
                                onClick = { toggleSelect("js:${dep.dirName}") },
                                onLongClick = { selecting = true },
                                onDelete = { deletingDep = dep }
                            )
                        }
                    }
                }
            }
        }
    }

    // 安装对话框（Python / JS 共用壳，按目标调用对应管理器）
    if (showInstallDialog != null) {
        val isJs = showInstallDialog == true
        AlertDialog(
            onDismissRequest = { if (installProgress == null) showInstallDialog = null },
            title = { Text(if (isJs) "安装 JS 依赖" else "安装 Python 依赖", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = depName,
                        onValueChange = { depName = it },
                        label = {
                            Text(
                                if (isJs) "npm 包名（如 crypto-js、dayjs）" else "PyPI 包名（如 rich、httpx）",
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        enabled = installProgress == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isJs) {
                        Text(
                            "将自动安装其 dependencies 依赖树，仅支持纯 JS 包",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        val target = depName.trim()
                        installProgress = "开始安装…"
                        scope.launch {
                            val result = if (isJs) {
                                LocalScriptKit.jsDeps.install(target) { p -> installProgress = p }
                                    .map { it.name to it.version }
                            } else {
                                LocalScriptKit.pyDeps.install(target) { p -> installProgress = p }
                                    .map { it.name to it.version }
                            }
                            result.onSuccess { (name, version) ->
                                Toast.makeText(context, "已安装 $name $version", Toast.LENGTH_SHORT).show()
                                showInstallDialog = null
                                depName = ""
                                refresh()
                            }.onFailure { e ->
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
                    onClick = { showInstallDialog = null }
                ) { Text("取消") }
            }
        )
    }

    // 卸载确认
    deletingDep?.let { dep ->
        val (title, subtitle, dirName) = when (dep) {
            is PyDepInfo -> Triple("卸载 Python 依赖", "${dep.name} ${dep.version}", dep.dirName)
            else -> {
                val js = dep as JsDepInfo
                Triple("卸载 JS 依赖", "${js.name} ${js.version}", js.dirName)
            }
        }
        AlertDialog(
            onDismissRequest = { deletingDep = null },
            title = { Text(title, fontSize = 15.sp) },
            text = { Text("确定卸载 $subtitle？", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = if (dep is PyDepInfo) {
                            LocalScriptKit.pyDeps.uninstall(dirName)
                        } else {
                            LocalScriptKit.jsDeps.uninstall(dirName)
                        }
                        result.onSuccess {
                            Toast.makeText(context, "已卸载", Toast.LENGTH_SHORT).show()
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

    // 批量卸载确认
    if (showBatchDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            title = { Text("批量卸载", fontSize = 15.sp) },
            text = { Text("确定卸载已选的 ${selectedKeys.size} 个依赖？此操作不可撤销。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val keys = selectedKeys.toSet()
                    showBatchDialog = false
                    scope.launch {
                        var ok = 0
                        var fail = 0
                        keys.forEach { key ->
                            val (prefix, dirName) = key.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                            val result = if (prefix == "py") {
                                LocalScriptKit.pyDeps.uninstall(dirName)
                            } else {
                                LocalScriptKit.jsDeps.uninstall(dirName)
                            }
                            if (result.isSuccess) ok++ else fail++
                        }
                        Toast.makeText(
                            context,
                            if (fail == 0) "已卸载 $ok 个依赖" else "已卸载 $ok 个，失败 $fail 个",
                            Toast.LENGTH_SHORT
                        ).show()
                        exitSelecting()
                        refresh()
                    }
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 可折叠依赖分组卡：组头（语言 tile + 标题计数 + 说明 + 安装 + 箭头）点击切换；
 *  折叠时仅显示组头，展开时渲染 content */
@Composable
private fun DepGroupCard(
    langLabel: String,
    title: String,
    desc: String,
    count: Int,
    collapsed: Boolean,
    primary: Boolean,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (primary) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        langLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("$title ($count)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ActionButtonSmall(
                    icon = Icons.Default.Add,
                    label = "安装",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onInstall
                )
                Icon(
                    if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!collapsed) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                content()
            }
        }
    }
}

/** 分区空态（嵌入分组卡内部使用） */
@Composable
private fun SectionEmpty(modifier: Modifier = Modifier, text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier
    )
}

/** 依赖紧凑行：名称 + 版本·大小 + 卸载（列表行样式，由分组卡统一包边）；
 *  选择模式下点击切换勾选，长按任意行进入选择模式 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DepRow(
    title: String,
    subtitle: String,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (selecting) onClick else onLongClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "卸载", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** 镜像下载源 chip：展示「前缀 当前源 ▾」，点击弹出源选择（单选 + 自定义增删）；
 *  两组分组卡共用的统一下载源入口 */
@Composable
private fun MirrorSourceChip(label: String, type: MirrorManager.Type) {
    var showPicker by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(MirrorManager.sources(type)) }
    var selectedId by remember { mutableStateOf(MirrorManager.selected(type).id) }
    var addDialog by remember { mutableStateOf(false) }

    fun refresh() {
        sources = MirrorManager.sources(type)
        selectedId = MirrorManager.selected(type).id
    }
    val currentName = sources.firstOrNull { it.id == selectedId }?.name ?: "默认"
    val isPy = type == MirrorManager.Type.PYPI

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { showPicker = true }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label $currentName",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = "选择 $label 下载源",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("$label 下载源", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (isPy) "安装纯 Python 包时 wheel 的下载源，改动立即生效"
                        else "安装 npm 包时 registry 查询与 tarball 的下载源，改动立即生效",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    sources.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    MirrorManager.select(type, source.id)
                                    refresh()
                                    showPicker = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = source.id == selectedId,
                                onClick = {
                                    MirrorManager.select(type, source.id)
                                    refresh()
                                    showPicker = false
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.name, fontSize = 12.sp)
                                if (source.url.isNotEmpty()) {
                                    Text(
                                        source.url,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (!source.isPreset) {
                                IconButton(
                                    onClick = {
                                        MirrorManager.removeCustom(source.id)
                                        refresh()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addDialog = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("添加自定义源", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        if (isPy) "自定义源填站点地址（如 https://pypi.example.com）"
                        else "自定义源填 registry 地址（如 https://registry.npmmirror.com）",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("完成") }
            }
        )
    }

    if (addDialog) {
        AddMirrorSourceDialog(
            type = type,
            onDismiss = { addDialog = false },
            onConfirm = { name, url ->
                val ok = MirrorManager.addCustom(type, name, url)
                if (ok) refresh()
                ok
            }
        )
    }
}

/** 添加自定义镜像源对话框（名称 + 地址校验） */
@Composable
private fun AddMirrorSourceDialog(
    type: MirrorManager.Type,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (type == MirrorManager.Type.PYPI) "添加 PyPI 依赖源" else "添加 npm 下载源",
                fontSize = 15.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        Text(
                            if (type == MirrorManager.Type.PYPI) "站点地址（https://...）"
                            else "registry 地址（https://...）",
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        name.isBlank() -> error = "请输入名称"
                        url.isBlank() -> error = "请输入地址"
                        !onConfirm(name, url) -> error = "地址需以 http(s):// 开头，名称不超过 30 字"
                        else -> return@TextButton
                    }
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
