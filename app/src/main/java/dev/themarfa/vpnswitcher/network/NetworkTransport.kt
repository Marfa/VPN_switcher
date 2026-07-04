package dev.themarfa.vpnswitcher.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkTransport {

    fun resolve(cm: ConnectivityManager): Int? {
        cm.activeNetwork?.let { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@let
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    return NetworkCapabilities.TRANSPORT_WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    return NetworkCapabilities.TRANSPORT_CELLULAR
            }
        }
        if (hasWifi(cm)) return NetworkCapabilities.TRANSPORT_WIFI
        if (hasCellular(cm)) return NetworkCapabilities.TRANSPORT_CELLULAR
        return null
    }

    fun networkSummary(cm: ConnectivityManager): String {
        val wifi = if (hasWifi(cm)) "Wi-Fi✓" else "Wi-Fi✗"
        val cell = if (hasCellular(cm)) "mobile✓" else "mobile✗"
        val def = label(resolve(cm))
        return "$wifi $cell · default:$def"
    }

    fun hasWifi(cm: ConnectivityManager): Boolean {
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
        }
        return false
    }

    fun hasCellular(cm: ConnectivityManager): Boolean {
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                return true
            }
        }
        return false
    }

    fun findCellularNetwork(cm: ConnectivityManager): android.net.Network? {
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                return network
            }
        }
        return null
    }

    fun label(transport: Int?): String = when (transport) {
        NetworkCapabilities.TRANSPORT_WIFI -> "Wi-Fi"
        NetworkCapabilities.TRANSPORT_CELLULAR -> "mobile"
        else -> "?"
    }
}
