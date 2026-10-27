package com.panel.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.components.ActionButtonSmall
import com.panel.app.util.LocalScriptKit
import com.panel.corescript.engine.OutputStream
import com.panel.corescript.runner.ConsoleLine
import com.panel.corescript.runner.RunStatus
import com.panel.corescript.runner.ScriptRunBus
import com.panel.corescript.runner.ScriptRunnerService
import java.io.File

/**
 * 本地脚本运行控制台：实时展示运行输出，支持停止；
 * 打开历史运行时从运行日志文件回放。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalRunConsoleScreen(
    runId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    val currentRun by ScriptRunBus.currentRun.collectAsState()
    val revision by ScriptRunBus.revision.collectAsState()

    // 正在查看的运行：当前总线上是本运行则实时读取，否则回放日志文件
    val liveRun = currentRun?.takeIf { runId == null || it.runId == runId }
    val isLive = liveRun != null && currentRun?.runId == runId

    val lines: List<ConsoleLine> = if (isLive) {
        remember(revision) { ScriptRunBus.currentLines }
    } else {
        remember(runId) { runId?.let { readRunLogFile(context, it) } ?: emptyList() }
    }

    val status = liveRun?.status ?: RunStatus.FAILED // 无状态且无日志时按失败展示占位
    val listState = rememberLazyListState()
    LaunchedEffect(revision, lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

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
                        Text(
                            liveRun?.scriptName ?: "运行控制台",
                            fontSize = 15.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("本地脚本执行输出", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    if (isLive && status == RunStatus.RUNNING && liveRun != null) {
                        ActionButtonSmall(
                            icon = Icons.Default.Stop,
                            label = "停止",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { ScriptRunnerService.stop(context, liveRun.runId) }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (liveRun != null) {
                RunStatusBar(
                    scriptName = liveRun.scriptName,
                    status = status,
                    detail = liveRun.finishedDetail
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                if (lines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无输出",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (line.stream == OutputStream.STDERR) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatusBar(scriptName: String, status: RunStatus, detail: String?) {
    val (label, color) = when (status) {
        RunStatus.RUNNING -> "运行中" to MaterialTheme.colorScheme.primary
        RunStatus.SUCCESS -> "成功" to MaterialTheme.colorScheme.primary
        RunStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        RunStatus.STOPPED -> "已停止" to MaterialTheme.colorScheme.onSurfaceVariant
    }
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
            if (status == RunStatus.RUNNING) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(scriptName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                detail?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

/** 解析运行日志文件回放历史输出，与 ScriptRunnerService 的落盘格式一致 */
private fun readRunLogFile(context: android.content.Context, runId: String): List<ConsoleLine> {
    val file: File = ScriptRunnerService.runLogFile(context, runId)
    if (!file.isFile) return emptyList()
    return file.readLines().mapNotNull { raw ->
        if (raw.length < 12 || !raw.startsWith("[")) return@mapNotNull null
        val isErr = raw.contains("] [err] ")
        val text = if (isErr) raw.substringAfter("] [err] ") else raw.substringAfter("] ", "")
        ConsoleLine(
            time = raw.substringBefore(']').removePrefix("["),
            stream = if (isErr) OutputStream.STDERR else OutputStream.STDOUT,
            text = text
        )
    }
}
