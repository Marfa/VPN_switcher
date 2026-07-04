package dev.themarfa.vpnswitcher.vpn



import android.content.Context

import android.net.ConnectivityManager

import android.net.NetworkCapabilities

import dev.themarfa.vpnswitcher.AppConstants

import kotlinx.coroutines.delay



object VpnMonitor {



    fun isVpnActive(context: Context): Boolean {

        if (DummyVpnService.dummyInFlight) return false

        val cm = context.getSystemService(ConnectivityManager::class.java)

        return cm.allNetworks.any { network ->

            cm.getNetworkCapabilities(network)

                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        }

    }



    suspend fun waitForVpnOff(context: Context, timeoutMs: Long): Boolean {

        val steps = (timeoutMs / 200).toInt().coerceAtLeast(1)

        repeat(steps) {

            delay(200)

            if (!isVpnActive(context)) return true

        }

        return !isVpnActive(context)

    }



    suspend fun waitForVpnOn(context: Context, timeoutMs: Long, packageName: String? = null): Boolean {

        val steps = (timeoutMs / 300).toInt().coerceAtLeast(1)

        repeat(steps) {

            delay(300)

            if (!isVpnActive(context)) return@repeat

            if (packageName == null) return true

            if (isTargetVpnUp(context, packageName)) return true

        }

        return packageName?.let { isTargetVpnUp(context, it) } ?: isVpnActive(context)

    }



    /** Happ часто подключается, но dumpsys не успевает вернуть com.happproxy. */

    fun isLikelyHappActive(context: Context): Boolean {

        if (!isVpnActive(context)) return false

        if (isHappActive(context)) return true

        val owner = ownerPackage() ?: return true

        return owner != AppConstants.CHATVPN_PACKAGE

    }



    private fun isTargetVpnUp(context: Context, packageName: String): Boolean {

        val owner = VpnAppConnector.activeVpnPackage()

        if (owner == packageName) return true

        return packageName == AppConstants.HAPP_PACKAGE &&

            isVpnActive(context) &&

            owner != AppConstants.CHATVPN_PACKAGE

    }



    fun ownerPackage(): String? = VpnAppConnector.activeVpnPackage()



    fun isHappActive(context: Context): Boolean =

        ownerPackage() == AppConstants.HAPP_PACKAGE



    fun isChatVpnActive(context: Context): Boolean =

        ownerPackage() == AppConstants.CHATVPN_PACKAGE

}


