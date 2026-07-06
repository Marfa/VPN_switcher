package dev.themarfa.vpnswitcher.vpn

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dev.themarfa.vpnswitcher.AppConstants
import dev.themarfa.vpnswitcher.shizuku.ShizukuManager
import dev.themarfa.vpnswitcher.ui.UiForegroundGuard
import kotlinx.coroutines.delay

class VpnOrchestrator(private val context: Context) {

    private val dummyVpn = DummyVpnController(context)

    suspend fun switchToHapp(serverLabel: String = "") {
        if (UiForegroundGuard.isMainActivityVisible) {
            Log.i(TAG, "skip switchToHapp: UI visible")
            return
        }
        if (!ShizukuManager.shellReady()) {
            Log.e(TAG, "shell not ready")
            return
        }

        ShizukuManager.awaitUserService(8_000)
        val useUi = context.getSystemService(PowerManager::class.java).isInteractive

        stopChatVpn()

        if (VpnMonitor.isVpnActive(context)) {
            dummyVpn.revokeOtherVpn()
            VpnMonitor.waitForVpnOff(context, 3_000)
            AppController.forceStop(AppConstants.CHATVPN_PACKAGE)
            VpnMonitor.waitForVpnOff(context, 3_000)
        }

        AppController.disableChatVpn()
        delay(500)

        if (!VpnMonitor.isVpnActive(context)) {
            VpnAppConnector.startHapp(serverLabel, useUi)
        }

        if (VpnMonitor.waitForVpnOn(context, 20_000, AppConstants.HAPP_PACKAGE)) {
            Log.i(TAG, "Happ VPN up")
            return
        }

        repeat(4) {
            Log.w(TAG, "Happ retry $it")
            VpnAppConnector.tapHappWidget()
            if (VpnMonitor.waitForVpnOn(context, 5_000, AppConstants.HAPP_PACKAGE)) return
        }
    }

    suspend fun prepareWifiMode() {
        if (UiForegroundGuard.isMainActivityVisible) {
            Log.i(TAG, "skip prepareWifiMode: UI visible")
            return
        }
        if (VpnMonitor.isChatVpnActive(context)) {
            AppController.enableChatVpn()
            delay(300)
            return
        }
        stopHapp()
        AppController.enableChatVpn()
        delay(300)
    }

    suspend fun stopChatVpn() {
        AppController.forceStop(AppConstants.CHATVPN_PACKAGE)
        delay(300)
        dummyVpn.revokeOtherVpn()
        VpnMonitor.waitForVpnOff(context, 3_000)
        AppController.forceStop(AppConstants.CHATVPN_PACKAGE)
        VpnMonitor.waitForVpnOff(context, 2_000)
    }

    suspend fun stopHapp() {
        AppController.forceStop(AppConstants.HAPP_PACKAGE)
        delay(300)
        if (VpnMonitor.ownerPackage() == AppConstants.HAPP_PACKAGE &&
            VpnMonitor.isVpnActive(context)
        ) {
            dummyVpn.revokeOtherVpn()
            VpnMonitor.waitForVpnOff(context, 4_000)
        }
        AppController.forceStop(AppConstants.HAPP_PACKAGE)
        VpnMonitor.waitForVpnOff(context, 2_000)
    }

    companion object {
        private const val TAG = "VpnOrchestrator"
    }
}
