package com.ndregil.inchat.data.network.update

import com.ndregil.inchat.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubReleaseAssetDto(
    @Json(name = "name") val name: String = "",
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "size") val size: Long = 0L
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "prerelease") val prerelease: Boolean = false,
    @Json(name = "draft") val draft: Boolean = false,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "assets") val assets: List<GitHubReleaseAssetDto> = emptyList()
)

interface GitHubReleaseService {
    @GET("repos/ndregil/InChat/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseDto

    companion object {
        const val BASE_URL = "https://api.github.com/"

        fun createMoshi(): Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "InChat-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            })
            .build()

        fun create(
            baseUrl: String = BASE_URL,
            okHttpClient: OkHttpClient = createOkHttpClient(),
            moshi: Moshi = createMoshi()
        ): GitHubReleaseService {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GitHubReleaseService::class.java)
        }
    }
}

object AppVersionComparator {

    /**
     * Strips any leading "v" or "V" and surrounding whitespace from a release tag or version string.
     */
    fun stripLeadingV(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Returns true if [remoteTag] represents a strictly newer version than [installedVersionName].
     * Leading "v"/"V" is stripped from both inputs before comparing numeric components.
     */
    fun isNewerVersion(remoteTag: String, installedVersionName: String): Boolean {
        val cleanRemote = stripLeadingV(remoteTag)
        val cleanInstalled = stripLeadingV(installedVersionName)
        if (cleanRemote.isBlank()) return false
        if (cleanInstalled.isBlank()) return true

        val (remoteCore, remotePre) = splitCoreAndPreRelease(cleanRemote)
        val (installedCore, installedPre) = splitCoreAndPreRelease(cleanInstalled)

        val remoteParts = parseNumericParts(remoteCore)
        val installedParts = parseNumericParts(installedCore)

        if (remoteParts.isEmpty()) return false
        if (installedParts.isEmpty()) return true

        val maxLen = maxOf(remoteParts.size, installedParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = installedParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // Core numeric versions are equal: stable release > pre-release
        if (remotePre == null && installedPre != null) return true
        if (remotePre != null && installedPre == null) return false
        if (remotePre != null && installedPre != null) {
            return comparePreRelease(remotePre, installedPre) > 0
        }
        return false
    }

    private fun splitCoreAndPreRelease(version: String): Pair<String, String?> {
        val withoutBuildMeta = version.substringBefore("+")
        val dashIndex = withoutBuildMeta.indexOf('-')
        return if (dashIndex >= 0) {
            withoutBuildMeta.substring(0, dashIndex) to
                withoutBuildMeta.substring(dashIndex + 1).ifBlank { null }
        } else {
            withoutBuildMeta to null
        }
    }

    private fun parseNumericParts(core: String): List<Int> {
        return core.split(".")
            .mapNotNull { segment ->
                val digits = segment.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
    }

    private fun comparePreRelease(remotePre: String, installedPre: String): Int {
        val rParts = remotePre.split(".")
        val iParts = installedPre.split(".")
        val maxLen = maxOf(rParts.size, iParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrNull(i) ?: return -1
            val c = iParts.getOrNull(i) ?: return 1
            val rNum = r.toIntOrNull()
            val cNum = c.toIntOrNull()
            val cmp = when {
                rNum != null && cNum != null -> rNum.compareTo(cNum)
                rNum != null -> -1 // numeric identifiers have lower precedence than non-numeric
                cNum != null -> 1
                else -> r.compareTo(c, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
