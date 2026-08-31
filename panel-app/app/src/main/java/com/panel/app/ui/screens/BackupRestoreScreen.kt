package com.panel.app.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.backup.RestoreReport
import com.panel.app.ui.viewmodel.MainViewModel

/**
 * 备份与恢复（二级页面）。
 *
 * - 备份内容：任务 / 环境变量 / 脚本 / 配置文件，可任意勾选
 * - 输出：统一 JSON 文件，通过系统文档接口保存（无需存储权限）
 * - 恢复：可跨手机、跨面板（配置文件除外，仅同类型面板）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var includeTasks by remember { mutableStateOf(true) }
    var includeEnvs by remember { mutableStateOf(true) }
    var includeScripts by remember { mutableStateOf(false) }
    var includeConfigs by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf<List<RestoreReport>>(emptyList()) }

    fun writeToUri(uri: Uri, text: String) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }.onSuccess {
            Toast.makeText(context, "备份文件已保存", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "保存失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun readFromUri(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && !isWorking) {
            isWorking = true
            viewModel.buildBackup(includeTasks, includeEnvs, includeScripts, includeConfigs) { json ->
                isWorking = false
                if (json != null) writeToUri(uri, json)
                else Toast.makeText(context, "备份内容收集失败，请检查面板连接", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = readFromUri(uri)
            if (json == null) {
                Toast.makeText(context, "读取备份文件失败", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            isWorking = true
            viewModel.restoreBackup(json) { result, error ->
                isWorking = false
                if (error != null) {
                    reports = emptyList()
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                } else {
                    reports = result
                }
            }
        }
    }

    val scriptCount = uiState.scriptTree.sumOf { node -> countFiles(node) }
    val configCount = uiState.configFiles.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("备份与恢复", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("可跨手机、跨面板迁移数据", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "选择要备份的内容（恢复时也会按同样范围导入）：",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        BackupCategoryRow(
                            checked = includeTasks,
                            onCheckedChange = { includeTasks = it },
                            icon = Icons.Default.Task,
                            title = "定时任务",
                            subtitle = "名称 / 命令 / 定时规则 / 标签 / 启停状态",
                            count = uiState.tasks.size
                        )
                        HorizontalDivider()
                        BackupCategoryRow(
                            checked = includeEnvs,
                            onCheckedChange = { includeEnvs = it },
                            icon = Icons.Default.Tune,
                            title = "环境变量",
                            subtitle = "名称 / 值 / 备注 / 启用状态",
                            count = uiState.envs.size
                        )
                        HorizontalDivider()
                        BackupCategoryRow(
                            checked = includeScripts,
                            onCheckedChange = { includeScripts = it },
                            icon = Icons.Default.Code,
                            title = "脚本文件",
                            subtitle = "脚本内容与目录结构",
                            count = scriptCount
                        )
                        HorizontalDivider()
                        BackupCategoryRow(
                            checked = includeConfigs,
                            onCheckedChange = { includeConfigs = it },
                            icon = Icons.Default.Settings,
                            title = "面板配置文件",
                            subtitle = "config.sh 等（仅同类型面板可恢复）",
                            count = configCount
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val anyChecked = includeTasks || includeEnvs || includeScripts || includeConfigs
                            if (!anyChecked) {
                                Toast.makeText(context, "请至少勾选一项备份内容", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            exportLauncher.launch("panel-backup-${System.currentTimeMillis()}.json")
                        },
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("立即备份")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("从文件恢复")
                    }
                }
            }

            if (isWorking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在处理...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (reports.isNotEmpty()) {
                item {
                    Text("恢复结果", fontSize = 12.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                items(reports, key = { it.category }) { report ->
                    RestoreReportCard(report)
                }
            }
        }
    }
}

private fun countFiles(node: com.panel.app.data.model.ScriptNode): Int =
    if (node.isDir) (node.children?.sumOf { countFiles(it) } ?: 0) else 1

@Composable
private fun BackupCategoryRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("$count 项", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RestoreReportCard(report: RestoreReport) {
    val hasError = report.failed > 0 || report.errors.isNotEmpty()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (hasError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    report.category,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "成功 ${report.success} / 跳过 ${report.skipped} / 失败 ${report.failed}",
                    fontSize = 11.sp,
                    color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (report.skipped > 0) {
                Text(
                    "跳过 ${report.skipped} 项（内容不完整或来源面板类型不匹配）",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            report.errors.take(3).forEach { err ->
                Text(
                    "• $err",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (report.errors.size > 3) {
                Text("…另有 ${report.errors.size - 3} 条错误", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
