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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.panel.app.util.LocalScriptKit
import com.panel.corescript.engine.OutputStream
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.runner.ConsoleLine
import com.panel.corescript.runner.RunStatus
import com.panel.corescript.runner.ScriptRunBus
import com.panel.corescript.runner.ScriptRunnerService

/**
 * 本地脚本运行控制台：实时展示运行输出；
 * 顶栏右侧操作：运行中 暂停/继续（仅 Python）+ 停止，结束后可一键重新运行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalRunConsoleScreen(
    runId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    // 控制台内重新运行时切换关注对象（新 runId 覆盖路由参数）
    var activeRunId by remember { mutableStateOf(runId) }

    val currentRun by ScriptRunBus.currentRun.collectAsState()
    val revision by ScriptRunBus.revision.collectAsState()

    // 正在查看的运行：当前总线上是本运行则实时读取，否则回放日志文件
    val liveRun = currentRun?.takeIf { activeRunId == null || it.runId == activeRunId }
    val isLive = liveRun != null && currentRun?.runId == activeRunId

    val lines: List<ConsoleLine> = if (isLive) {
        remember(revision) { ScriptRunBus.currentLines }
    } else {
        remember(activeRunId) { activeRunId?.let { LocalScriptKit.runLogStore(context).read(it) } ?: emptyList() }
    }

    val status = liveRun?.status ?: RunStatus.FAILED // 无状态且无日志时按失败展示占位
    val isRunning = status == RunStatus.RUNNING || status == RunStatus.PAUSED
    val listState = rememberLazyListState()
    LaunchedEffect(revision, lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    // 从输出定位脚本：解析「== 运行开始 xxx [Python/JavaScript] ==」行后在脚本库中查找文件
    val rerunScript = remember(lines) { resolveRerunScript(lines) }

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
                            liveRun?.scriptName ?: rerunScript?.first?.name ?: "运行控制台",
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
                    if (isLive && isRunning) {
                        // 运行中：暂停/继续（仅 Python）+ 停止
                        if (liveRun.lang == ScriptLang.PYTHON) {
                            if (status == RunStatus.RUNNING) {
                                IconButton(
                                    onClick = { ScriptRunnerService.pause(context, liveRun.runId) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "暂停", modifier = Modifier.size(20.dp))
                                }
                            } else {
                                IconButton(
                                    onClick = { ScriptRunnerService.resume(context, liveRun.runId) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "继续", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        IconButton(
                            onClick = { ScriptRunnerService.stop(context, liveRun.runId) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "停止",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (rerunScript != null) {
                        // 已结束且能定位脚本：重新运行
                        IconButton(
                            onClick = {
                                val (script, _) = rerunScript
                                activeRunId = ScriptRunnerService.start(context, script.lang, script.file)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "运行 ${rerunScript.first.name}",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        },
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
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(lines) { line ->
                            ConsoleLineRow(line)
                        }
                    }
                }
            }
        }
    }
}

/** 单行日志：时间戳淡化小字 + 正文（stderr 红色） */
@Composable
private fun ConsoleLineRow(line: ConsoleLine) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = line.time,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = line.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (line.stream == OutputStream.STDERR) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 从日志行解析可重跑的脚本：匹配「== 运行开始 名称 [语言] ==」，
 * 在本地脚本库中按文件名查找，语言按扩展名核对。
 */
private fun resolveRerunScript(lines: List<ConsoleLine>): Pair<com.panel.corescript.store.LocalScript, ScriptLang>? {
    if (lines.isEmpty()) return null
    val regex = Regex("^== 运行开始 (.+) \\[(.+)\\] ==$")
    val startLine = lines.firstOrNull { regex.matches(it.text) } ?: return null
    val match = regex.find(startLine.text) ?: return null
    val (name, langLabel) = match.destructured
    val script = LocalScriptKit.store.list().firstOrNull { it.name == name } ?: return null
    val lang = ScriptLang.fromFileName(script.name)
        ?: ScriptLang.entries.firstOrNull { it.label == langLabel }
        ?: return null
    return script to lang
}

@Composable
private fun RunStatusBar(scriptName: String, status: RunStatus, detail: String?) {
    val (label, color) = when (status) {
        RunStatus.RUNNING -> "运行中" to MaterialTheme.colorScheme.primary
        RunStatus.PAUSED -> "已暂停（计时继续）" to MaterialTheme.colorScheme.tertiary
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
