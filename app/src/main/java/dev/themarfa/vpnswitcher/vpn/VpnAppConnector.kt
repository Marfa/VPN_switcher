package dev.themarfa.vpnswitcher.vpn



import android.os.SystemClock

import android.util.Log

import dev.themarfa.vpnswitcher.AppConstants

import dev.themarfa.vpnswitcher.shizuku.ShizukuManager

import kotlinx.coroutines.delay



object VpnAppConnector {



    private const val TAG = "VpnAppConnector"

    private var lastPkgAt = 0L

    private var lastPkg: String? = null



    /** Anubis: TOGGLE через виджет; при включённом экране UI-tap мешает подключению. */
    suspend fun startHapp(serverLabel: String, useUi: Boolean) {
        if (useUi) {
            ShizukuManager.run("input keyevent 3")
            delay(400)
            repeat(2) {
                tapHappWidget()
                delay(600)
            }
            return
        }

        tapHappWidget()
    }



    fun tapHappWidget(): Boolean {

        val result = ShizukuManager.run(

            "am broadcast -a ${AppConstants.HAPP_WIDGET_ACTION} " +

                "-p ${AppConstants.HAPP_PACKAGE} " +

                "-n ${AppConstants.HAPP_PACKAGE}/com.happproxy.receiver.WidgetProvider",

        )

        Log.i(TAG, "happ widget code=${result.exitCode} stdout=${result.stdout}")

        return result.ok

    }



    fun activeVpnPackage(): String? {

        val now = SystemClock.elapsedRealtime()

        if (now - lastPkgAt < 800) return lastPkg

        lastPkgAt = now

        val resolved = resolveActiveVpnPackage()

        lastPkg = resolved

        return resolved

    }



    private fun resolveActiveVpnPackage(): String? {

        val myUid = android.os.Process.myUid()

        val uidOut = ShizukuManager.run(

            "dumpsys connectivity 2>/dev/null | grep -A 30 'type: VPN\\[' | " +

                "grep -oE 'OwnerUid: [0-9]+' | grep -v 'OwnerUid: $myUid' | " +

                "head -1 | grep -oE '[0-9]+'",

        ).stdout.trim()



        val uid = uidOut.toIntOrNull()

        if (uid != null && uid > 0) {

            val pkgLine = ShizukuManager.run(

                "pm list packages --uid $uid 2>/dev/null | head -1",

            ).stdout.trim()

            val pkg = pkgLine.removePrefix("package:").substringBefore(" ").trim()

            if (pkg.isNotBlank()) return pkg

        }



        val out = ShizukuManager.run("dumpsys connectivity").stdout

        if (out.isBlank()) return null

        val fromDump = listOf(AppConstants.HAPP_PACKAGE, AppConstants.CHATVPN_PACKAGE)

            .firstOrNull { out.contains("vpnPackageName=$it") }

        if (fromDump != null) return fromDump



        return foregroundVpnPackage()

    }



    private fun foregroundVpnPackage(): String? {

        for (pkg in listOf(AppConstants.HAPP_PACKAGE, AppConstants.CHATVPN_PACKAGE)) {

            val count = ShizukuManager.run(

                "dumpsys activity services $pkg 2>/dev/null | grep -c 'isForeground=true'",

            ).stdout.trim().toIntOrNull() ?: 0

            if (count > 0) return pkg

        }

        return null

    }

}
