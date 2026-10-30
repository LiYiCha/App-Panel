package com.panel.corescript.store

import com.panel.corescript.engine.ScriptLang
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 本地脚本条目；folder 为虚拟分组的完整路径（如 "util/notify"，空串=未分类） */
data class LocalScript(
    val name: String,
    val lang: ScriptLang,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long,
    val folder: String = ""
)

/** 文件夹树节点：path 完整路径、depth 根级为 0、subFolders 直接子文件夹、scripts 直属脚本名 */
data class FolderNode(
    val path: String,
    val depth: Int,
    val subFolders: List<String>,
    val scripts: List<String>
)

/**
 * 本地脚本文件存储：脚本保存在应用私有目录下，每个 .py / .js 文件即一个脚本。
 * 本地模块与入口脚本同目录放置，运行时由引擎按需解析。
 * 文件夹为虚拟分组：归属关系存 sidecar 元数据（.folders.json），支持多级嵌套路径
 * （如 "util/notify"，旧版单级名即根级路径），脚本文件始终平铺，
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

    /**
     * 重命名脚本：新名缺扩展名时按原语言补全，显式扩展名必须与原语言一致；
     * 同名目标已存在则失败；成功后把旧名的文件夹归属迁移到新名。
     */
    fun rename(name: String, newName: String): Result<Unit> = runCatching {
        val oldFile = fileFor(name)
        require(oldFile.isFile) { "脚本不存在" }
        val lang = ScriptLang.fromFileName(name) ?: throw IllegalArgumentException("脚本类型无法识别")
        val ext = newName.trim().substringAfterLast('.', "")
        require(ext.isEmpty() || ext.equals(lang.extension, ignoreCase = true)) {
            "扩展名需与脚本类型一致（.${lang.extension}）"
        }
        val normalized = normalizeName(newName, lang) ?: throw IllegalArgumentException("文件名不合法")
        if (normalized == name) return@runCatching
        val target = fileFor(normalized)
        require(!target.exists()) { "同名脚本已存在" }
        if (!oldFile.renameTo(target)) throw IllegalStateException("重命名失败")
        val meta = readMeta()
        val folder = meta.map[name]
        if (folder != null) {
            writeMeta(meta.copy(map = (meta.map - name) + (normalized to folder)))
        }
    }

    // ---------- 虚拟文件夹（多级嵌套路径） ----------

    /** 全部文件夹路径（登记列表 + 已归类脚本的路径，含隐式祖先，去重保序） */
    fun folders(): List<String> {
        val meta = readMeta()
        val all = linkedSetOf<String>()
        (meta.folders + meta.map.values.filter { it.isNotBlank() }).forEach { addWithAncestors(all, it) }
        return all.toList()
    }

    /**
     * 文件夹层级结构：深度优先前序（父节点在子节点之前）。
     * 每个节点含完整路径、深度（根级为 0）、直接子文件夹、直属脚本。
     */
    fun folderTree(): List<FolderNode> {
        val meta = readMeta()
        val all = linkedSetOf<String>()
        meta.folders.forEach { addWithAncestors(all, it) }
        meta.map.values.forEach { if (it.isNotBlank()) addWithAncestors(all, it) }
        val roots = mutableListOf<String>()
        val children = mutableMapOf<String, MutableList<String>>()
        all.sorted().forEach { path ->
            val idx = path.lastIndexOf('/')
            if (idx < 0) roots += path
            else children.getOrPut(path.substring(0, idx)) { mutableListOf() } += path
        }
        val scriptsByFolder = meta.map.filterValues { it.isNotBlank() }
            .entries.groupBy({ it.value }, { it.key })
        val nodes = mutableListOf<FolderNode>()
        fun walk(path: String, depth: Int) {
            val subs = children[path].orEmpty()
            nodes += FolderNode(path, depth, subs, scriptsByFolder[path].orEmpty())
            subs.forEach { walk(it, depth + 1) }
        }
        roots.forEach { walk(it, 0) }
        return nodes
    }

    /** 新建文件夹：parent 为空表示根目录，缺失的祖先路径一并登记 */
    fun createFolder(name: String, parent: String = ""): Result<Unit> = runCatching {
        val segment = normalizeFolderName(name) ?: throw IllegalArgumentException("文件夹名不合法")
        val parentPath = if (parent.isBlank()) "" else
            normalizeFolderPath(parent)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("父目录不合法")
        val path = if (parentPath.isEmpty()) segment else "$parentPath/$segment"
        val meta = readMeta()
        val folders = meta.folders.toMutableSet()
        addWithAncestors(folders, path)
        writeMeta(meta.copy(folders = folders.toList()))
    }

    /** 移动脚本到文件夹路径；folder 为空表示移回未分类，目标路径自动登记 */
    fun moveTo(name: String, folder: String): Result<Unit> = runCatching {
        require(fileFor(name).isFile) { "脚本不存在" }
        val path = normalizeFolderPath(folder) ?: throw IllegalArgumentException("文件夹路径不合法")
        val meta = readMeta()
        if (path.isEmpty()) {
            writeMeta(meta.copy(map = meta.map - name))
        } else {
            val folders = meta.folders.toMutableSet()
            addWithAncestors(folders, path)
            writeMeta(meta.copy(folders = folders.toList(), map = meta.map + (name to path)))
        }
    }

    /**
     * 合并导入的文件夹归属（zip 导入用）：仅对已存在的脚本生效，
     * 不存在的脚本条目忽略；目标路径（含祖先）自动登记。
     */
    fun mergeFolders(entries: Map<String, String>) {
        if (entries.isEmpty()) return
        val meta = readMeta()
        val applied = entries.filterKeys { fileFor(it).isFile }
            .mapNotNull { (k, v) -> normalizeFolderPath(v)?.takeIf { it.isNotBlank() }?.let { k to it } }
            .toMap()
        if (applied.isEmpty()) return
        val folders = meta.folders.toMutableSet()
        applied.values.forEach { addWithAncestors(folders, it) }
        writeMeta(meta.copy(folders = folders.toList(), map = meta.map + applied))
    }

    /** 删除文件夹：级联删除整棵子树，树下脚本移回未分类 */
    fun deleteFolder(folder: String): Result<Unit> = runCatching {
        val path = normalizeFolderPath(folder)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("文件夹名不合法")
        val meta = readMeta()
        writeMeta(
            meta.copy(
                folders = meta.folders.filterNot { it == path || it.startsWith("$path/") },
                map = meta.map.filterValues { it != path && !it.startsWith("$path/") }
            )
        )
    }

    /** 重命名/移动文件夹：new 为新完整路径，整棵子树按前缀一起迁移 */
    fun renameFolder(old: String, new: String): Result<Unit> = runCatching {
        val oldPath = normalizeFolderPath(old)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("文件夹名不合法")
        val newPath = normalizeFolderPath(new)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("文件夹名不合法")
        if (newPath == oldPath) return@runCatching
        val meta = readMeta()
        val existing = meta.folders + meta.map.values
        require(oldPath in existing) { "文件夹不存在" }
        require(!newPath.startsWith("$oldPath/")) { "不能移动到自身的子目录" }
        require(newPath !in existing) { "目标文件夹已存在" }
        fun migrate(p: String) = when {
            p == oldPath -> newPath
            p.startsWith("$oldPath/") -> newPath + p.substring(oldPath.length)
            else -> p
        }
        val folders = meta.folders.map { migrate(it) }.toMutableSet()
        addWithAncestors(folders, newPath)
        writeMeta(meta.copy(folders = folders.toList(), map = meta.map.mapValues { (_, v) -> migrate(v) }))
    }

    // ---------- 元数据读写 ----------

    private data class Meta(val folders: List<String>, val map: Map<String, String>)

    private fun metaFile(): File = File(baseDir, META_FILE)

    private fun readMeta(): Meta {
        val json = runCatching { JSONObject(metaFile().readText()) }.getOrNull() ?: return Meta(emptyList(), emptyMap())
        val mapObj = json.optJSONObject("map")
        val parsed = if (mapObj == null) {
            // 最旧的 flat 格式：{script: folder}
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
        // 兼容迁移：旧格式/单级名规范化为路径（单级名读取时即根级路径），首次读取静默回写
        val migrated = Meta(
            folders = parsed.folders.mapNotNull {
                normalizeFolderPath(it)?.takeIf { p -> p.isNotBlank() }
            }.distinct(),
            map = parsed.map.mapNotNull { (k, v) ->
                normalizeFolderPath(v)?.takeIf { p -> p.isNotBlank() }?.let { k to it }
            }.toMap()
        )
        if (json.optInt("version", 1) < META_VERSION || migrated != parsed) {
            runCatching { writeMeta(migrated) }
        }
        return migrated
    }

    private fun writeMeta(meta: Meta) {
        val mapObj = JSONObject()
        meta.map.forEach { (k, v) -> mapObj.put(k, v) }
        val folderArr = JSONArray()
        meta.folders.forEach { folderArr.put(it) }
        metaFile().writeText(
            JSONObject().put("version", META_VERSION).put("folders", folderArr).put("map", mapObj).toString()
        )
    }

    private fun removeMeta(name: String) {
        val meta = readMeta()
        if (meta.map.containsKey(name)) {
            writeMeta(meta.copy(map = meta.map - name))
        }
    }

    /** 把路径及其全部祖先（util → util/notify）加入集合 */
    private fun addWithAncestors(sink: MutableSet<String>, path: String) {
        var acc = ""
        path.split('/').forEach { seg ->
            if (seg.isBlank()) return@forEach
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            sink += acc
        }
    }

    /** 供 zip 导出：返回元数据 JSON 原文；尚无元数据时返回空串 */
    fun exportMetaJson(): String = runCatching { metaFile().readText() }.getOrDefault("")

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1f]")

        const val META_FILE = ".folders.json"

        /** 元数据版本：v2 起 folders/map 存多级路径，旧版本首次读取静默迁移 */
        private const val META_VERSION = 2

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

        /**
         * 校验文件夹路径：按 '/' 分段去空白后逐段校验。
         * 空白输入返回空串（根路径），含非法段返回 null。
         */
        fun normalizeFolderPath(raw: String): String? {
            val segments = raw.split('/').map { it.trim() }.filter { it.isNotEmpty() }
            if (segments.isEmpty()) return ""
            if (segments.any { normalizeFolderName(it) == null }) return null
            return segments.joinToString("/")
        }
    }
}
