package com.example.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubRepoMetadata(
    val fullName: String,
    val description: String,
    val defaultBranch: String,
    val openIssuesCount: Int,
    val pushedAt: String,
    val isPrivate: Boolean,
    val htmlUrl: String
)

/**
 * Optional read-only GitHub repository metadata lookup.
 * - Never embeds any personal access token in code.
 * - Never assumes access to private repositories.
 * - Accepts an optional ephemeral user-supplied read-only token strictly in memory if provided.
 */
class GitHubReadOnlyService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun parseOwnerAndRepo(url: String): Pair<String, String>? {
        val cleaned = url.trim().removeSuffix("/").removeSuffix(".git")
        val regex = Regex("""^https://github\.com/([A-Za-z0-9_.\-]+)/([A-Za-z0-9_.\-]+)(?:/.*)?$""")
        val match = regex.matchEntire(cleaned) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    suspend fun fetchPublicRepoMetadata(
        repoUrl: String,
        ephemeralReadOnlyToken: String? = null
    ): Result<GitHubRepoMetadata> = withContext(Dispatchers.IO) {
        val parsed = parseOwnerAndRepo(repoUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("URL is not a valid https://github.com/owner/repo link.")
            )
        val (owner, repo) = parsed
        try {
            val requestBuilder = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DexCommand-Android-ReadOnly")

            if (!ephemeralReadOnlyToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer ${ephemeralReadOnlyToken.trim()}")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val hint = when (response.code) {
                        404 -> "Repository not found or private (DEX//COMMAND does not assume private repo access)."
                        401, 403 -> "GitHub API authorization or rate limit returned HTTP ${response.code}."
                        else -> "GitHub API returned HTTP ${response.code}."
                    }
                    return@withContext Result.failure(IllegalStateException(hint))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                Result.success(
                    GitHubRepoMetadata(
                        fullName = json.optString("full_name", "$owner/$repo"),
                        description = json.optString("description", "").takeIf { it != "null" }.orEmpty(),
                        defaultBranch = json.optString("default_branch", "main"),
                        openIssuesCount = json.optInt("open_issues_count", 0),
                        pushedAt = json.optString("pushed_at", ""),
                        isPrivate = json.optBoolean("private", false),
                        htmlUrl = json.optString("html_url", repoUrl)
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
