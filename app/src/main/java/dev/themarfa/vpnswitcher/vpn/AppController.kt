package dev.themarfa.vpnswitcher.vpn

import android.util.Log
import dev.themarfa.vpnswitcher.AppConstants
import dev.themarfa.vpnswitcher.shizuku.ShizukuManager

/** pm disable-user / enable — как заморозка в Anubis. */
object AppController {

    private const val TAG = "AppController"

    fun disableApp(packageName: String) {
        val result = ShizukuManager.run("pm disable-user --user 0 $packageName")
        Log.i(TAG, "disable $packageName code=${result.exitCode}")
    }

    fun enableApp(packageName: String) {
        val result = ShizukuManager.run("pm enable $packageName")
        Log.i(TAG, "enable $packageName code=${result.exitCode}")
    }

    fun forceStop(packageName: String) {
        val result = ShizukuManager.run("am force-stop $packageName")
        Log.i(TAG, "force-stop $packageName code=${result.exitCode}")
    }

    fun disableChatVpn() = disableApp(AppConstants.CHATVPN_PACKAGE)

    fun enableChatVpn() = enableApp(AppConstants.CHATVPN_PACKAGE)
}
