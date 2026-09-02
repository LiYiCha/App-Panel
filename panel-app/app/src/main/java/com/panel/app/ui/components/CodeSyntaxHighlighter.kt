package com.panel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

/**
 * 专为 Android 移动端优化的轻量、高性能代码语法高亮转换器。
 * 支持 JavaScript / TypeScript、Python、Shell / Bash、JSON 等主流脚本语言语法色彩。
 * 基于 Compose VisualTransformation 实现：不修改底层文本，保留 100% 原生输入法、光标与复制粘贴体验。
 */
class CodeSyntaxVisualTransformation(
    private val extension: String,
    private val isDark: Boolean = true
) : VisualTransformation {

    // 经典现代化 IDE 代码色彩主题（VS Code 风格）
    private val keywordColor = if (isDark) Color(0xFFC586C0) else Color(0xFFAF00DB)     // 紫色关键字
    private val controlColor = if (isDark) Color(0xFF569CD6) else Color(0xFF0000FF)     // 蓝色控制字
    private val stringColor = if (isDark) Color(0xFFCE9178) else Color(0xFFA31515)      // 暖橙/砖红字符串
    private val commentColor = if (isDark) Color(0xFF6A9955) else Color(0xFF008000)     // 绿色注释
    private val numberColor = if (isDark) Color(0xFFB5CEA8) else Color(0xFF098658)      // 柔绿数字
    private val booleanColor = if (isDark) Color(0xFF4EC9B0) else Color(0xFF267F99)     // 青色布尔/内置常量
    private val decoratorColor = if (isDark) Color(0xFFDCDCAA) else Color(0xFF795E26)   // 金黄装饰器/函数名

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val ext = extension.lowercase().substringAfterLast('.')
        val annotated = buildAnnotatedString {
            append(raw)

            // 1. 高亮字符串 (最高优先级之一，先于关键字处理)
            val stringRegex = Pattern.compile("(\"[^\"]*\"|'[^']*'|`[^`]*`)")
            val stringMatcher = stringRegex.matcher(raw)
            val stringRanges = mutableListOf<IntRange>()
            while (stringMatcher.find()) {
                val start = stringMatcher.start()
                val end = stringMatcher.end()
                addStyle(SpanStyle(color = stringColor), start, end)
                stringRanges.add(start until end)
            }

            // 2. 高亮单行与多行注释
            val commentPattern = when (ext) {
                "py", "sh" -> Pattern.compile("(#.*$)")
                else -> Pattern.compile("(//.*$|/\\*[\\s\\S]*?\\*/)")
            }
            val commentMatcher = commentPattern.matcher(raw)
            val commentRanges = mutableListOf<IntRange>()
            while (commentMatcher.find()) {
                val start = commentMatcher.start()
                val end = commentMatcher.end()
                // 确保不是字符串里的 # 或 //
                if (stringRanges.none { it.contains(start) }) {
                    addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), start, end)
                    commentRanges.add(start until end)
                }
            }

            fun isInsideStringOrComment(idx: Int): Boolean {
                return stringRanges.any { it.contains(idx) } || commentRanges.any { it.contains(idx) }
            }

            // 3. 语言专用关键字与控制流
            val (keywords, controls, booleans) = getKeywordsForLang(ext)

            // 关键字正则
            if (keywords.isNotEmpty()) {
                val kwPattern = Pattern.compile("\\b(${keywords.joinToString("|")})\\b")
                val kwMatcher = kwPattern.matcher(raw)
                while (kwMatcher.find()) {
                    val start = kwMatcher.start()
                    val end = kwMatcher.end()
                    if (!isInsideStringOrComment(start)) {
                        addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), start, end)
                    }
                }
            }

            // 控制流关键字
            if (controls.isNotEmpty()) {
                val ctrlPattern = Pattern.compile("\\b(${controls.joinToString("|")})\\b")
                val ctrlMatcher = ctrlPattern.matcher(raw)
                while (ctrlMatcher.find()) {
                    val start = ctrlMatcher.start()
                    val end = ctrlMatcher.end()
                    if (!isInsideStringOrComment(start)) {
                        addStyle(SpanStyle(color = controlColor, fontWeight = FontWeight.Bold), start, end)
                    }
                }
            }

            // 布尔值与常量
            if (booleans.isNotEmpty()) {
                val boolPattern = Pattern.compile("\\b(${booleans.joinToString("|")})\\b")
                val boolMatcher = boolPattern.matcher(raw)
                while (boolMatcher.find()) {
                    val start = boolMatcher.start()
                    val end = boolMatcher.end()
                    if (!isInsideStringOrComment(start)) {
                        addStyle(SpanStyle(color = booleanColor), start, end)
                    }
                }
            }

            // 4. 数字
            val numPattern = Pattern.compile("\\b(\\d+(\\.\\d+)?)\\b")
            val numMatcher = numPattern.matcher(raw)
            while (numMatcher.find()) {
                val start = numMatcher.start()
                val end = numMatcher.end()
                if (!isInsideStringOrComment(start)) {
                    addStyle(SpanStyle(color = numberColor), start, end)
                }
            }

            // 5. 函数调用名匹配 (如 console.log, def foo(), func())
            val funcPattern = Pattern.compile("(\\b[a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()")
            val funcMatcher = funcPattern.matcher(raw)
            while (funcMatcher.find()) {
                val start = funcMatcher.start(1)
                val end = funcMatcher.end(1)
                if (!isInsideStringOrComment(start)) {
                    addStyle(SpanStyle(color = decoratorColor), start, end)
                }
            }
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }

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

/**
 * 具有行号槽位、语法高亮与轻量现代感的代码编辑器组件
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

    val lines = remember(code) {
        if (code.isEmpty()) listOf("") else code.lines()
    }

    val lineNumbersText = remember(lines.size) {
        (1..lines.size).joinToString("\n")
    }

    val horizontalScrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF8F9FA)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // 1. 左侧代码行号栏
            Text(
                text = lineNumbersText,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 6).sp,
                color = if (isDark) Color(0xFF858585) else Color(0xFFA0A0A0),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .widthIn(min = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )

            // 分割细线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))
            )

            // 2. 右侧代码正文区 (带语法高亮与横向平滑滚动)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(horizontalScrollState)
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    readOnly = !isEditable,
                    visualTransformation = visualTransformation,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + 6).sp,
                        color = if (isDark) Color(0xFFD4D4D4) else Color(0xFF1E1E1E)
                    ),
                    cursorBrush = SolidColor(if (isDark) Color(0xFF569CD6) else MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
