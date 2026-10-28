package com.panel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
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
import com.panel.app.util.LocalScriptKit
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.runner.RunStatus
import com.panel.corescript.store.RunLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行记录 Tab 内容：日志按脚本归类管理。
 * 顶层按脚本聚合（脚本卡显示运行次数/最近状态/最近时间），点开内联展开该脚本
 * 的全部历史记录；支持单条删除、按脚本清理、全部清空。
 * 数据源为 files/run_logs/ 下的日志文件（RunLogStore）。
 * 由 ScriptCenterScreen 承载，自身不带页面壳。
 */
@Composable
fun RunHistoryTabContent(
    onOpenRun: (String) -> Unit
) {
    val context = LocalContext.current

    var entries by remember { mutableStateOf(LocalScriptKit.runLogStore(context).list()) }
    var confirmClear by remember { mutableStateOf(false) }
    // 待清理的脚本组（null=全部）
    var confirmClearScript by remember { mutableStateOf<String?>(null) }
    var expandedScripts by remember { mutableStateOf(setOf<String>()) }

    fun refresh() {
        entries = LocalScriptKit.runLogStore(context).list()
    }

    val groups = remember(entries) {
        entries.groupBy { it.scriptName }
            .map { (name, list) -> name to list.sortedByDescending { it.startedAt } }
            .sortedByDescending { it.second.first().startedAt }
    }
    val okCount = remember(entries) { entries.count { it.status == RunStatus.SUCCESS } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // 汇总卡片：图标 tile + 统计 + 清空动作
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
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("运行记录", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${groups.size} 个脚本 · ${entries.size} 条（成功 $okCount） · 保留最近 50 条",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entries.isNotEmpty()) {
                    ActionButtonSmall(
                        icon = Icons.Default.Close,
                        label = "清空",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { confirmClear = true }
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无运行记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                groups.forEach { (scriptName, list) ->
                    val expanded = scriptName in expandedScripts
                    item(key = "group_$scriptName") {
                        ScriptGroupCard(
                            scriptName = scriptName,
                            runs = list,
                            expanded = expanded,
                            onToggle = {
                                expandedScripts = if (expanded) expandedScripts - scriptName else expandedScripts + scriptName
                            },
                            onClearScript = { confirmClearScript = scriptName }
                        )
                    }
                    if (expanded) {
                        items(list, key = { "${scriptName}_${it.runId}" }) { entry ->
                            RunLogEntryCard(
                                entry = entry,
                                onClick = { onOpenRun(entry.runId) },
                                onDelete = {
                                    LocalScriptKit.runLogStore(context).delete(entry.runId)
                                    refresh()
                                }
                            )
                        }
                    }
                }
            }
        }

        Text(
            "日志按脚本归类；点脚本展开历史，可单条或整组清理。单条日志上限 10MB。",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空运行记录", fontSize = 15.sp) },
            text = { Text("将删除全部 ${entries.size} 条运行日志，不可恢复。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    LocalScriptKit.runLogStore(context).clearAll()
                    confirmClear = false
                    refresh()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }

    confirmClearScript?.let { scriptName ->
        val count = entries.count { it.scriptName == scriptName }
        AlertDialog(
            onDismissRequest = { confirmClearScript = null },
            title = { Text("清理脚本日志", fontSize = 15.sp) },
            text = { Text("将删除「$scriptName」的全部 $count 条运行日志，不可恢复。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val store = LocalScriptKit.runLogStore(context)
                    entries.filter { it.scriptName == scriptName }.forEach { store.delete(it.runId) }
                    confirmClearScript = null
                    refresh()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearScript = null }) { Text("取消") }
            }
        )
    }
}

/** 脚本分组卡：语言 tile + 脚本名 + 次数统计 + 最近状态 + 最近时间；展开箭头；清理按钮 */
@Composable
private fun ScriptGroupCard(
    scriptName: String,
    runs: List<RunLogEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClearScript: () -> Unit
) {
    val latest = runs.first()
    val okCount = runs.count { it.status == RunStatus.SUCCESS }
    val failCount = runs.count { it.status == RunStatus.FAILED }
    val timeText = remember(latest.startedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(latest.startedAt))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (latest.lang == ScriptLang.PYTHON) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (latest.lang == ScriptLang.PYTHON) "PY" else "JS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (latest.lang == ScriptLang.PYTHON) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scriptName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${runs.size} 次 · 成功 $okCount · 失败 $failCount · 最近 $timeText",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(latest.status)
            IconButton(onClick = onClearScript, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "清理该脚本日志",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RunLogEntryCard(
    entry: RunLogEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(entry.startedAt) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.startedAt))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                timeText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatSize(entry.sizeBytes),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusChip(entry.status)
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: RunStatus?) {
    val (label, color) = when (status) {
        RunStatus.SUCCESS -> "成功" to MaterialTheme.colorScheme.primary
        RunStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        RunStatus.STOPPED -> "已停止" to MaterialTheme.colorScheme.onSurfaceVariant
        RunStatus.RUNNING, RunStatus.PAUSED -> "运行中" to MaterialTheme.colorScheme.primary
        null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
