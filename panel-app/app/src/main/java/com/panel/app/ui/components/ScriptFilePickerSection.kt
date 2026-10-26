package com.panel.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 新建/编辑定时任务共用的「浏览脚本库」选择器：
 * 头部标签 + 收起/展开按钮，展开后为脚本文件列表；
 * 选中文件后回调任务名（去后缀）与按后缀建议的执行命令（.py/.js/.sh）。
 */
@Composable
fun ScriptFilePickerSection(
    scriptFiles: List<String>,
    onPicked: (taskName: String, suggestedCommand: String) -> Unit,
    modifier: Modifier = Modifier,
    labelText: String = "选择脚本文件:"
) {
    var showFilePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(labelText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { showFilePicker = !showFilePicker },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showFilePicker) "收起列表" else "浏览脚本库", fontSize = 10.sp)
            }
        }

        if (showFilePicker) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(4.dp)) {
                    items(scriptFiles) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val baseName = file.substringAfterLast("/")
                                    val suggestedCommand = when {
                                        file.endsWith(".py") -> "python3 $file"
                                        file.endsWith(".js") -> "node $file"
                                        file.endsWith(".sh") -> "bash $file"
                                        else -> "python3 $file"
                                    }
                                    onPicked(baseName.substringBeforeLast("."), suggestedCommand)
                                    showFilePicker = false
                                }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(file, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
