package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.components.CodeSyntaxVisualTransformation
import com.panel.app.util.CronExpressionDescriber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneScriptEditorScreen(
    scriptName: String,
    initialContent: String = "",
    onSave: ((String) -> Unit)? = null,
    onAddToTask: ((name: String, command: String, schedule: String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    BackHandler {
        onBack()
    }

    var searchQuery by remember { mutableStateOf("") }
    var fontSizeSp by remember { mutableStateOf(12) }
    var showSearchBar by remember { mutableStateOf(false) }
    var isEditable by remember { mutableStateOf(false) }
    var showAddToTaskDialog by remember { mutableStateOf(false) }

    // 使用 TextFieldValue 保留选区和光标状态，杜绝纯 String 重组时光标被置零的问题
    var textFieldValue by remember(initialContent) {
        mutableStateOf(TextFieldValue(initialContent))
    }

    LaunchedEffect(initialContent) {
        if (initialContent.isNotEmpty() && textFieldValue.text != initialContent) {
            textFieldValue = TextFieldValue(initialContent)
        }
    }

    val codeText = textFieldValue.text

    // 计算搜索匹配项的所有起始下标
    val searchMatches = remember(codeText, searchQuery) {
        if (searchQuery.isBlank()) emptyList<Int>()
        else {
            val list = mutableListOf<Int>()
            var idx = 0
            while (idx < codeText.length) {
                val found = codeText.indexOf(searchQuery, idx, ignoreCase = true)
                if (found == -1) break
                list.add(found)
                idx = found + maxOf(searchQuery.length, 1)
            }
            list
        }
    }
    var currentMatchIndex by remember(searchMatches) { mutableStateOf(0) }

    // 当匹配项变化时重置/限制下标
    LaunchedEffect(searchMatches) {
        if (currentMatchIndex >= searchMatches.size) {
            currentMatchIndex = 0
        }
    }

    val lineCount = remember(codeText) {
        if (codeText.isEmpty()) 1 else codeText.lines().size
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val syntaxTransformation = remember(scriptName, isDark, searchQuery, currentMatchIndex, searchMatches.size) {
        CodeSyntaxVisualTransformation(
            extension = scriptName,
            isDark = isDark,
            searchQuery = searchQuery,
            activeMatchIndex = if (searchMatches.isNotEmpty()) currentMatchIndex else -1
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = scriptName.substringAfterLast('/'),
                        fontSize = 14.sp,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    // 添加为定时任务
                    IconButton(
                        onClick = { showAddToTaskDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AddAlarm, contentDescription = "添加为定时任务", modifier = Modifier.size(18.dp))
                    }
                    // 复制全文
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(codeText))
                            Toast.makeText(context, "代码已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
                    }
                    // 搜索开关
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (showSearchBar) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // 缩放字号
                    IconButton(onClick = { if (fontSizeSp > 10) fontSizeSp -= 1 }, modifier = Modifier.size(28.dp)) {
                        Text("A-", fontSize = 10.sp)
                    }
                    IconButton(onClick = { if (fontSizeSp < 22) fontSizeSp += 1 }, modifier = Modifier.size(28.dp)) {
                        Text("A+", fontSize = 10.sp)
                    }
                    if (!isEditable) {
                        Button(
                            onClick = { isEditable = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp).height(30.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("编辑", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                onSave?.invoke(codeText)
                                isEditable = false
                                Toast.makeText(context, "脚本已保存！", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp).height(30.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("保存", fontSize = 11.sp)
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
        ) {
            // 搜索操作栏 (具备高亮匹配计数与前后跳转)
            if (showSearchBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索代码关键词...", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 1,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )

                        // 匹配数量提示
                        val countText = when {
                            searchMatches.isNotEmpty() -> "${currentMatchIndex + 1}/${searchMatches.size}"
                            searchQuery.isNotEmpty() -> "0/0"
                            else -> ""
                        }
                        if (countText.isNotEmpty()) {
                            Text(
                                text = countText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 上一处匹配
                        IconButton(
                            onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                    val targetOffset = searchMatches[currentMatchIndex]
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(targetOffset, targetOffset + searchQuery.length)
                                    )
                                }
                            },
                            enabled = searchMatches.isNotEmpty(),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个", modifier = Modifier.size(18.dp))
                        }

                        // 下一处匹配
                        IconButton(
                            onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                                    val targetOffset = searchMatches[currentMatchIndex]
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(targetOffset, targetOffset + searchQuery.length)
                                    )
                                }
                            },
                            enabled = searchMatches.isNotEmpty(),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 动态行号与代码编辑区域
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 行号栏
                Column(
                    modifier = Modifier
                        .width(38.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 1..maxOf(lineCount, 1)) {
                        Text(
                            text = "$i",
                            fontSize = fontSizeSp.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // 核心代码输入区：
                // 注意：只有在只读状态时包裹 SelectionContainer；可编辑模式下坚决不能包裹 SelectionContainer，
                // 否则 SelectionContainer 会强行捕获手势并重置焦点游标至 0（导致点击中间自动回滚到顶部）！
                if (!isEditable) {
                    androidx.compose.foundation.text.selection.SelectionContainer(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        TextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            readOnly = true,
                            visualTransformation = syntaxTransformation,
                            placeholder = {
                                if (codeText.isEmpty()) {
                                    Text("正在从服务端读取脚本内容...", fontSize = fontSizeSp.sp, fontFamily = FontFamily.Monospace)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                } else {
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        readOnly = false,
                        visualTransformation = syntaxTransformation,
                        placeholder = {
                            if (codeText.isEmpty()) {
                                Text("请输入或粘贴脚本代码...", fontSize = fontSizeSp.sp, fontFamily = FontFamily.Monospace)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }

    // 从代码编辑器一键添加为定时任务弹窗
    if (showAddToTaskDialog) {
        val (parsedCron, parsedName) = remember(codeText) {
            parseScriptCommentInfo(codeText, scriptName.substringAfterLast('/'))
        }
        var taskName by remember { mutableStateOf(parsedName) }
        var taskCommand by remember { mutableStateOf("task $scriptName") }
        var taskSchedule by remember { mutableStateOf(parsedCron) }

        val cronDesc = remember(taskSchedule) {
            CronExpressionDescriber.describe(taskSchedule)
        }

        AlertDialog(
            onDismissRequest = { showAddToTaskDialog = false },
            title = { Text("添加到定时任务", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("从当前正在编辑的脚本创建定时任务：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = taskName,
                        onValueChange = { taskName = it },
                        label = { Text("任务名称", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskCommand,
                        onValueChange = { taskCommand = it },
                        label = { Text("执行命令", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskSchedule,
                        onValueChange = { taskSchedule = it },
                        label = { Text("定时规则 (Cron)", fontSize = 11.sp) },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (taskName.isNotBlank() && taskCommand.isNotBlank()) {
                            onAddToTask?.invoke(taskName, taskCommand, taskSchedule)
                            Toast.makeText(context, "已成功添加任务 [$taskName]", Toast.LENGTH_SHORT).show()
                            showAddToTaskDialog = false
                        }
                    },
                    enabled = taskName.isNotBlank() && taskCommand.isNotBlank()
                ) {
                    Text("确认添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddToTaskDialog = false }) { Text("取消") }
            }
        )
    }
}
