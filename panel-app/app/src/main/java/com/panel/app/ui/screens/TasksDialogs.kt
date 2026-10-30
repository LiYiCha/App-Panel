package com.panel.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.UnifiedTask
import com.panel.app.ui.components.ScriptFilePickerSection
import com.panel.app.util.CronExpressionDescriber

/**
 * 任务新建/编辑对话框（从 TasksScreen.kt 拆出，保持任务列表文件聚焦于列表本身）。
 */

@Composable
fun CreateTaskDialog(
    scriptFiles: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (UnifiedTask) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("0 8 * * *") }
    var showAdvanced by remember { mutableStateOf(false) }

    // 白虎与青龙高级配置项
    var preCommand by remember { mutableStateOf("") }
    var postCommand by remember { mutableStateOf("") }
    var timeoutStr by remember { mutableStateOf("30") }
    var workDir by remember { mutableStateOf("") }
    var retryCountStr by remember { mutableStateOf("0") }
    var retryIntervalStr by remember { mutableStateOf("0") }
    var randomRangeStr by remember { mutableStateOf("0") }
    var labelsStr by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建任务", fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScriptFilePickerSection(
                    scriptFiles = scriptFiles,
                    onPicked = { taskName, suggestedCommand ->
                        name = taskName
                        command = suggestedCommand
                    }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("执行命令 (例如 python3 checkin.py)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("定时规则 (Cron)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val cronDesc = remember(schedule) {
                    CronExpressionDescriber.describe(schedule)
                }
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

                val presets = remember {
                    CronExpressionDescriber.PRESETS_5_PART
                }
                Text("常用预设:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(presets) { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (schedule == p.expression) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { schedule = p.expression }
                        ) {
                            Text(
                                text = p.label,
                                fontSize = 10.sp,
                                color = if (schedule == p.expression) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 高级配置切换按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showAdvanced) "收起高级配置 ▲" else "展开更多配置 ▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = preCommand,
                        onValueChange = { preCommand = it },
                        label = { Text("前置命令 (选填)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postCommand,
                        onValueChange = { postCommand = it },
                        label = { Text("后置命令 (选填)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeoutStr,
                            onValueChange = { timeoutStr = it },
                            label = { Text("超时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = workDir,
                            onValueChange = { workDir = it },
                            label = { Text("工作目录(选填)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = retryCountStr,
                            onValueChange = { retryCountStr = it },
                            label = { Text("重试次数", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = retryIntervalStr,
                            onValueChange = { retryIntervalStr = it },
                            label = { Text("重试间隔(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = randomRangeStr,
                            onValueChange = { randomRangeStr = it },
                            label = { Text("随机延时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = labelsStr,
                        onValueChange = { labelsStr = it },
                        label = { Text("标签 (逗号分隔)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && command.isNotBlank()) {
                        val task = UnifiedTask(
                            id = "",
                            name = name.trim(),
                            command = command.trim(),
                            schedule = schedule.trim(),
                            statusText = "已就绪",
                            preCommand = preCommand.trim().ifBlank { null },
                            postCommand = postCommand.trim().ifBlank { null },
                            timeout = timeoutStr.toIntOrNull() ?: 30,
                            workDir = workDir.trim().ifBlank { null },
                            retryCount = retryCountStr.toIntOrNull() ?: 0,
                            retryInterval = retryIntervalStr.toIntOrNull() ?: 0,
                            randomRange = randomRangeStr.toIntOrNull() ?: 0,
                            labels = if (labelsStr.isBlank()) emptyList() else labelsStr.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        onConfirm(task)
                    }
                },
                enabled = name.isNotBlank() && command.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun EditTaskDialog(
    task: UnifiedTask,
    onDismiss: () -> Unit,
    onConfirm: (UnifiedTask) -> Unit,
    scriptFiles: List<String> = emptyList()
) {
    var name by remember { mutableStateOf(task.name) }
    var command by remember { mutableStateOf(task.command) }
    var schedule by remember { mutableStateOf(task.schedule) }
    var showAdvanced by remember { mutableStateOf(false) }

    var preCommand by remember { mutableStateOf(task.preCommand ?: "") }
    var postCommand by remember { mutableStateOf(task.postCommand ?: "") }
    var timeoutStr by remember { mutableStateOf(task.timeout.toString()) }
    var workDir by remember { mutableStateOf(task.workDir ?: "") }
    var retryCountStr by remember { mutableStateOf(task.retryCount.toString()) }
    var retryIntervalStr by remember { mutableStateOf(task.retryInterval.toString()) }
    var randomRangeStr by remember { mutableStateOf(task.randomRange.toString()) }
    var labelsStr by remember { mutableStateOf(task.labels.joinToString(", ")) }

    val scrollState = rememberScrollState()

    val cronDesc = remember(schedule) {
        CronExpressionDescriber.describe(schedule)
    }

    val presets = remember(schedule) {
        if (schedule.trim().split("\\s+".toRegex()).size == 6)
            CronExpressionDescriber.PRESETS_6_PART
        else
            CronExpressionDescriber.PRESETS_5_PART
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑任务", fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("执行命令", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 浏览脚本库：与新建任务共用同一组件
                ScriptFilePickerSection(
                    scriptFiles = scriptFiles,
                    labelText = "更换目标脚本:",
                    onPicked = { taskName, suggestedCommand ->
                        name = taskName
                        command = suggestedCommand
                    }
                )

                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("Cron 规则", fontSize = 11.sp) },
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

                Text("常用预设:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(presets) { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (schedule == p.expression) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { schedule = p.expression }
                        ) {
                            Text(
                                text = p.label,
                                fontSize = 10.sp,
                                color = if (schedule == p.expression) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 高级配置切换按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showAdvanced) "收起高级配置 ▲" else "展开更多配置 ▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = preCommand,
                        onValueChange = { preCommand = it },
                        label = { Text("前置命令", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postCommand,
                        onValueChange = { postCommand = it },
                        label = { Text("后置命令", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeoutStr,
                            onValueChange = { timeoutStr = it },
                            label = { Text("超时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = workDir,
                            onValueChange = { workDir = it },
                            label = { Text("工作目录", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = retryCountStr,
                            onValueChange = { retryCountStr = it },
                            label = { Text("重试次数", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = retryIntervalStr,
                            onValueChange = { retryIntervalStr = it },
                            label = { Text("重试间隔(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = randomRangeStr,
                            onValueChange = { randomRangeStr = it },
                            label = { Text("随机延时(秒)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = labelsStr,
                        onValueChange = { labelsStr = it },
                        label = { Text("标签 (逗号分隔)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = task.copy(
                        name = name.trim(),
                        command = command.trim(),
                        schedule = schedule.trim(),
                        preCommand = preCommand.trim().ifBlank { null },
                        postCommand = postCommand.trim().ifBlank { null },
                        timeout = timeoutStr.toIntOrNull() ?: task.timeout,
                        workDir = workDir.trim().ifBlank { null },
                        retryCount = retryCountStr.toIntOrNull() ?: task.retryCount,
                        retryInterval = retryIntervalStr.toIntOrNull() ?: task.retryInterval,
                        randomRange = randomRangeStr.toIntOrNull() ?: task.randomRange,
                        labels = if (labelsStr.isBlank()) emptyList() else labelsStr.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    onConfirm(updated)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
