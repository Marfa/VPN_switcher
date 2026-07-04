package dev.themarfa.vpnswitcher.happ

import org.json.JSONArray
import org.json.JSONObject

object HappJsonConfig {

    fun isValid(raw: String): Boolean = resolveConfig(raw) != null

    fun parseRemarks(raw: String): String? {
        val json = resolveConfig(raw) ?: return null
        return try {
            JSONObject(json).optString("remarks").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** Один объект или массив — возвращает готовый JSON одного сервера. */
    fun resolveConfig(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        return when {
            trimmed.startsWith("[") -> firstArrayEntry(trimmed)
            trimmed.startsWith("{") -> trimmed.takeIf { hasProxyOutbound(it) }
            else -> null
        }
    }

    fun findInArray(raw: String, nameNeedle: String): String? {
        if (nameNeedle.isBlank()) return null
        val needle = nameNeedle.trim()
        return try {
            val array = JSONArray(raw.trim())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val remarks = obj.optString("remarks")
                if (remarks.contains(needle, ignoreCase = true) && hasProxyOutbound(obj.toString())) {
                    return obj.toString()
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun firstArrayEntry(raw: String): String? {
        return try {
            val array = JSONArray(raw)
            if (array.length() == 0) return null
            val first = array.getJSONObject(0).toString()
            first.takeIf { hasProxyOutbound(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasProxyOutbound(json: String): Boolean {
        return try {
            val outbounds = JSONObject(json).getJSONArray("outbounds")
            (0 until outbounds.length()).any { i ->
                val tag = outbounds.getJSONObject(i).optString("tag")
                tag == "proxy" || outbounds.getJSONObject(i).optString("protocol") != "freedom"
            }
        } catch (_: Exception) {
            false
        }
    }
}
