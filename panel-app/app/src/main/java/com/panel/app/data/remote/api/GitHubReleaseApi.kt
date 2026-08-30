package com.panel.app.data.remote.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long? = null,
    val content_type: String? = null
)

data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val published_at: String? = null,
    val html_url: String,
    val assets: List<GitHubAsset>? = null
)

interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRelease>
}
