package dev.themarfa.vpnswitcher.update

import android.util.Log
import dev.themarfa.vpnswitcher.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
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

            val apkUrl = findApkAsset(json.optJSONArray("assets"))
                ?: return@withContext null

            UpdateInfo(
                versionName = tag,
                downloadUrl = apkUrl,
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

    suspend fun downloadApk(url: String, target: File, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                target.parentFile?.mkdirs()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val total = conn.contentLengthLong.coerceAtLeast(1L)
                conn.inputStream.use { input ->
                    FileOutputStream(target).use { out ->
                        val buf = ByteArray(16_384)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } >= 0) {
                            out.write(buf, 0, read)
                            done += read
                            onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                false
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
