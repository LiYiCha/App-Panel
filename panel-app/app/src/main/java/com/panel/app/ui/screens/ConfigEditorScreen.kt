package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(
    viewModel: MainViewModel,
    showDirectoryDrawer: Boolean = false,
    onDismissDirectoryDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeEditingFile by remember { mutableStateOf<String?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var isEditable by remember { mutableStateOf(false) }

    val filteredFiles = remember(searchQuery, uiState.configFiles) {
        if (searchQuery.isEmpty()) uiState.configFiles else {
            uiState.configFiles.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    // 视图模式 1: 编辑特定配置文件
    if (activeEditingFile != null) {
        val editingFile = activeEditingFile!!
        val lineCount = remember(editorContent) { editorContent.lines().size }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            activeEditingFile = null
                            isEditable = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回列表", modifier = Modifier.size(18.dp))
                    }
                    Text(text = "config / $editingFile", fontSize = 14.sp, style = MaterialTheme.typography.titleMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(editorContent))
                            Toast.makeText(context, "配置全文已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                    }
                    if (!isEditable) {
                        Button(
                            onClick = { isEditable = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("编辑", fontSize = 11.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isEditable = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("取消", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                viewModel.saveConfigFile(editingFile, editorContent)
                                isEditable = false
                                Toast.makeText(context, "[$editingFile] 保存命令已发送！", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("保存", fontSize = 11.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (i in 1..maxOf(lineCount, 1)) {
                            Text(
                                text = "$i",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    TextField(
                        value = editorContent,
                        onValueChange = { if (isEditable) editorContent = it },
                        readOnly = !isEditable,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    } else {
        // 视图模式 2: 配置文件目录树与文件列表（对齐青龙/白虎官方文件管理）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索配置文件 (如 config.sh, extra.sh)...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshCurrentPanel() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (uiState.isLoading) "正在同步配置文件树..." else "暂无配置文件", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredFiles) { file ->
                            val isSh = file.endsWith(".sh")
                            val isJson = file.endsWith(".json")
                            val desc = when (file) {
                                "config.sh" -> "青龙核心环境变量与运行脚本主配置文件"
                                "extra.sh" -> "容器重启后自动执行的自定义扩展命令脚本"
                                "config.json" -> "白虎面板主系统与调度器核心配置文件"
                                "config.sample.sh" -> "官方配置样本模板文件"
                                else -> "系统服务端运行配置文件"
                            }

                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.readConfigFile(file)
                                        editorContent = uiState.configContent
                                        activeEditingFile = file
                                    },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = when {
                                                isSh -> Color(0xFF10B981).copy(alpha = 0.12f)
                                                isJson -> Color(0xFF3B82F6).copy(alpha = 0.12f)
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = when {
                                                        isSh -> Icons.Default.Terminal
                                                        isJson -> Icons.Default.DataObject
                                                        else -> Icons.Default.Description
                                                    },
                                                    contentDescription = null,
                                                    tint = when {
                                                        isSh -> Color(0xFF10B981)
                                                        isJson -> Color(0xFF3B82F6)
                                                        else -> MaterialTheme.colorScheme.primary
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(text = file, fontSize = 14.sp, style = MaterialTheme.typography.titleMedium)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isSh) "Shell" else if (isJson) "JSON" else "Conf",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(file))
                                                Toast.makeText(context, "文件名已复制", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.readConfigFile(file)
                                                editorContent = uiState.configContent
                                                activeEditingFile = file
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
