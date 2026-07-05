package dev.themarfa.vpnswitcher.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DebugLogCollector {

    private const val TAG = "DebugLogCollector"

    private var logcatProcess: java.lang.Process? = null
    private var logFile: File? = null

    val isCollecting: Boolean
        get() = logcatProcess?.isAlive == true

    fun start(context: Context): Boolean {
        stop()
        return try {
            val dir = File(context.cacheDir, "debug_logs").apply { mkdirs() }
            logFile = File(dir, "vpnswitcher_${System.currentTimeMillis()}.log")
            ProcessBuilder("logcat", "-c").redirectErrorStream(true).start().waitFor()
            logcatProcess = ProcessBuilder(
                "logcat",
                "-v",
                "threadtime",
                "--pid=${android.os.Process.myPid()}",
                "-f",
                logFile!!.absolutePath,
            ).redirectErrorStream(true).start()
            Log.i(TAG, "collecting to ${logFile!!.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            stop()
            false
        }
    }

    fun stop() {
        try {
            logcatProcess?.destroy()
        } catch (_: Exception) {
        }
        logcatProcess = null
    }

    fun stopAndShare(context: Context): Boolean {
        stop()
        val source = logFile ?: return false
        if (!source.exists() || source.length() == 0L) return false
        return try {
            val zip = File(context.cacheDir, "debug_logs/vpnswitcher_logs.zip")
            zip.parentFile?.mkdirs()
            zipFile(source, zip)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "VPN Switcher logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Отправить логи"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
            false
        }
    }

    private fun zipFile(source: File, zip: File) {
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            FileInputStream(source).use { input ->
                BufferedInputStream(input).use { buf ->
                    out.putNextEntry(ZipEntry(source.name))
                    buf.copyTo(out)
                    out.closeEntry()
                }
            }
        }
    }
}
