package com.panel.corescript.deps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** 已安装的运行时依赖 */
data class PyDepInfo(
    val name: String,
    val version: String,
    /** deps 根目录下的安装目录名，卸载时使用 */
    val dirName: String,
    val sizeBytes: Long
)

/**
 * Python 运行时依赖管理：仅支持纯 Python 包（wheel 标签 *-any.whl）。
 * 下载 wheel 解压到 deps 目录，运行脚本前由引擎把该目录注入 sys.path。
 * 带 C 扩展的包无法在设备上运行时安装，需构建期预置。
 *
 * 元数据查询走 PyPI 官方 JSON API；wheel 大文件下载走镜像加速
 * （镜像路径结构与 files.pythonhosted.org 一致，仅替换域名）。
 * 镜像源通过 [mirrorProvider] 每次下载时动态获取（由应用侧镜像管理中心提供，改动即时生效）。
 */
class PyDepManager(
    private val depsDir: File,
    private val mirrorProvider: () -> String = { DEFAULT_MIRROR_HOST },
    private val client: OkHttpClient = defaultClient()
) {

    init {
        depsDir.mkdirs()
    }

    fun list(): List<PyDepInfo> {
        return depsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val meta = readDistInfo(dir) ?: return@mapNotNull null
            PyDepInfo(
                name = meta.first,
                version = meta.second,
                dirName = dir.name,
                sizeBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            )
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun totalSizeBytes(): Long =
        depsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun findByDirName(dirName: String): PyDepInfo? = list().firstOrNull { it.dirName == dirName }

    /**
     * 从 PyPI 安装指定包的最新纯 Python wheel。
     * [onProgress] 回调阶段性文本（下载进度百分比）。
     */
    suspend fun install(name: String, onProgress: (String) -> Unit = {}): Result<PyDepInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pkg = name.trim()
                require(pkg.isNotEmpty()) { "包名不能为空" }

                onProgress("查询 PyPI…")
                val release = queryLatestWheel(pkg)
                    ?: return@runCatching throw IllegalArgumentException("PyPI 上没有可用的纯 Python wheel（可能是原生扩展包，需构建期预置）")

                val filename = release.wheelFile.optString("filename")
                val dirName = filename.removeSuffix(".whl")
                val target = File(depsDir, dirName)
                if (target.exists()) target.deleteRecursively()

                onProgress("下载 ${filename}…")
                val tempFile = File.createTempFile("dep_", ".whl", depsDir)
                try {
                    download(release.wheelFile, tempFile, onProgress)
                    onProgress("解压安装…")
                    unzipTo(tempFile, target)
                } finally {
                    tempFile.delete()
                }

                val meta = readDistInfo(target)
                    ?: throw IllegalStateException("wheel 内缺少 dist-info，安装失败")

                onProgress("完成")
                PyDepInfo(meta.first, meta.second, dirName, target.walkTopDown().filter { it.isFile }.sumOf { it.length() })
            }
        }

    /** 卸载：删除安装目录 */
    suspend fun uninstall(dirName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(dirName.isNotBlank() && !dirName.contains(File.separatorChar)) { "目录名不合法" }
            val dir = File(depsDir, dirName)
            require(dir.isDirectory && dir.parentFile == depsDir) { "依赖不存在" }
            dir.deleteRecursively()
            Unit
        }
    }

    // ---------- 内部实现 ----------

    private data class LatestWheel(val version: String, val wheelFile: JSONObject)

    private fun queryLatestWheel(pkg: String): LatestWheel? {
        val json = client.newCall(
            Request.Builder()
                .url("https://pypi.org/pypi/$pkg/json")
                .header("User-Agent", REQUEST_USER_AGENT)
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }
        val root = JSONObject(json)
        val version = root.optString("version")
        val urls = root.optJSONArray("urls") ?: return null
        var wheel: JSONObject? = null
        for (i in 0 until urls.length()) {
            val item = urls.getJSONObject(i)
            val filename = item.optString("filename", "")
            if (filename.endsWith(".whl") && filename.endsWith("-any.whl")) {
                wheel = item
                break
            }
        }
        return wheel?.let { LatestWheel(version, it) }
    }

    private fun download(remote: JSONObject, target: File, onProgress: (String) -> Unit) {
        val officialUrl = remote.getString("url").substringBefore('#')
        // 镜像优先；403/404（部分镜像对个别路径拒绝）时回退官方源
        val candidates = listOf(
            officialUrl.replace(OFFICIAL_PACKAGE_HOST, mirrorProvider()),
            officialUrl
        ).distinct()
        var lastCode = 0
        for (url in candidates) {
            val code = downloadOnce(url, remote.optLong("size", -1L), target, onProgress)
            if (code == 200) return
            lastCode = code
            if (code != 403 && code != 404) break
        }
        throw IllegalStateException("下载失败 HTTP $lastCode（镜像与官方源均不可用）")
    }

    /** 单次下载；返回 HTTP 状态码，成功时写出文件 */
    private fun downloadOnce(url: String, total: Long, target: File, onProgress: (String) -> Unit): Int {
        client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", REQUEST_USER_AGENT)
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) return resp.code
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
            return 200
        }
    }

    /** 解压 wheel 到目标目录，做 zip-slip 防护 */
    private fun unzipTo(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        val canonicalBase = targetDir.canonicalPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(targetDir, entry.name)
                if (!out.canonicalPath.startsWith(canonicalBase)) {
                    throw SecurityException("wheel 内路径越界：${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** 读取目录下首个 dist-info/METADATA 的 Name 与 Version */
    private fun readDistInfo(dir: File): Pair<String, String>? {
        val distInfo = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.endsWith(".dist-info") }
            ?: return null
        val metadata = File(distInfo, "METADATA")
        if (!metadata.isFile) return null
        var name: String? = null
        var version: String? = null
        metadata.useLines { lines ->
            for (line in lines) {
                when {
                    name == null && line.startsWith("Name:", ignoreCase = true) ->
                        name = line.substringAfter(':').trim()
                    version == null && line.startsWith("Version:", ignoreCase = true) ->
                        version = line.substringAfter(':').trim()
                }
                if (name != null && version != null) break
            }
        }
        val n = name ?: return null
        return n to (version ?: "?")
    }

    companion object {
        /** PyPI 官方 wheel 下载域名 */
        private const val OFFICIAL_PACKAGE_HOST = "https://files.pythonhosted.org"

        /** 默认 wheel 下载镜像（清华 TUNA，国内加速） */
        const val DEFAULT_MIRROR_HOST = "https://pypi.tuna.tsinghua.edu.cn"

        /** 请求 UA：部分镜像 CDN 拒绝非浏览器 UA（okhttp 默认 UA 会 403） */
        private const val REQUEST_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
