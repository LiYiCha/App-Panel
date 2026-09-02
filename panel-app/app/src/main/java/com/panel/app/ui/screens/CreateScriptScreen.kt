package com.panel.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    BackHandler { onBack() }

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
    var isSaving by remember { mutableStateOf(false) }

    // 智能任务识别与二进制文件拦截状态
    var showBinaryErrorDialog by remember { mutableStateOf(false) }
    var showSmartTaskDialog by remember { mutableStateOf(false) }
    var smartTaskName by remember { mutableStateOf("") }
    var smartTaskCron by remember { mutableStateOf("0 8 * * *") }
    var smartTaskCmd by remember { mutableStateOf("") }

    val allDirs = remember(existingDirs) {
        listOf("根目录 (/)" to "") + existingDirs.map { "$it/" to it }
    }

    // 本地代码文件选择器 (严禁二进制 apk/zip 等，只导入合法脚本)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val pickedName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":") ?: "imported_script.js"

            val ext = pickedName.substringAfterLast('.', "").lowercase()
            val binaryExts = setOf("apk", "zip", "rar", "7z", "tar", "gz", "dex", "so", "bin", "exe", "png", "jpg", "jpeg", "webp", "gif", "mp4", "mp3", "pdf")

            if (binaryExts.contains(ext)) {
                showBinaryErrorDialog = true
                return@rememberLauncherForActivityResult
            }

            val readContent = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()

            if (readContent == null) {
                Toast.makeText(context, "读取本地文件失败", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            // 导入成功：填入文件名与正文
            filename = pickedName
            content = readContent
            if (taskName.isBlank()) {
                taskName = pickedName.substringBeforeLast('.')
            }

            // 智能提取脚本头部元数据 (new Env, cron, command)
            val (parsedName, parsedCron) = parseScriptCommentInfo(readContent, pickedName)
            val cleanName = pickedName.trim()
            val fullPath = if (selectedDir.isEmpty()) cleanName else "$selectedDir/$cleanName"
            val parsedCmd = when {
                pickedName.endsWith(".py", ignoreCase = true) -> "task $fullPath"
                pickedName.endsWith(".sh", ignoreCase = true) -> "task $fullPath"
                else -> "task $fullPath"
            }

            smartTaskName = parsedName
            smartTaskCron = parsedCron
            smartTaskCmd = parsedCmd
            showSmartTaskDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("新建脚本文件", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                        Text("在线编写代码或从本机导入文本脚本", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                            isSaving = true
                            if (isAddToCron) {
                                val tName = taskName.ifBlank { cleanName.substringBeforeLast('.') }
                                val cmd = "task $fullPath"
                                viewModel.createScriptAndTask(fullPath, content, tName, cmd, cronSchedule) { success, _ ->
                                    isSaving = false
                                    if (success) onBack()
                                }
                            } else {
                                viewModel.createScript(fullPath, content) { success, _ ->
                                    isSaving = false
                                    if (success) onBack()
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("保存中...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("保存并推送到远端", fontSize = 13.sp)
                        }
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

                    // 快捷后缀选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("快捷后缀:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(".js", ".py", ".sh", ".ts", ".json").forEach { ext ->
                            SuggestionChip(
                                onClick = {
                                    val base = if (filename.contains('.')) filename.substringBeforeLast('.') else filename
                                    filename = (base.ifBlank { "script" }) + ext
                                },
                                label = { Text(ext, fontSize = 10.sp) }
                            )
                        }
                    }

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
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                            onCheckedChange = { isAddToCron = it },
                            modifier = Modifier.scale(0.75f)
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
                                "0 8 * * *" to "每天早8点",
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

            // 3. 脚本内容编写区域 (带导入本地文件功能)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("脚本源码正文", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导入本地代码文件", fontSize = 11.sp)
                        }
                    }

                    com.panel.app.ui.components.CodeEditorView(
                        code = content,
                        onCodeChange = { content = it },
                        fileName = filename.ifBlank { "script.js" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 500.dp)
                    )
                }
            }
        }
    }

    // 4. 二进制文件 (APK/ZIP) 友好拦截弹窗
    if (showBinaryErrorDialog) {
        AlertDialog(
            onDismissRequest = { showBinaryErrorDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("不支持上传该文件类型", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "您选择的是二进制文件（如 APK、ZIP 等安装或压缩包）。青龙与白虎面板属于脚本运行容器，仅支持执行文本代码文件（.py、.js、.ts、.sh 等）。面板无法运行或查看二进制 APK 文件。",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(onClick = { showBinaryErrorDialog = false }) {
                    Text("我知道了", fontSize = 12.sp)
                }
            }
        )
    }

    // 5. 智能识别到任务配置确认弹窗 (您的补充需求：智能显示任务并让用户选择是否添加)
    if (showSmartTaskDialog) {
        AlertDialog(
            onDismissRequest = { showSmartTaskDialog = false },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
            title = { Text("智能识别到脚本任务配置", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("检测到导入的脚本包含定时调度元数据，是否同步注册为定时任务？", fontSize = 12.sp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text("任务名称: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(smartTaskName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row {
                                Text("定时规则: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(smartTaskCron, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            }
                            Row {
                                Text("调度命令: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(smartTaskCmd, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    isAddToCron = true
                    taskName = smartTaskName
                    cronSchedule = smartTaskCron
                    showSmartTaskDialog = false
                    Toast.makeText(context, "已为您开启并填入定时任务配置！", Toast.LENGTH_SHORT).show()
                }) {
                    Text("同步添加定时任务", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartTaskDialog = false }) {
                    Text("仅保存脚本", fontSize = 12.sp)
                }
            }
        )
    }
}
