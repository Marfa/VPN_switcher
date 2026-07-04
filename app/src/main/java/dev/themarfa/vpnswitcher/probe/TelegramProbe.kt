package dev.themarfa.vpnswitcher.probe

import android.net.Network
import android.util.Log
import dev.themarfa.vpnswitcher.AppConstants
import java.net.InetSocketAddress

/**
 * Простая проверка доступности Telegram: TCP connect к api.telegram.org:443.
 * Логика из jirahelper/proxy_picker.py (test_socks5_proxy).
 */
object TelegramProbe {

    private const val TAG = "TelegramProbe"

    fun isReachable(network: Network, timeoutMs: Int = AppConstants.TELEGRAM_PROBE_TIMEOUT_MS): Boolean {
        return try {
            val socket = network.socketFactory.createSocket()
            socket.use {
                it.soTimeout = timeoutMs
                try {
                    network.bindSocket(it)
                } catch (_: Exception) {
                }
                it.connect(
                    InetSocketAddress(AppConstants.TELEGRAM_HOST, AppConstants.TELEGRAM_PORT),
                    timeoutMs,
                )
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "probe failed on $network: ${e.message}")
            false
        }
    }
}
