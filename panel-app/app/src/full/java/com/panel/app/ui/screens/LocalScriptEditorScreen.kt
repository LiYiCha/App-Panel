package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.ui.components.CodeEditorView
import com.panel.app.util.LocalScriptKit
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.runner.ScriptRunnerService

/**
 * 本地脚本编辑器：与远程脚本编辑器独立的轻量实现，
 * 直接读写设备私有目录文件，支持保存与一键运行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScriptEditorScreen(
    scriptName: String,
    onBack: () -> Unit,
    onOpenConsole: (String) -> Unit
) {
    val context = LocalContext.current

    var content by remember(scriptName) { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(scriptName) {
        LocalScriptKit.store.read(LocalScriptKit.store.fileFor(scriptName)).onSuccess {
            content = it
            loaded = true
        }.onFailure { e ->
            Toast.makeText(context, e.message ?: "读取失败", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    fun save(): Boolean {
        if (!loaded) return false
        val ok = LocalScriptKit.store.write(LocalScriptKit.store.fileFor(scriptName), content)
        dirty = false
        return ok.isSuccess
    }

    // 有未保存修改时返回需要确认，避免改动静默丢失
    BackHandler(enabled = dirty) { showExitConfirm = true }
    BackHandler(enabled = !dirty) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column {
                        Text(scriptName, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text(if (dirty) "未保存更改" else "本地脚本编辑", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (dirty) showExitConfirm = true else onBack()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    ActionButtonSmall(
                        icon = Icons.Default.Save,
                        label = "保存",
                        tint = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            if (save()) Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ActionButtonSmall(
                        icon = Icons.Default.PlayArrow,
                        label = "运行",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            if (save()) {
                                val lang = ScriptLang.fromFileName(scriptName)
                                    ?: return@ActionButtonSmall
                                val runId = ScriptRunnerService.start(context, lang, LocalScriptKit.store.fileFor(scriptName))
                                onOpenConsole(runId)
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            CodeEditorView(
                code = content,
                onCodeChange = {
                    content = it
                    dirty = true
                },
                fileName = scriptName,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Text(
                "提示：同目录内的其他 .js / .py 文件可作为模块 import，运行时自动解析。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("未保存的修改", fontSize = 15.sp) },
            text = { Text("$scriptName 有未保存的修改，退出将丢失。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    save()
                    onBack()
                }) { Text("保存并退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitConfirm = false
                        dirty = false
                        onBack()
                    }) { Text("不保存", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showExitConfirm = false }) { Text("取消") }
                }
            }
        )
    }
}
