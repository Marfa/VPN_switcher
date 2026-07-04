package dev.themarfa.vpnswitcher.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

/** Держит CPU при проверке сети с выключенным экраном. */
object MonitorWakeLock {

    private const val TAG = "MonitorWakeLock"
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context, timeoutMs: Long = 90_000L) {
        release()
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VpnSwitcher:monitor").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
        Log.d(TAG, "acquired")
    }

    fun release() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w(TAG, "release failed", e)
        } finally {
            wakeLock = null
        }
    }
}
