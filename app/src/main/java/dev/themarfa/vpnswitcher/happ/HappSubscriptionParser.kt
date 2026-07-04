package dev.themarfa.vpnswitcher.happ

import android.util.Base64
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.GZIPInputStream

object HappSubscriptionParser {

    private val PROTOCOL_MARKERS = listOf(
        "vless://",
        "vmess://",
        "trojan://",
        "ss://",
        "socks://",
        "hysteria2://",
        "hysteria://",
        "wireguard://",
    )

    fun extractSubscriptionUrl(happUri: String): String {
        val trimmed = happUri.trim()
        val prefix = "happ://add/"
        require(trimmed.lowercase().startsWith(prefix)) {
            "Ожидается ссылка вида happ://add/https://…"
        }
        return trimmed.substring(prefix.length)
    }

    fun parseBody(rawBytes: ByteArray): List<HappServerEntry> {
        val text = decodeBody(bytesToText(rawBytes))
        if (text.contains("<html", ignoreCase = true)) {
            throw HappSubscriptionException(
                "Панель вернула HTML вместо подписки — неверный HWID. " +
                    "Скопируйте HWID из Happ (О программе → устройство) и вставьте в поле.",
            )
        }

        val servers = mutableListOf<HappServerEntry>()
        var index = 1
        for (line in text.lineSequence()) {
            val config = extractConfigLine(line) ?: continue
            val name = extractServerName(config).ifBlank { "Сервер $index" }
            servers += HappServerEntry(name, config)
            index++
        }
        if (servers.isEmpty()) {
            throw HappSubscriptionException("В подписке не найдено серверов (vless/vmess/trojan/…)")
        }
        return servers
    }

    fun toHappImportUri(configLine: String): String = "happ://add/${configLine.trim()}"

    private fun bytesToText(bytes: ByteArray): String {
        val payload = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } else {
            bytes
        }
        return String(payload, Charsets.UTF_8)
    }

    private fun decodeBody(text: String): String {
        val trimmed = text.trim()
        if (PROTOCOL_MARKERS.any { trimmed.contains(it, ignoreCase = true) }) {
            return trimmed
        }
        return try {
            val once = String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
            if (PROTOCOL_MARKERS.any { once.contains(it, ignoreCase = true) }) once else decodeBody(once)
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun extractConfigLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val lower = trimmed.lowercase()
        val start = PROTOCOL_MARKERS.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
        return trimmed.substring(start)
    }

    private fun extractServerName(config: String): String {
        val hash = config.substringAfter('#', "")
        if (hash.isBlank()) return ""

        val params = hash.substringAfter('?', hash)
        if (params.contains("serverDescription=", ignoreCase = true)) {
            val raw = params.substringAfter("serverDescription=").substringBefore('&')
            return decodeTitle(raw)
        }
        if (hash.startsWith("title?", ignoreCase = true)) {
            val raw = hash.substringAfter("title?").substringAfter("serverDescription=").substringBefore('&')
            return decodeTitle(raw)
        }
        return decodeTitle(hash.substringBefore('?'))
    }

    private fun decodeTitle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val bytes = Base64.decode(trimmed, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                URLDecoder.decode(trimmed, "UTF-8")
            } catch (_: Exception) {
                trimmed
            }
        }
    }
}
