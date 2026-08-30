package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerLogsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenLogViewer: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var logTree by remember { mutableStateOf<com.google.gson.JsonElement?>(null) }
    var activeLogPath by remember { mutableStateOf<String?>(null) }
    var activeLogContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isReadingFile by remember { mutableStateOf(false) }

    val reloadTree = {
        isLoading = true
        viewModel.loadServerLogsTree { elem ->
            logTree = elem
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reloadTree()
    }

    BackHandler {
        if (activeLogPath != null) {
            activeLogPath = null
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeLogPath != null) activeLogPath!! else "服务端系统日志中心",
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeLogPath != null) {
                            activeLogPath = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (activeLogPath != null) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(activeLogContent))
                            Toast.makeText(context, "日志内容已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制日志")
                        }
                    } else {
                        IconButton(onClick = {
                            reloadTree()
                            Toast.makeText(context, "正在刷新服务端日志列表...", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (activeLogPath != null) {
                // 日志内容阅读器界面
                if (isReadingFile) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        val lines = remember(activeLogContent) { activeLogContent.lines() }
                        if (lines.isEmpty() || activeLogContent.isBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("该日志文件内容为空", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(lines) { line ->
                                    Text(
                                        text = line,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 日志文件树列表界面
                val fileList = remember(logTree) {
                    val list = mutableListOf<Pair<String, String>>()
                    val arr = if (logTree?.isJsonObject == true && logTree!!.asJsonObject.has("data")) {
                        logTree!!.asJsonObject.get("data").asJsonArray
                    } else if (logTree?.isJsonArray == true) {
                        logTree!!.asJsonArray
                    } else null

                    arr?.forEach { elem ->
                        if (elem.isJsonObject) {
                            val obj = elem.asJsonObject
                            val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "未命名"
                            val isDir = obj.get("type")?.asString == "directory" || obj.has("children")
                            if (isDir && obj.has("children") && obj.get("children").isJsonArray) {
                                obj.get("children").asJsonArray.forEach { sub ->
                                    if (sub.isJsonObject) {
                                        val subTitle = sub.asJsonObject.get("title")?.asString ?: sub.asJsonObject.get("name")?.asString ?: "log"
                                        list.add(Pair(title, subTitle))
                                    }
                                }
                            } else {
                                list.add(Pair("", title))
                            }
                        }
                    }
                    list
                }

                if (fileList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("暂无服务端日志文件", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(fileList) { (dir, filename) ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val full = if (dir.isEmpty()) filename else "$dir/$filename"
                                        onOpenLogViewer(filename, full)
                                    },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text(text = filename, fontSize = 13.sp, style = MaterialTheme.typography.bodyMedium)
                                            if (dir.isNotEmpty()) {
                                                Text(text = "目录: $dir", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
