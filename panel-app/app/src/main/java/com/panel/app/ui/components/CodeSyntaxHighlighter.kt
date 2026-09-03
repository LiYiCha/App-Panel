package com.panel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 专为 Android 移动端优化的轻量、高性能代码语法高亮转换器。
 * 支持 JavaScript / TypeScript、Python、Shell / Bash、JSON 等主流脚本语言语法色彩。
 * 基于 Compose VisualTransformation 实现：不修改底层文本，保留 100% 原生输入法、光标与复制粘贴体验。
 *
 * 性能约定（重要）：
 * - VisualTransformation.filter 会在**每一次重组、每一次按键**时被调用，必须保持低开销；
 * - 所有正则在 [Companion.getLangRules] 中按语言**预编译并缓存**，绝不在 filter 内编译正则；
 * - 字符串 / 注释的覆盖范围使用一次性布尔掩码记录，查询 O(1)，整体复杂度保持 O(n)；
 * - 超过 [MAX_HIGHLIGHT_CHARS] 的文本直接跳过着色，避免长脚本阻塞主线程造成掉帧 / 渲染异常。
 */
class CodeSyntaxVisualTransformation(
    private val extension: String,
    private val isDark: Boolean = true
) : VisualTransformation {

    // 经典现代化 IDE 代码色彩主题（VS Code 风格），SpanStyle 预先构建，避免在 filter 中反复创建
    private val stringSpan = SpanStyle(color = if (isDark) Color(0xFFCE9178) else Color(0xFFA31515))
    private val commentSpan = SpanStyle(
        color = if (isDark) Color(0xFF6A9955) else Color(0xFF008000),
        fontStyle = FontStyle.Italic
    )
    private val keywordSpan = SpanStyle(
        color = if (isDark) Color(0xFFC586C0) else Color(0xFFAF00DB),
        fontWeight = FontWeight.SemiBold
    )
    private val controlSpan = SpanStyle(
        color = if (isDark) Color(0xFF569CD6) else Color(0xFF0000FF),
        fontWeight = FontWeight.Bold
    )
    private val booleanSpan = SpanStyle(color = if (isDark) Color(0xFF4EC9B0) else Color(0xFF267F99))
    private val numberSpan = SpanStyle(color = if (isDark) Color(0xFFB5CEA8) else Color(0xFF098658))
    private val funcSpan = SpanStyle(color = if (isDark) Color(0xFFDCDCAA) else Color(0xFF795E26))

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        // 空文本或超长文本直接放弃着色：长脚本的全量扫描会阻塞主线程导致掉帧甚至渲染异常
        if (raw.isEmpty() || raw.length > MAX_HIGHLIGHT_CHARS) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val ext = extension.lowercase().substringAfterLast('.')
        val rules = getLangRules(ext)

        // 覆盖掩码：true 表示该字符位于字符串或注释内部，不参与后续着色
        val masked = BooleanArray(raw.length)

        val annotated = buildAnnotatedString {
            append(raw)

            // 1. 字符串（优先级最高，先圈定范围）
            rules.stringPattern.forEachMatch(raw) { start, end ->
                addStyle(stringSpan, start, end)
                Arrays.fill(masked, start, end, true)
            }

            // 2. 注释（不覆盖字符串内部的 # 与 //）
            rules.commentPattern.forEachMatch(raw) { start, end ->
                if (!masked.getOrFalse(start)) {
                    addStyle(commentSpan, start, end)
                    Arrays.fill(masked, start, end, true)
                }
            }

            // 3. 关键字 / 控制流 / 布尔常量
            rules.keywordPattern?.forEachMatch(raw) { start, end ->
                if (!masked[start]) addStyle(keywordSpan, start, end)
            }
            rules.controlPattern?.forEachMatch(raw) { start, end ->
                if (!masked[start]) addStyle(controlSpan, start, end)
            }
            rules.booleanPattern?.forEachMatch(raw) { start, end ->
                if (!masked[start]) addStyle(booleanSpan, start, end)
            }

            // 4. 数字
            NUMBER_PATTERN.forEachMatch(raw) { start, end ->
                if (!masked[start]) addStyle(numberSpan, start, end)
            }

            // 5. 函数调用名 (如 console.log、def foo()、func())
            FUNC_PATTERN.forEachMatch(raw) { start, end ->
                if (!masked[start]) addStyle(funcSpan, start, end)
            }
        }

        // 文本长度未变化，使用 Identity 映射，保证光标与选区偏移正确
        return TransformedText(annotated, OffsetMapping.Identity)
    }

    /** 遍历 [input] 中所有匹配项；跳过落在文本末尾的零宽匹配，避免索引越界 */
    private fun Pattern.forEachMatch(input: String, action: (start: Int, end: Int) -> Unit) {
        val matcher = matcher(input)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start < input.length && start <= end) {
                action(start, end)
            }
        }
    }

    private fun BooleanArray.getOrFalse(index: Int): Boolean = index in indices && this[index]

    companion object {
        /** 单次着色的字符上限，超过该长度只保留纯文本，优先保证滚动与输入的流畅度 */
        private const val MAX_HIGHLIGHT_CHARS = 200_000

        private val STRING_PATTERN: Pattern = Pattern.compile("\"[^\"\n]*\"|'[^'\n]*'|`[^`]*`")
        private val COMMENT_PATTERN_HASH: Pattern = Pattern.compile("#[^\n]*")
        private val COMMENT_PATTERN_SLASH: Pattern = Pattern.compile("//[^\n]*|/\\*[\\s\\S]*?\\*/")
        private val NUMBER_PATTERN: Pattern = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b")
        private val FUNC_PATTERN: Pattern = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()")

        /** 语言规则缓存：正则编译开销大，按扩展名缓存复用 */
        private val LANG_CACHE = ConcurrentHashMap<String, LangRules>()

        private fun getLangRules(ext: String): LangRules = LANG_CACHE.getOrPut(ext) {
            val (keywords, controls, booleans) = getKeywordsForLang(ext)
            LangRules(
                stringPattern = STRING_PATTERN,
                commentPattern = if (ext == "py" || ext == "sh" || ext == "bash") {
                    COMMENT_PATTERN_HASH
                } else {
                    COMMENT_PATTERN_SLASH
                },
                keywordPattern = keywords.toWordPattern(),
                controlPattern = controls.toWordPattern(),
                booleanPattern = booleans.toWordPattern()
            )
        }

        private fun List<String>.toWordPattern(): Pattern? =
            if (isEmpty()) null else Pattern.compile("\\b(?:${joinToString("|")})\\b")

        private fun getKeywordsForLang(ext: String): Triple<List<String>, List<String>, List<String>> {
            return when (ext) {
                "py" -> Triple(
                    listOf("def", "class", "import", "from", "lambda", "pass", "global", "nonlocal", "with", "as", "yield"),
                    listOf("if", "elif", "else", "for", "while", "try", "except", "finally", "raise", "return", "in", "is", "not", "and", "or", "break", "continue"),
                    listOf("True", "False", "None", "self")
                )
                "sh", "bash" -> Triple(
                    listOf("function", "export", "source", "local", "alias", "set", "unset", "readonly", "echo", "exit"),
                    listOf("if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "return"),
                    listOf("true", "false")
                )
                "json" -> Triple(
                    emptyList(),
                    emptyList(),
                    listOf("true", "false", "null")
                )
                else -> Triple( // 默认 js / ts
                    listOf("const", "let", "var", "function", "class", "import", "export", "from", "async", "await", "new", "this", "typeof", "instanceof", "default"),
                    listOf("return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw"),
                    listOf("true", "false", "null", "undefined", "NaN", "Infinity", "console", "require", "process")
                )
            }
        }
    }

    private data class LangRules(
        val stringPattern: Pattern,
        val commentPattern: Pattern,
        val keywordPattern: Pattern?,
        val controlPattern: Pattern?,
        val booleanPattern: Pattern?
    )
}

/**
 * 具有行号槽位、语法高亮与轻量现代感的代码编辑器组件。
 *
 * 行号与正文位于**同一个滚动容器**内共享滚动位置，保证两者严格对齐；
 * 滚动容器同时为 BasicTextField 提供有界的内容测量环境，避免超长文本一次性撑出巨大布局。
 */
@Composable
fun CodeEditorView(
    code: String,
    onCodeChange: (String) -> Unit,
    fileName: String,
    modifier: Modifier = Modifier,
    isEditable: Boolean = true,
    fontSizeSp: Int = 12
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        // 简易判断暗色主题
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val visualTransformation = remember(fileName, isDark) {
        CodeSyntaxVisualTransformation(fileName, isDark)
    }

    val lineCount = remember(code) {
        if (code.isEmpty()) 1 else code.lines().size
    }

    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    // 行号与正文必须使用完全一致的 lineHeight，否则行号会与代码逐行错位
    val lineHeightSp = (fontSizeSp * 1.4f).sp
    val dividerColor = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF8F9FA)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 1. 左侧代码行号栏（分隔线用 drawBehind 绘制，避免在无界高度的滚动容器内使用 fillMaxHeight）
                Text(
                    text = lineNumbersText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    lineHeight = lineHeightSp,
                    color = if (isDark) Color(0xFF858585) else Color(0xFFA0A0A0),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .padding(start = 8.dp, end = 6.dp)
                        .drawBehind {
                            val x = size.width + 4.dp.toPx()
                            drawLine(
                                color = dividerColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                )

                // 2. 右侧代码正文区（与行号栏共享滚动，去掉无效的 horizontalScroll）
                BasicTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    readOnly = !isEditable,
                    visualTransformation = visualTransformation,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = lineHeightSp,
                        color = if (isDark) Color(0xFFD4D4D4) else Color(0xFF1E1E1E)
                    ),
                    cursorBrush = SolidColor(if (isDark) Color(0xFF569CD6) else MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp)
                )
            }
        }
    }
}
