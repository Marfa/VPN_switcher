package dev.themarfa.vpnswitcher

object AppConstants {
    const val CHATVPN_PACKAGE = "net.chatvpn.app.wg.android"
    const val HAPP_PACKAGE = "com.happproxy"

    const val HAPP_WIDGET_ACTION = "com.happproxy.action.widget.click"
    const val HAPP_WIDGET_RECEIVER = "com.happproxy/com.happproxy.receiver.WidgetProvider"

    /** Как в jirahelper/proxy_picker.py — TCP к api.telegram.org:443 */
    const val TELEGRAM_HOST = "api.telegram.org"
    const val TELEGRAM_PORT = 443
    const val TELEGRAM_PROBE_TIMEOUT_MS = 10_000

    /** Пауза после отключения Wi-Fi перед проверкой Telegram. */
    const val WIFI_LOST_PROBE_DELAY_MS = 10_000L

    const val PREFS_NAME = "vpn_switcher_prefs"
    const val KEY_HAPP_CONFIG_URI = "happ_config_uri"
    const val KEY_HAPP_JSON_CONFIG = "happ_json_config"
    const val KEY_HAPP_JSON_READY = "happ_json_ready"
    const val KEY_HAPP_HWID = "happ_hwid"
    const val KEY_HAPP_SERVER = "happ_server_label"
    const val KEY_HAPP_SERVER_CONFIG = "happ_server_config"
    const val KEY_CHATVPN_SERVER = "chatvpn_server_label"
    const val KEY_SWITCH_ON_UNAVAILABLE = "switch_on_unavailable"
    const val KEY_SWITCH_ALWAYS = "switch_always"
    const val KEY_PUSH_ENABLED = "push_enabled"
    const val KEY_ON_HAPP_MODE = "on_happ_mode"
    const val KEY_LAST_STATUS = "last_status"
    const val KEY_LAST_NOTIFIED_UPDATE = "last_notified_update_version"

    const val GITHUB_REPO = "Marfa/VPN_switcher"
    const val GITHUB_URL = "https://github.com/Marfa/VPN_switcher"
    const val GITHUB_RELEASES_URL = "https://github.com/Marfa/VPN_switcher/releases/latest"
    const val PROXY_AD_URL = "https://proxys.world/?refid=41873"
    const val PROXY_AD_IMAGE_URL = "https://proxys.world/img/b/new_light_900x60.gif"
    const val DONATE_URL = "https://www.donationalerts.com/r/themarfa"
    const val DONATE_CRYPTO_URL = "https://nowpayments.io/donation/themarfa"
}
