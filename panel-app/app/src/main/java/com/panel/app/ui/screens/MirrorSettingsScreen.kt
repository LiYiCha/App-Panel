package com.panel.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.util.MirrorManager
import com.panel.app.util.MirrorManager.Type

/**
 * 下载加速镜像管理页（从设置页进入）。
 *
 * 简化版能力：源列表 + 单选切换 + 自定义源增删（预设源不可删，不做延迟测速）。
 * - 更新下载镜像：应用内检查更新后 APK 的下载源（GitHub Release 直链加速）
 * - Python 依赖镜像：PyPI wheel 下载源（仅完整版生效）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorSettingsScreen(
    onBack: () -> Unit,
    localScriptEnabled: Boolean = false,
) {
    BackHandler { onBack() }

    var updateSources by remember { mutableStateOf(MirrorManager.sources(Type.UPDATE)) }
    var pypiSources by remember { mutableStateOf(MirrorManager.sources(Type.PYPI)) }
    var selectedUpdate by remember { mutableStateOf(MirrorManager.selected(Type.UPDATE)) }
    var selectedPyPi by remember { mutableStateOf(MirrorManager.selected(Type.PYPI)) }
    var addDialogType by remember { mutableStateOf<Type?>(null) }

    fun refresh() {
        updateSources = MirrorManager.sources(Type.UPDATE)
        pypiSources = MirrorManager.sources(Type.PYPI)
        selectedUpdate = MirrorManager.selected(Type.UPDATE)
        selectedPyPi = MirrorManager.selected(Type.PYPI)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "下载加速镜像",
                        fontSize = 15.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==================== 更新下载镜像 ====================
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "应用更新下载源",
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "应用内检查更新后，APK 从所选源下载；镜像失败会自动回退 GitHub 直连",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    updateSources.forEach { source ->
                        MirrorSourceRow(
                            source = source,
                            selected = source.id == selectedUpdate.id,
                            onSelect = {
                                MirrorManager.select(Type.UPDATE, source.id)
                                refresh()
                            },
                            onRemove = {
                                MirrorManager.removeCustom(source.id)
                                refresh()
                            }
                        )
                    }
                    AddSourceRow { addDialogType = Type.UPDATE }
                }
            }

            // ==================== Python 依赖镜像 ====================
            if (localScriptEnabled) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Python 依赖下载源",
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "本地脚本安装纯 Python 包时 wheel 的下载源，改动立即生效",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        pypiSources.forEach { source ->
                            MirrorSourceRow(
                                source = source,
                                selected = source.id == selectedPyPi.id,
                                onSelect = {
                                    MirrorManager.select(Type.PYPI, source.id)
                                    refresh()
                                },
                                onRemove = {
                                    MirrorManager.removeCustom(source.id)
                                    refresh()
                                }
                            )
                        }
                        AddSourceRow { addDialogType = Type.PYPI }
                    }
                }
            }

            Text(
                "自定义源格式：更新源填镜像前缀（如 https://example.com/），Python 源填站点地址（如 https://pypi.example.com）",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    addDialogType?.let { type ->
        AddMirrorDialog(
            type = type,
            onDismiss = { addDialogType = null },
            onConfirm = { name, url ->
                val ok = MirrorManager.addCustom(type, name, url)
                if (ok) {
                    refresh()
                    addDialogType = null
                }
                ok
            }
        )
    }
}

@Composable
private fun MirrorSourceRow(
    source: MirrorManager.MirrorSource,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column {
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
        }
        if (!source.isPreset) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
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

@Composable
private fun AddSourceRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
}

@Composable
private fun AddMirrorDialog(
    type: Type,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Boolean,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (type == Type.UPDATE) "添加更新下载源" else "添加 Python 依赖源",
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
                            if (type == Type.UPDATE) "镜像前缀（https://...）" else "站点地址（https://...）",
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
