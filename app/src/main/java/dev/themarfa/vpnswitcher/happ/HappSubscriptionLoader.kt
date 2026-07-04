package dev.themarfa.vpnswitcher.happ

import android.content.Context
import dev.themarfa.vpnswitcher.prefs.AppPreferences
import java.net.HttpURLConnection
import java.net.URL

object HappSubscriptionLoader {

    fun loadServers(context: Context, prefs: AppPreferences, happUri: String): List<HappServerEntry> {
        val subscriptionUrl = HappSubscriptionParser.extractSubscriptionUrl(happUri)
        val bytes = fetch(subscriptionUrl, HappRequestHeaders.build(prefs))
        return HappSubscriptionParser.parseBody(bytes)
    }

    private fun fetch(url: String, headers: Map<String, String>): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.readBytes() ?: ByteArray(0)
            if (code !in 200..299) {
                throw HappSubscriptionException("HTTP $code при загрузке подписки")
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
