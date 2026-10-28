package com.panel.corescript.store

import com.panel.corescript.engine.ScriptLang
import org.json.JSONObject
import java.io.File

/** 本地脚本条目；folder 为虚拟分组（不改文件布局，归属存元数据） */
data class LocalScript(
    val name: String,
    val lang: ScriptLang,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long,
    val folder: String = ""
)

/**
 * 本地脚本文件存储：脚本保存在应用私有目录下，每个 .py / .js 文件即一个脚本。
 * 本地模块与入口脚本同目录放置，运行时由引擎按需解析。
 * 文件夹为虚拟分组：归属关系存 sidecar 元数据（.folders.json），脚本文件始终平铺，
 * 保证运行时同目录 require/import 语义不变。
 */
class LocalScriptStore(private val baseDir: File) {

    init {
        baseDir.mkdirs()
    }

    fun list(): List<LocalScript> {
        val meta = readMeta().map
        val files = baseDir.listFiles()?.filter {
            it.isFile && ScriptLang.fromFileName(it.name) != null
        } ?: emptyList()
        return files.map {
            LocalScript(
                name = it.name,
                lang = ScriptLang.fromFileName(it.name)!!,
                file = it,
                sizeBytes = it.length(),
                lastModified = it.lastModified(),
                folder = meta[it.name] ?: ""
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
        removeMeta(file.name)
    }

    // ---------- 虚拟文件夹 ----------

    /** 全部文件夹名（登记列表 + 已归类脚本的文件夹，去重保序） */
    fun folders(): List<String> {
        val meta = readMeta()
        return (meta.folders + meta.map.values.filter { it.isNotBlank() }).distinct()
    }

    /** 新建空文件夹 */
    fun createFolder(name: String): Result<Unit> = runCatching {
        val normalized = normalizeFolderName(name) ?: throw IllegalArgumentException("文件夹名不合法")
        val meta = readMeta()
        if (normalized !in meta.folders) {
            writeMeta(meta.copy(folders = meta.folders + normalized, map = meta.map))
        }
    }

    /** 移动脚本到文件夹；folder 为空表示移回未分类 */
    fun moveTo(name: String, folder: String): Result<Unit> = runCatching {
        require(fileFor(name).isFile) { "脚本不存在" }
        val meta = readMeta()
        val newMap = if (folder.isBlank()) meta.map - name else meta.map + (name to folder.trim())
        writeMeta(meta.copy(map = newMap))
    }

    /**
     * 合并导入的文件夹归属（zip 导入用）：仅对已存在的脚本生效，
     * 不存在的脚本条目忽略；目标文件夹自动登记。
     */
    fun mergeFolders(entries: Map<String, String>) {
        if (entries.isEmpty()) return
        val meta = readMeta()
        val applied = entries.filterKeys { fileFor(it).isFile }.filterValues { it.isNotBlank() }
        if (applied.isEmpty()) return
        val folders = (meta.folders + applied.values).distinct()
        writeMeta(meta.copy(folders = folders, map = meta.map + applied))
    }

    /** 删除文件夹：组内脚本移回未分类 */
    fun deleteFolder(folder: String): Result<Unit> = runCatching {
        require(folder.isNotBlank()) { "文件夹名不合法" }
        val meta = readMeta()
        writeMeta(meta.copy(folders = meta.folders - folder, map = meta.map.filterValues { it != folder }))
    }

    /** 重命名文件夹：组内脚本全部迁移 */
    fun renameFolder(old: String, new: String): Result<Unit> = runCatching {
        val normalized = normalizeFolderName(new) ?: throw IllegalArgumentException("文件夹名不合法")
        require(old.isNotBlank()) { "文件夹名不合法" }
        val meta = readMeta()
        writeMeta(
            meta.copy(
                folders = (meta.folders - old + normalized).distinct(),
                map = meta.map.mapValues { if (it.value == old) normalized else it.value }
            )
        )
    }

    // ---------- 元数据读写 ----------

    private data class Meta(val folders: List<String>, val map: Map<String, String>)

    private fun metaFile(): File = File(baseDir, META_FILE)

    private fun readMeta(): Meta {
        val json = runCatching { JSONObject(metaFile().readText()) }.getOrNull() ?: return Meta(emptyList(), emptyMap())
        val mapObj = json.optJSONObject("map")
        return if (mapObj == null) {
            // 旧版 flat 格式：{script: folder}
            val names = json.names()
            if (names == null) {
                Meta(emptyList(), emptyMap())
            } else {
                val flat = (0 until names.length()).mapNotNull { i ->
                    val key = names.getString(i)
                    json.optString(key, "").takeIf { it.isNotBlank() }?.let { key to it }
                }.toMap()
                Meta(flat.values.filter { it.isNotBlank() }.distinct(), flat)
            }
        } else {
            val folderArr = json.optJSONArray("folders")
            val mapNames = mapObj.names()
            Meta(
                folders = (0 until (folderArr?.length() ?: 0)).map { folderArr!!.getString(it) },
                map = if (mapNames == null) emptyMap() else (0 until mapNames.length()).associate { i ->
                    val key = mapNames.getString(i)
                    key to mapObj.optString(key, "")
                }
            )
        }
    }

    private fun writeMeta(meta: Meta) {
        val mapObj = JSONObject()
        meta.map.forEach { (k, v) -> mapObj.put(k, v) }
        val folderArr = org.json.JSONArray()
        meta.folders.forEach { folderArr.put(it) }
        metaFile().writeText(JSONObject().put("folders", folderArr).put("map", mapObj).toString())
    }

    private fun removeMeta(name: String) {
        val meta = readMeta()
        if (meta.map.containsKey(name)) {
            writeMeta(meta.copy(map = meta.map - name))
        }
    }

    /** 供 zip 导出：返回元数据 JSON 原文；尚无元数据时返回空串 */
    fun exportMetaJson(): String = runCatching { metaFile().readText() }.getOrDefault("")

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1f]")

        const val META_FILE = ".folders.json"

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

        /** 校验文件夹名：复用文件名规则（不含扩展名处理） */
        fun normalizeFolderName(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return null
            if (ILLEGAL_NAME_CHARS.containsMatchIn(trimmed)) return null
            return trimmed
        }
    }
}
