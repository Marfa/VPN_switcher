package dev.themarfa.vpnswitcher.happ

import dev.themarfa.vpnswitcher.AppConstants
import dev.themarfa.vpnswitcher.shizuku.ShizukuManager

object HappHwidReader {

    private val HAPP_PACKAGES = listOf(
        AppConstants.HAPP_PACKAGE,
        "su.happ.proxyutility",
    )

    private val XML_HWID = Regex(
        """<string name="[^"]*hwid[^"]*">([^<]+)</string>""",
        RegexOption.IGNORE_CASE,
    )

    /** Читает HWID из shared_prefs Happ через Shizuku (работает не на всех прошивках). */
    fun readViaShizuku(): String? {
        if (!ShizukuManager.isReady) return null
        for (pkg in HAPP_PACKAGES) {
            val result = ShizukuManager.run(
                "grep -h -i hwid /data/data/$pkg/shared_prefs/*.xml 2>/dev/null",
            )
            val fromXml = XML_HWID.find(result.stdout)?.groupValues?.getOrNull(1)?.trim()
            if (!fromXml.isNullOrBlank()) return fromXml

            val alt = ShizukuManager.run(
                "grep -h -i hwid /data/user/0/$pkg/shared_prefs/*.xml 2>/dev/null",
            )
            val fromAlt = XML_HWID.find(alt.stdout)?.groupValues?.getOrNull(1)?.trim()
            if (!fromAlt.isNullOrBlank()) return fromAlt
        }
        return null
    }

    fun looksLikeHwid(value: String): Boolean {
        val v = value.trim()
        return v.length in 12..128 && v.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
