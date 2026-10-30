package com.panel.corescript.deps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** 已安装的 JS 运行时依赖 */
data class JsDepInfo(
    val name: String,
    val version: String,
    /** deps 根目录下的安装目录名，卸载时使用 */
    val dirName: String,
    val sizeBytes: Long,
    /** 是否为用户主动安装的顶层包（false = 随顶层包带入的传递依赖） */
    val isTopLevel: Boolean = true,
    /** 顶层包带入的传递依赖数量（非顶层包为 0） */
    val depCount: Int = 0
)

/**
 * JS 运行时依赖管理（对标青龙 npm 生态）：从 npm registry 下载纯 JS 包
 * （含递归 dependencies），解包 tarball 到 deps 目录，运行脚本时由 QuickJS
 * 引擎注册为 CommonJS 模块，脚本内 require('包名') 即可使用。
 *
 * 目录布局：js_deps/{name}@{version}/package 内容（package/ 前缀已剥除），
 * 作用域包名中的 '/' 编码为 %2F。
 * 原生扩展包（.node）与构建产物无法在设备上运行，仅适合纯 JS 包。
 *
 * registry 查询与 tarball 下载优先走 [mirrorProvider] 返回的镜像源，
 * 失败自动回退 npm 官方源；镜像源由应用侧镜像管理中心提供，改动即时生效。
 */
class JsDepManager(
    private val depsDir: File,
    private val mirrorProvider: () -> String = { DEFAULT_REGISTRY },
    private val client: OkHttpClient = defaultClient()
) {

    init {
        depsDir.mkdirs()
    }

    fun list(): List<JsDepInfo> {
        val manifest = readManifest()
        // 顶层包集合：manifest 记录且目录仍存在；无 manifest（旧版本数据）时全部视为顶层
        val topSet = if (manifest.top.isEmpty()) emptySet()
        else manifest.top.filter { File(depsDir, it).isDirectory }.toSet()
        val noManifest = manifest.top.isEmpty()
        return depsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val decoded = decodeDirName(dir.name) ?: return@mapNotNull null
            val name = decoded.first
            val version = decoded.second
            val pkgJson = File(dir, PACKAGE_ENTRY)
            val metaName = runCatching {
                JSONObject(pkgJson.readText()).optString("name", name)
            }.getOrDefault(name)
            val isTop = noManifest || dir.name in topSet
            val depCount = if (!isTop) 0
            else (manifest.deps[dir.name] ?: emptyList()).count { File(depsDir, it).isDirectory }
            JsDepInfo(
                name = metaName,
                version = version,
                dirName = dir.name,
                sizeBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                isTopLevel = isTop,
                depCount = depCount
            )
        }?.sortedWith(
            compareByDescending<JsDepInfo> { it.isTopLevel }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
    }

    fun findByDirName(dirName: String): JsDepInfo? = list().firstOrNull { it.dirName == dirName }

    /**
     * 从 npm registry 安装指定包的最新版本及其 dependencies 依赖树。
     * 顶层包与其传递依赖记入清单（_manifest.json）：列表只展示顶层包，
     * 卸载顶层包时自动清理不再被其他顶层包引用的传递依赖。
     * [onProgress] 回调阶段性文本。
     */
    suspend fun install(name: String, onProgress: (String) -> Unit = {}): Result<JsDepInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pkg = name.trim()
                require(pkg.isNotEmpty() && !pkg.contains(Regex("\\s"))) { "包名不合法" }
                val tree = mutableMapOf<String, String>() // pkg -> dirName（含顶层自身）
                val rootInfo = installRecursive(pkg, onProgress, tree)
                rootInfo ?: throw IllegalStateException("安装失败：$pkg")
                // 记录清单：顶层包 + 其依赖树
                val manifest = readManifest()
                val deps = tree.values.filter { it != rootInfo.dirName }
                val newTop = (manifest.top + rootInfo.dirName).distinct()
                val newDeps = manifest.deps + (rootInfo.dirName to deps)
                writeManifest(newTop, newDeps)
                rootInfo
            }
        }

    /**
     * 卸载指定依赖。若为顶层包：连同其独占的传递依赖一并删除（仍被其他顶层包
     * 引用的依赖保留），并更新清单；若为传递依赖/旧数据目录：仅删除该目录。
     */
    suspend fun uninstall(dirName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(dirName.isNotBlank() && !dirName.contains(File.separatorChar)) { "目录名不合法" }
            val dir = File(depsDir, dirName)
            require(dir.isDirectory && dir.parentFile == depsDir) { "依赖不存在" }
            val manifest = readManifest()
            if (dirName in manifest.top) {
                val myDeps = (manifest.deps[dirName] ?: emptyList()).toSet()
                val otherTop = manifest.top - dirName
                val protected = otherTop.toSet() + otherTop.flatMap { manifest.deps[it] ?: emptyList() }
                val orphans = (myDeps - protected).filter { File(depsDir, it).isDirectory }
                dir.deleteRecursively()
                orphans.forEach { File(depsDir, it).deleteRecursively() }
                writeManifest(
                    top = otherTop,
                    deps = manifest.deps - dirName - orphans.toSet()
                )
            } else {
                dir.deleteRecursively()
            }
            Unit
        }
    }

    // ---------- 顶层包清单（对标青龙的依赖列表语义：只记录用户主动安装的包） ----------

    private data class Manifest(val top: List<String>, val deps: Map<String, List<String>>)

    private fun manifestFile() = File(depsDir, MANIFEST_FILE)

    private fun readManifest(): Manifest {
        val f = manifestFile()
        if (!f.isFile) return Manifest(emptyList(), emptyMap())
        return runCatching {
            val root = JSONObject(f.readText())
            val top = mutableListOf<String>()
            val topArr = root.optJSONArray("top")
            if (topArr != null) for (i in 0 until topArr.length()) top.add(topArr.getString(i))
            val deps = mutableMapOf<String, List<String>>()
            val depsObj = root.optJSONObject("deps")
            if (depsObj != null) {
                for (key in depsObj.keys()) {
                    val arr = depsObj.optJSONArray(key) ?: continue
                    deps[key] = (0 until arr.length()).map { arr.getString(it) }
                }
            }
            Manifest(top, deps)
        }.getOrDefault(Manifest(emptyList(), emptyMap()))
    }

    private fun writeManifest(top: List<String>, deps: Map<String, List<String>>) {
        runCatching {
            val root = JSONObject()
            root.put("top", org.json.JSONArray(top))
            val depsObj = JSONObject()
            deps.forEach { (k, v) -> depsObj.put(k, org.json.JSONArray(v)) }
            root.put("deps", depsObj)
            manifestFile().writeText(root.toString(2))
        }
    }

    // ---------- 递归安装 ----------

    private fun installRecursive(
        pkg: String,
        onProgress: (String) -> Unit,
        tree: MutableMap<String, String> // pkg -> dirName，安装结束后供清单记录
    ): JsDepInfo? {
        onProgress("查询 registry：$pkg")
        val meta = queryLatest(pkg)
            ?: throw IllegalArgumentException("registry 上找不到 $pkg（或为原生扩展包）")

        val dirName = dirNameOf(pkg, meta.version)
        // 循环依赖/重复包：已出现在树中则跳过
        if (tree.putIfAbsent(pkg, dirName) != null) return null
        val target = File(depsDir, dirName)
        if (target.exists()) {
            onProgress("已安装：$pkg@${meta.version}，跳过")
            return JsDepInfo(pkg, meta.version, dirName, target.walkTopDown().filter { it.isFile }.sumOf { it.length() })
        }

        onProgress("下载 $pkg@${meta.version}…")
        val tempFile = File.createTempFile("jsdep_", ".tgz", depsDir)
        try {
            downloadWithFallback(meta.tarballUrl, tempFile, onProgress)
            onProgress("解压 $pkg…")
            extractTarGz(tempFile, target)
        } finally {
            tempFile.delete()
        }

        val pkgJsonFile = File(target, PACKAGE_ENTRY)
        if (!pkgJsonFile.isFile) throw IllegalStateException("$pkg 压缩包内缺少 package.json")
        val pkgJson = JSONObject(pkgJsonFile.readText())

        // 递归安装 dependencies（按最新版本，忽略版本范围约束）
        val deps = pkgJson.optJSONObject("dependencies")
        if (deps != null && deps.length() > 0) {
            val names = (0 until deps.length()).map { deps.names().getString(it) }
            onProgress("$pkg 有 ${names.size} 个依赖")
            for (child in names) {
                installRecursive(child, onProgress, tree)
            }
        }

        return JsDepInfo(pkg, meta.version, dirName, target.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }

    private data class LatestMeta(val version: String, val tarballUrl: String)

    private fun queryLatest(pkg: String): LatestMeta? {
        val path = "/${java.net.URLEncoder.encode(pkg, "UTF-8").replace("+", "%2F")}"
        // 镜像 registry 优先，查询失败回退官方源
        val registries = listOf(mirrorProvider().trimEnd('/'), DEFAULT_REGISTRY).distinct()
        for (registry in registries) {
            val meta = runCatching { queryFrom("$registry$path") }.getOrNull() ?: continue
            return meta
        }
        return null
    }

    private fun queryFrom(url: String): LatestMeta? {
        val json = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }
        val root = JSONObject(json)
        val version = root.optJSONObject("dist-tags")?.optString("latest") ?: return null
        val verMeta = root.optJSONObject("versions")?.optJSONObject(version) ?: return null
        val tarball = verMeta.optJSONObject("dist")?.optString("tarball") ?: return null
        return LatestMeta(version, tarball)
    }

    /**
     * 下载 tarball：将官方 registry 域名替换为所选镜像源优先尝试，
     * 镜像不可用（HTTP 失败/网络异常）时回退原始地址。
     */
    private fun downloadWithFallback(url: String, target: File, onProgress: (String) -> Unit) {
        val candidates = listOf(
            url.replace(DEFAULT_REGISTRY, mirrorProvider().trimEnd('/')),
            url
        ).distinct()
        var last: Exception? = null
        for (candidate in candidates) {
            try {
                download(candidate, target, onProgress)
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw IllegalStateException("下载失败（镜像与官方源均不可用）：${last?.message}", last)
    }

    private fun download(url: String, target: File, onProgress: (String) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败 HTTP ${resp.code}")
            val total = resp.body?.contentLength() ?: -1L
            resp.body?.byteStream()?.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress("下载 $pct%")
                            }
                        }
                    }
                }
            } ?: throw IllegalStateException("下载失败：空响应")
        }
    }

    // ---------- tar.gz 解包（自实现最小 ustar 读取，支持 GNU 长名与 PAX path） ----------

    private fun extractTarGz(tgz: File, targetDir: File) {
        targetDir.mkdirs()
        val canonicalBase = targetDir.canonicalPath
        var pendingName: String? = null
        GZIPInputStream(tgz.inputStream().buffered()).use { gzip ->
            while (true) {
                val header = ByteArray(BLOCK).also { b ->
                    var off = 0
                    while (off < BLOCK) {
                        val n = gzip.read(b, off, BLOCK - off)
                        if (n < 0) return
                        off += n
                    }
                }
                if (header.all { it == 0.toByte() }) return

                var name = readString(header, 0, 100)
                val size = readOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                if (readString(header, 257, 6) == "ustar") {
                    val prefix = readString(header, 345, 155)
                    if (prefix.isNotEmpty()) name = "$prefix/$name"
                }
                pendingName?.let {
                    name = it
                    pendingName = null
                }

                when (type) {
                    // GNU 长文件名：内容是下一个条目的名字
                    'L' -> {
                        pendingName = readContent(gzip, size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                        skipPadding(gzip, size)
                        continue
                    }
                    // PAX 扩展头：解析 path= 覆盖下一个条目名
                    'x', 'g' -> {
                        val pax = readContent(gzip, size).toString(Charsets.UTF_8)
                        pendingName = pax.lineSequence()
                            .firstOrNull { it.contains(" path=") || it.startsWith("path=") }
                            ?.substringAfter("path=")
                            ?.trimEnd('\n')
                        skipPadding(gzip, size)
                        continue
                    }
                    '0', '\u0000' -> {
                        val rel = stripPackagePrefix(name)
                        val out = File(targetDir, rel)
                        if (!out.canonicalPath.startsWith(canonicalBase)) {
                            throw SecurityException("tarball 内路径越界：$name")
                        }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { copyN(gzip, it, size) }
                    }
                    '5' -> {
                        val rel = stripPackagePrefix(name)
                        val out = File(targetDir, rel)
                        if (!out.canonicalPath.startsWith(canonicalBase)) {
                            throw SecurityException("tarball 内路径越界：$name")
                        }
                        out.mkdirs()
                    }
                    else -> copyN(gzip, discardStream(), size)
                }
                // 普通条目内容按 512 字节对齐，跳过尾部 padding
                skipPadding(gzip, size)
            }
        }
    }

    /** tar 条目内容后不足 512 的部分为 padding，必须跳过以对齐下一个头 */
    private fun skipPadding(input: java.io.InputStream, size: Long) {
        var pad = ((BLOCK - (size % BLOCK)) % BLOCK).toInt()
        while (pad > 0) {
            val n = input.read(ByteArray(pad))
            require(n >= 0) { "tar 流意外结束" }
            pad -= n
        }
    }

    private fun stripPackagePrefix(name: String): String =
        if (name.startsWith("package/")) name.removePrefix("package/")
        else name.substringAfter("package/", name)

    private fun readContent(input: java.io.InputStream, size: Long): ByteArray {
        val bytes = ByteArray(size.toInt())
        var off = 0
        while (off < size) {
            val n = input.read(bytes, off, (size - off).toInt())
            require(n >= 0) { "tar 流意外结束" }
            off += n
        }
        return bytes
    }

    private fun copyN(input: java.io.InputStream, output: java.io.OutputStream, size: Long) {
        var remaining = size
        val buf = ByteArray(64 * 1024)
        while (remaining > 0) {
            val want = minOf(remaining, buf.size.toLong()).toInt()
            val n = input.read(buf, 0, want)
            require(n >= 0) { "tar 流意外结束" }
            output.write(buf, 0, n)
            remaining -= n
        }
    }

    /** 丢弃内容的空输出流（跳过链接等无需落盘的条目） */
    private fun discardStream(): java.io.OutputStream = object : java.io.OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }

    private fun readString(header: ByteArray, off: Int, len: Int): String =
        header.drop(off).take(len).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

    private fun readOctal(header: ByteArray, off: Int, len: Int): Long {
        var v = 0L
        for (b in header.copyOfRange(off, off + len)) {
            if (b == 0.toByte() || b == ' '.code.toByte()) break
            v = v * 8 + (b - '0'.code.toByte())
        }
        return v
    }

    // ---------- 目录名编解码 ----------

    private fun dirNameOf(pkg: String, version: String) = "${pkg.replace("/", "%2F")}@$version"

    private fun decodeDirName(dirName: String): Pair<String, String>? {
        val idx = dirName.lastIndexOf('@')
        if (idx <= 0) return null
        val name = dirName.substring(0, idx).takeIf { it.isNotBlank() } ?: return null
        val version = dirName.substring(idx + 1)
        return name.replace("%2F", "/") to version
    }

    companion object {
        private const val BLOCK = 512

        /** 解包后包内容所在文件 */
        const val PACKAGE_ENTRY = "package.json"

        /** 顶层包清单：列表只显示用户主动安装的包，卸载时自动清理孤儿传递依赖 */
        const val MANIFEST_FILE = "_manifest.json"

        /** npm 官方 registry */
        const val DEFAULT_REGISTRY = "https://registry.npmjs.org"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
