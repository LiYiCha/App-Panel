package com.panel.corescript.store

import com.panel.corescript.engine.ScriptLang
import java.io.File

/** 本地脚本条目 */
data class LocalScript(
    val name: String,
    val lang: ScriptLang,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * 本地脚本文件存储：脚本保存在应用私有目录下，每个 .py / .js 文件即一个脚本。
 * 本地模块与入口脚本同目录放置，运行时由引擎按需解析。
 */
class LocalScriptStore(private val baseDir: File) {

    init {
        baseDir.mkdirs()
    }

    fun list(): List<LocalScript> {
        val files = baseDir.listFiles()?.filter {
            it.isFile && ScriptLang.fromFileName(it.name) != null
        } ?: emptyList()
        return files.map {
            LocalScript(
                name = it.name,
                lang = ScriptLang.fromFileName(it.name)!!,
                file = it,
                sizeBytes = it.length(),
                lastModified = it.lastModified()
            )
        }.sortedByDescending { it.lastModified }
    }

    fun fileFor(name: String): File = File(baseDir, name)

    fun exists(name: String): Boolean = fileFor(name).isFile

    /**
     * 新建脚本：名称缺少扩展名时自动补全；同名文件已存在则失败。
     */
    fun create(name: String, content: String, lang: ScriptLang): Result<File> {
        val normalized = normalizeName(name, lang)
            ?: return Result.failure(IllegalArgumentException("文件名不合法"))
        val file = fileFor(normalized)
        if (file.exists()) {
            return Result.failure(IllegalArgumentException("同名脚本已存在"))
        }
        return runCatching {
            file.writeText(content)
            file
        }
    }

    fun read(file: File): Result<String> = runCatching {
        require(file.parentFile == baseDir) { "路径越界" }
        file.readText()
    }

    fun write(file: File, content: String): Result<Unit> = runCatching {
        require(file.parentFile == baseDir) { "路径越界" }
        file.writeText(content)
    }

    fun delete(file: File): Result<Unit> = runCatching {
        require(file.parentFile == baseDir) { "路径越界" }
        file.delete()
    }

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1f]")

        /** 校验并规范文件名：拒绝路径分隔符与非法字符，按语言补全扩展名 */
        fun normalizeName(raw: String, lang: ScriptLang): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return null
            if (trimmed.startsWith(".")) return null
            if (ILLEGAL_NAME_CHARS.containsMatchIn(trimmed)) return null
            val name = if (trimmed.endsWith(".${lang.extension}", ignoreCase = true)) {
                trimmed.dropLast(lang.extension.length + 1) + "." + lang.extension
            } else {
                "$trimmed.${lang.extension}"
            }
            return name
        }
    }
}
