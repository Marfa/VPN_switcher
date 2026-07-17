package dev.themarfa.vpnswitcher.update

import android.util.Log
import dev.themarfa.vpnswitcher.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releasePageUrl: String,
    val releaseNotes: String,
)

object GitHubUpdater {

    private const val TAG = "GitHubUpdater"
    private const val API = "https://api.github.com/repos/${AppConstants.GITHUB_REPO}/releases/latest"

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson(API) ?: return@withContext null
            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank() || !isNewer(tag, currentVersion)) return@withContext null

            // Require a published APK asset so we don't notify on tag-only releases.
            if (findApkAsset(json.optJSONArray("assets")) == null) return@withContext null

            UpdateInfo(
                versionName = tag,
                releasePageUrl = json.optString("html_url")
                    .takeIf { it.isNotBlank() }
                    ?: "${AppConstants.GITHUB_URL}/releases/latest",
                releaseNotes = json.optString("body", ""),
            )
        } catch (e: Exception) {
            Log.w(TAG, "check failed", e)
            null
        }
    }

    private fun fetchJson(url: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        if (conn.responseCode !in 200..299) return null
        return conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    private fun findApkAsset(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** ponytail: простое сравнение x.y.z */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}
