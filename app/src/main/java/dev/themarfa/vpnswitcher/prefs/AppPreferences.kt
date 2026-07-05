package dev.themarfa.vpnswitcher.prefs



import android.content.Context

import android.content.SharedPreferences

import dev.themarfa.vpnswitcher.AppConstants



class AppPreferences(context: Context) {



    private val store = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)



    val shared: SharedPreferences get() = store



    var switchOnUnavailable: Boolean

        get() {

            if (!store.contains(AppConstants.KEY_SWITCH_ON_UNAVAILABLE)) {

                val legacyAlways = store.getBoolean("test_always_happ", false)

                return !legacyAlways

            }

            return store.getBoolean(AppConstants.KEY_SWITCH_ON_UNAVAILABLE, true)

        }

        set(value) = store.edit().putBoolean(AppConstants.KEY_SWITCH_ON_UNAVAILABLE, value).apply()



    var switchAlways: Boolean

        get() {

            if (!store.contains(AppConstants.KEY_SWITCH_ALWAYS)) {

                return store.getBoolean("test_always_happ", false)

            }

            return store.getBoolean(AppConstants.KEY_SWITCH_ALWAYS, false)

        }

        set(value) = store.edit().putBoolean(AppConstants.KEY_SWITCH_ALWAYS, value).apply()



    var pushEnabled: Boolean

        get() {

            if (!store.contains(AppConstants.KEY_PUSH_ENABLED)) {

                return store.getBoolean("wifi_vpn_reminder", true)

            }

            return store.getBoolean(AppConstants.KEY_PUSH_ENABLED, true)

        }

        set(value) = store.edit().putBoolean(AppConstants.KEY_PUSH_ENABLED, value).apply()



    var onHappMode: Boolean

        get() = store.getBoolean(AppConstants.KEY_ON_HAPP_MODE, false)

        set(value) = store.edit().putBoolean(AppConstants.KEY_ON_HAPP_MODE, value).apply()



    var lastStatus: String

        get() = store.getString(AppConstants.KEY_LAST_STATUS, "Ожидание") ?: "Ожидание"

        set(value) = store.edit().putString(AppConstants.KEY_LAST_STATUS, value).apply()



    var happHwid: String

        get() = store.getString(AppConstants.KEY_HAPP_HWID, "") ?: ""

        set(value) = store.edit().putString(AppConstants.KEY_HAPP_HWID, value.trim()).apply()



    fun shouldRunService(): Boolean = switchOnUnavailable || switchAlways || pushEnabled

}

