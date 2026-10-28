package com.panel.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 * 下载加速镜像选择行：紧凑单行展示当前选中源，点击弹出选择对话框。
 * 对话框内：源单选列表（自定义源可删）+ 添加自定义源。
 *
 * 展示两类源：
 *  - 应用更新下载源：应用内检查更新后 APK 的下载源（GitHub Release 直链加速）
 *  - Python 依赖下载源：PyPI wheel 下载源（仅完整版脚本引擎可用）
 *
 * 复用方：「其他设置」页（OtherSettingsScreen）基础设置卡片内嵌。
 */
@Composable
fun MirrorPickerRow(
    label: String,
    subtitle: String,
    type: Type,
    divider: @Composable () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(MirrorManager.sources(type)) }
    var selectedId by remember { mutableStateOf(MirrorManager.selected(type).id) }
    var addDialog by remember { mutableStateOf(false) }

    fun refresh() {
        sources = MirrorManager.sources(type)
        selectedId = MirrorManager.selected(type).id
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, fontSize = 12.sp)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${sources.firstOrNull { it.id == selectedId }?.name ?: "默认"} >",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
    divider()

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (type == Type.UPDATE)
                            "应用内检查更新后，APK 从所选源下载；镜像失败自动回退 GitHub 直连"
                        else
                            "本地脚本安装纯 Python 包时 wheel 的下载源，改动立即生效",
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
                        "自定义源格式：更新源填镜像前缀（如 https://example.com/），Python 源填站点地址（如 https://pypi.example.com）",
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
        AddMirrorDialog(
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
