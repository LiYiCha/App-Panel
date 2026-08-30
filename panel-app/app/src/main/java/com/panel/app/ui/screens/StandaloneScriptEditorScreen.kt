package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneScriptEditorScreen(
    scriptName: String,
    initialContent: String = "",
    onSave: ((String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    BackHandler {
        onBack()
    }

    var searchQuery by remember { mutableStateOf("") }
    var fontSizeSp by remember { mutableStateOf(12) }
    var showSearchBar by remember { mutableStateOf(false) }

    // 动态脚本真实源码：使用 remember(initialContent) 与 LaunchedEffect 确保真实文件源码加载后立刻呈现
    var codeContent by remember(initialContent) {
        mutableStateOf(initialContent)
    }

    LaunchedEffect(initialContent) {
        if (initialContent.isNotEmpty() && codeContent != initialContent) {
            codeContent = initialContent
        }
    }

    val lineCount = remember(codeContent) {
        if (codeContent.isEmpty()) 1 else codeContent.lines().size
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var isEditable by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = scriptName, fontSize = 14.sp, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    // 复制全文不受限制
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(codeContent))
                            Toast.makeText(context, "代码已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showSearchBar = !showSearchBar }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(18.dp))
                    }
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
                                onSave?.invoke(codeContent)
                                isEditable = false
                                Toast.makeText(context, "脚本 [$scriptName] 已保存！", Toast.LENGTH_SHORT).show()
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
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索代码关键词...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }

            // 动态行号与代码编辑区域 (支持双指手势捏合自由放大缩小字号)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) {
                                val target = fontSizeSp * zoom
                                fontSizeSp = target.coerceIn(9f, 26f).toInt()
                            }
                        }
                    }
            ) {
                // 行号栏
                Column(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 1..maxOf(lineCount, 1)) {
                        Text(
                            text = "$i",
                            fontSize = fontSizeSp.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // 核心代码输入区（只读模式允许自由选中文本复制）
                androidx.compose.foundation.text.selection.SelectionContainer(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    TextField(
                        value = codeContent,
                        onValueChange = { if (isEditable) codeContent = it },
                        readOnly = !isEditable,
                        placeholder = {
                            if (codeContent.isEmpty()) {
                                Text("正在从服务端读取脚本内容...", fontSize = fontSizeSp.sp, fontFamily = FontFamily.Monospace)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
