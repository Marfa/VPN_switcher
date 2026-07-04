package dev.themarfa.vpnswitcher.happ

import android.os.Build
import dev.themarfa.vpnswitcher.prefs.AppPreferences
import java.util.Locale

object HappRequestHeaders {

    fun build(prefs: AppPreferences): Map<String, String> {
        val hwid = prefs.happHwid.trim()
        if (hwid.isBlank()) {
            throw HappSubscriptionException(
                "Нужен HWID из Happ. Нажмите «Взять HWID из Happ» или скопируйте вручную " +
                    "(О программе → устройство → короткое нажатие).",
            )
        }

        val locale = Locale.getDefault().language
        return linkedMapOf(
            "User-Agent" to "Happ/3.13.0",
            "X-Device-Os" to "Android",
            "X-Device-Locale" to locale,
            "X-Device-Model" to Build.MODEL,
            "X-Ver-Os" to Build.VERSION.RELEASE,
            "X-Hwid" to hwid,
            "X-HWID" to hwid,
            "Accept-Encoding" to "gzip",
        )
    }
}
