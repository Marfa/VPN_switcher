package dev.themarfa.vpnswitcher.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class DummyVpnController(private val context: Context) {

    /** Как Anubis StealthVpnService: establish() → revoke чужой VPN → сразу close(). */
    suspend fun revokeOtherVpn(): Boolean {
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "VPN permission not granted")
            return false
        }

        val done = CompletableDeferred<Boolean>()
        DummyVpnService.onComplete = { done.complete(true) }
        DummyVpnService.dummyInFlight = true

        context.startService(
            Intent(context, DummyVpnService::class.java).apply {
                action = DummyVpnService.ACTION_DISCONNECT
            },
        )

        val ok = withTimeoutOrNull(5_000L) { done.await() } ?: false
        DummyVpnService.onComplete = null
        return ok
    }

    companion object {
        private const val TAG = "DummyVpnController"
    }
}

class DummyVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            doDisconnect()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun doDisconnect() {
        try {
            tun?.close()
            tun = Builder()
                .addAddress(DUMMY_ADDRESS, 32)
                .setSession("VpnSwitcher disconnect")
                .setBlocking(false)
                .establish()
            tun?.close()
            tun = null
            onComplete?.invoke()
            Log.i(TAG, "dummy VPN revoked external slot")
        } catch (e: Exception) {
            Log.e(TAG, "dummy disconnect failed", e)
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({ dummyInFlight = false }, 1_500L)
            stopSelf()
        }
    }

    override fun onDestroy() {
        tun?.close()
        tun = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISCONNECT = "dev.themarfa.vpnswitcher.DISCONNECT_VPN"
        private const val DUMMY_ADDRESS = "198.18.0.1"
        private const val TAG = "DummyVpnService"

        @Volatile
        var dummyInFlight: Boolean = false

        @Volatile
        var onComplete: (() -> Unit)? = null
    }
}
