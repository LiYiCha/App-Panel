package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScriptScreen(
    viewModel: MainViewModel,
    existingDirs: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var filename by remember { mutableStateOf("") }
    var selectedDir by remember { mutableStateOf("") }
    var isDirDropdownExpanded by remember { mutableStateOf(false) }
    var content by remember {
        mutableStateOf(
            """// 脚本入口
console.log("Hello from Panel Hub!");
"""
        )
    }

    var isAddToCron by remember { mutableStateOf(false) }
    var taskName by remember { mutableStateOf("") }
    var cronSchedule by remember { mutableStateOf("0 0 * * *") }

    val allDirs = remember(existingDirs) {
        listOf("根目录 (/)" to "") + existingDirs.map { "$it/" to it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("新建脚本文件", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                        Text("编写脚本并可选同步创建定时调度任务", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (filename.isBlank()) {
                                Toast.makeText(context, "请输入文件名 (如 test.js)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val cleanName = filename.trim()
                            val fullPath = if (selectedDir.isEmpty()) cleanName else "$selectedDir/$cleanName"

                            viewModel.createScript(fullPath, content)

                            if (isAddToCron) {
                                val tName = taskName.ifBlank { cleanName.substringBeforeLast('.') }
                                val cmd = "task $fullPath"
                                viewModel.createTask(tName, cmd, cronSchedule)
                                Toast.makeText(context, "已创建脚本并添加定时任务 [$tName]", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "脚本 [$fullPath] 已创建", Toast.LENGTH_SHORT).show()
                            }
                            onBack()
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存并创建", fontSize = 13.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 基础配置卡片 (文件名与目录)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("文件属性", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = filename,
                        onValueChange = {
                            filename = it
                            if (taskName.isBlank()) {
                                taskName = it.substringBeforeLast('.')
                            }
                        },
                        label = { Text("脚本文件名 (例如 test.js / check.py / task.sh)") },
                        placeholder = { Text("test.js") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 所在目录下拉选择
                    ExposedDropdownMenuBox(
                        expanded = isDirDropdownExpanded,
                        onExpandedChange = { isDirDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = allDirs.firstOrNull { it.second == selectedDir }?.first ?: "根目录 (/)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("存放目录 (选择已有文件夹)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDirDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = isDirDropdownExpanded,
                            onDismissRequest = { isDirDropdownExpanded = false }
                        ) {
                            allDirs.forEach { (display, value) ->
                                DropdownMenuItem(
                                    text = { Text(display) },
                                    onClick = {
                                        selectedDir = value
                                        isDirDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. 定时任务配置卡片 (Switch 联动)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("同步添加为定时任务", style = MaterialTheme.typography.titleSmall)
                                Text("脚本创建后自动在调度中心注册此任务", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isAddToCron,
                            onCheckedChange = { isAddToCron = it }
                        )
                    }

                    if (isAddToCron) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = taskName,
                            onValueChange = { taskName = it },
                            label = { Text("定时任务名称") },
                            placeholder = { Text("自动执行脚本") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = cronSchedule,
                            onValueChange = { cronSchedule = it },
                            label = { Text("Cron 调度表达式 (秒 分 时 日 月 周)") },
                            placeholder = { Text("0 0 * * *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 快捷 Cron 推荐标签
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "0 0 * * *" to "每天零点",
                                "0 */1 * * *" to "每小时",
                                "*/30 * * * *" to "每30分钟"
                            ).forEach { (cron, desc) ->
                                AssistChip(
                                    onClick = { cronSchedule = cron },
                                    label = { Text(desc, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. 脚本内容编写区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("脚本源码正文", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 500.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        placeholder = { Text("// 在此编写代码...") }
                    )
                }
            }
        }
    }
}
