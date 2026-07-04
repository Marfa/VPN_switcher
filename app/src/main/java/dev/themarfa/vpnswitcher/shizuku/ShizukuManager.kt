package dev.themarfa.vpnswitcher.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import dev.themarfa.vpnswitcher.IUserService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

object ShizukuManager {

    const val REQUEST_CODE = 1

    @Volatile
    private var userService: IUserService? = null

    @Volatile
    private var lastShellOk = false

    @Volatile
    private var lastShellCheckAt = 0L

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val readyListeners = CopyOnWriteArrayList<() -> Unit>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            userService = IUserService.Stub.asInterface(binder)
            notifyReady()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName("dev.themarfa.vpnswitcher", UserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .version(1)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (hasPermission()) bindUserService()
    }

    fun start() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        if (Shizuku.pingBinder() && hasPermission()) {
            bindUserService()
        }
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        unbindUserService()
    }

    fun addOnReadyListener(listener: () -> Unit) {
        readyListeners.add(listener)
        if (isReady) mainHandler.post(listener)
    }

    fun removeOnReadyListener(listener: () -> Unit) {
        readyListeners.remove(listener)
    }

    private fun notifyReady() {
        if (!isReady) return
        readyListeners.forEach { mainHandler.post(it) }
    }

    val isReady: Boolean
        get() = Shizuku.pingBinder() && hasPermission() && userService != null

    /** Реально ли работает shell (не только bind). Кэш 30 с — меньше нагрузка на CPU. */
    fun shellReady(forceRefresh: Boolean = false): Boolean {
        if (!isReady) return false
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh && now - lastShellCheckAt < 30_000) return lastShellOk
        lastShellCheckAt = now
        val result = run("id")
        lastShellOk = result.ok && result.stdout.contains("uid=2000")
        return lastShellOk
    }

    fun openManager(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun hasPermission(): Boolean =
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun bindUserService() {
        if (userService != null || !Shizuku.pingBinder() || !hasPermission()) return
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        } catch (_: Exception) {
        }
    }

    suspend fun awaitUserService(timeoutMs: Long = 2_000): Boolean {
        if (userService != null) return true
        bindUserService()
        return withTimeoutOrNull(timeoutMs) {
            while (userService == null) delay(50)
            true
        } != null
    }

    fun unbindUserService() {
        try {
            Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
        } catch (_: Exception) {
        }
        userService = null
    }

    fun run(command: String): ShellResult {
        val service = userService
            ?: return ShellResult(-1, "", "UserService не подключён")

        return try {
            val raw = service.execCommandWithOutput(arrayOf("sh", "-c", command))
            parseOutput(raw)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "error")
        }
    }

    private fun parseOutput(raw: String): ShellResult {
        val parts = raw.split("\n---STDERR---\n", limit = 2)
        val head = parts[0]
        val stderr = if (parts.size > 1) parts[1] else ""
        val newline = head.indexOf('\n')
        if (newline < 0) return ShellResult(-1, "", head)
        val code = head.substring(0, newline).toIntOrNull() ?: -1
        val stdout = head.substring(newline + 1)
        return ShellResult(code, stdout.trim(), stderr.trim())
    }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0
}
