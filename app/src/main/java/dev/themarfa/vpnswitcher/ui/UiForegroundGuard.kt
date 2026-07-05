package dev.themarfa.vpnswitcher.ui

/** Пока экран приложения открыт — не трогаем VPN (шум от network callbacks). */
object UiForegroundGuard {

    @Volatile
    var isMainActivityVisible: Boolean = false
}
