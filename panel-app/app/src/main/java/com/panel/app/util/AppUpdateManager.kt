package com.panel.app.util

import com.panel.app.BuildConfig
import com.panel.app.data.remote.NetworkClient
import com.panel.app.data.remote.api.GitHubReleaseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val releasePageUrl: String,
    val publishedAt: String
)

object AppUpdateManager {
    private const val GITHUB_OWNER = "LiYiCha"
    private const val GITHUB_REPO = "App-Panel"
    private const val GITHUB_API_BASE = "https://api.github.com/"

    // GitHub API 默认使用全局 unsafeOkHttpClient（10s 连接 / 20s 读取）
    // 国内网络访问 GitHub 常超时，提供备用 CDN 域名
    private const val GITHUB_API_FALLBACK = "https://ghproxy.com/"

    private val api: GitHubReleaseApi by lazy {
        NetworkClient.buildRetrofit(GITHUB_API_BASE).create(GitHubReleaseApi::class.java)
    }

    private val apiFallback: GitHubReleaseApi by lazy {
        NetworkClient.buildRetrofit(GITHUB_API_FALLBACK).create(GitHubReleaseApi::class.java)
    }

    /**
     * 检查 GitHub 仓库最新发布的 Release 版本
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            // 优先直连 GitHub，失败时尝试 ghproxy.com 代理
            val resp = try {
                api.getLatestRelease(GITHUB_OWNER, GITHUB_REPO)
            } catch (_: Exception) {
                apiFallback.getLatestRelease(GITHUB_OWNER, GITHUB_REPO)
            }
            if (resp.isSuccessful && resp.body() != null) {
                val release = resp.body()!!
                val currentVer = BuildConfig.VERSION_NAME
                val latestTag = release.tag_name.trim().removePrefix("v").removePrefix("V")
                val cleanCurrent = currentVer.trim().removePrefix("v").removePrefix("V")

                val hasNew = isVersionNewer(cleanCurrent, latestTag)
                val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val downloadUrl = apkAsset?.browser_download_url ?: release.html_url

                Result.success(
                    AppUpdateInfo(
                        hasUpdate = hasNew,
                        currentVersion = currentVer,
                        latestVersion = release.tag_name,
                        releaseTitle = release.name ?: release.tag_name,
                        releaseNotes = release.body ?: "暂无更新日志",
                        downloadUrl = downloadUrl,
                        releasePageUrl = release.html_url,
                        publishedAt = release.published_at?.substringBefore("T") ?: "--"
                    )
                )
            } else if (resp.code() == 404) {
                Result.success(
                    AppUpdateInfo(
                        hasUpdate = false,
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestVersion = BuildConfig.VERSION_NAME,
                        releaseTitle = "当前为最新版",
                        releaseNotes = "仓库暂未发布任何 Release 版本",
                        downloadUrl = null,
                        releasePageUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO",
                        publishedAt = "--"
                    )
                )
            } else {
                Result.failure(Exception("检查更新失败: HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接 GitHub 检查更新失败（国内网络可能需要科学上网）: ${e.message}"))
        }
    }

    /**
     * 语义化版本号对比，如 1.0.1 > 1.0.0
     */
    private fun isVersionNewer(current: String, remote: String): Boolean {
        if (current == remote) return false
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(currParts.size, remoteParts.size)

        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
