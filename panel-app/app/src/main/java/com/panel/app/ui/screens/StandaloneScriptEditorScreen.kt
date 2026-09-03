package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.components.CodeSyntaxVisualTransformation
import com.panel.app.ui.viewmodel.MainViewModel

/** 只读预览时单次渲染的最大行数，其余行由用户手动"加载更多"追加 */
private const val MAX_INITIAL_LINES = 200
/** 每次点击"加载更多"追加的行数 */
private const val LOAD_MORE_LINES = 200

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneScriptEditorScreen(
    scriptName: String,
    initialContent: String = "",
    viewModel: MainViewModel,
    onSave: ((String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    BackHandler {
        onBack()
    }

    var searchQuery by remember { mutableStateOf("") }
    var fontSizeSp by remember { mutableIntStateOf(12) }
    var showSearchBar by remember { mutableStateOf(false) }
    var isEditable by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    // 若外部未传入 onSave，则使用 ViewModel 内嵌的保存逻辑
    val effectiveOnSave = onSave ?: { _ -> }

    // 优先使用 ViewModel 缓存，避免重复请求
    val cachedContent = uiState.scriptViewerCache[scriptName] ?: initialContent

    // 本地编辑状态只在切换脚本时重建；不能用 cachedContent 作为 key，
    // 否则 uiState 的任何后台刷新都会静默丢弃用户正在编辑的内容（表现为界面闪一下、内容跳回旧版本）
    var codeContent by remember(scriptName) { mutableStateOf(cachedContent) }
    var isDirty by remember(scriptName) { mutableStateOf(false) }

    // 服务端内容到达后同步到本地；用户一旦编辑过就不再覆盖
    LaunchedEffect(cachedContent) {
        if (!isDirty && cachedContent != codeContent) {
            codeContent = cachedContent
        }
    }

    val totalLines = remember(codeContent) {
        if (codeContent.isEmpty()) 1 else codeContent.lines().size
    }

    // 只读预览的分页行数；编辑态必须持有完整文本，因此不受分页限制
    var visibleLineCount by remember(scriptName) { mutableIntStateOf(MAX_INITIAL_LINES) }
    val query = searchQuery.trim()

    // 只读预览下按关键词过滤，并保留原始行号；编辑态一律使用完整文本（过滤会破坏可编辑内容）
    val previewRows = remember(codeContent, query, isEditable) {
        if (isEditable) {
            null
        } else {
            codeContent.lines().let { lines ->
                if (query.isEmpty()) {
                    lines.mapIndexed { index, line -> (index + 1) to line }
                } else {
                    lines.mapIndexedNotNull { index, line ->
                        if (line.contains(query, ignoreCase = true)) (index + 1) to line else null
                    }
                }
            }
        }
    }

    val matchedCount = previewRows?.size ?: totalLines
    val effectiveLineCount = if (isEditable) totalLines else minOf(visibleLineCount, matchedCount)
    val hasMoreLines = !isEditable && matchedCount > effectiveLineCount

    // 只读预览只把前 N 行交给 BasicTextField。
    // BasicTextField 不是懒加载组件，会把传入的文本一次性完整布局；
    // 直接喂入数千行会让布局高度膨胀到数万像素，主线程被拖死并最终出现渲染异常（花屏）。
    val displayText = remember(previewRows, effectiveLineCount) {
        if (previewRows == null) {
            codeContent
        } else {
            previewRows.take(effectiveLineCount).joinToString("\n") { it.second }
        }
    }

    val lineNumbersText = remember(previewRows, effectiveLineCount) {
        if (previewRows == null) {
            (1..effectiveLineCount).joinToString("\n")
        } else {
            previewRows.take(effectiveLineCount).joinToString("\n") { it.first.toString() }
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }
    val syntaxTransformation = remember(scriptName, isDark) {
        CodeSyntaxVisualTransformation(scriptName, isDark)
    }

    // 行号与正文必须使用完全一致的 lineHeight，否则行号会与代码逐行错位
    val lineHeightSp = (fontSizeSp * 1.35f).sp
    val scrollState = rememberScrollState()

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
                            clipboardManager.setText(AnnotatedString(codeContent))
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
                            onClick = {
                                isEditable = true
                                showSearchBar = false
                            },
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
                                effectiveOnSave(codeContent)
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
            // 仅在只读预览态提供过滤搜索：编辑态必须展示完整文本，过滤会破坏可编辑内容
            if (showSearchBar && !isEditable) {
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

            // 代码区：行号与正文处于同一个滚动容器中，滚动天然同步、行号严格对齐；
            // 滚动容器同时为 BasicTextField 提供"内容高度自适应"的测量环境。
            // pointerInput 放在 verticalScroll 之前（外层），保证单指滚动优先被 scroll 消费，
            // 双指捏合才进入字号缩放。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) {
                                val target = (fontSizeSp * zoom).coerceIn(9f, 26f).toInt()
                                // 值未变化时不要写 state，避免手势期间产生无意义的重组
                                if (target != fontSizeSp) fontSizeSp = target
                            }
                        }
                    }
                    .verticalScroll(scrollState)
            ) {
                if (codeContent.isEmpty()) {
                    // 注意：此处处于 verticalScroll 内部，高度约束是无限的，不能使用 fillMaxSize
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(
                                "正在从服务端读取脚本内容...",
                                fontSize = fontSizeSp.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        // 行号栏：与正文行数、字号、行高完全一致
                        Text(
                            text = lineNumbersText,
                            fontSize = fontSizeSp.sp,
                            lineHeight = lineHeightSp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(36.dp)
                                .padding(horizontal = 6.dp)
                        )

                        // 核心代码区：BasicTextField 自带长按选择 / 复制能力，
                        // 不需要再套 SelectionContainer（两者会争抢同一份 Selection 与 TextLayoutResult，
                        // 造成选中高亮残影、光标漂移，甚至长按时崩溃）。
                        BasicTextField(
                            value = displayText,
                            onValueChange = {
                                if (isEditable) {
                                    codeContent = it
                                    isDirty = true
                                }
                            },
                            readOnly = !isEditable,
                            visualTransformation = if (isEditable) VisualTransformation.None else syntaxTransformation,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            textStyle = TextStyle(
                                fontSize = fontSizeSp.sp,
                                lineHeight = lineHeightSp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            if (hasMoreLines) {
                TextButton(
                    onClick = { visibleLineCount += LOAD_MORE_LINES },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "加载更多（剩余 ${matchedCount - effectiveLineCount} 行）",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
