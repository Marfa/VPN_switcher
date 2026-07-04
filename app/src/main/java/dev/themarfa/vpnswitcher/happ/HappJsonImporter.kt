package dev.themarfa.vpnswitcher.happ

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import dev.themarfa.vpnswitcher.AppConstants
import dev.themarfa.vpnswitcher.automation.ShizukuUiPicker
import dev.themarfa.vpnswitcher.shizuku.ShizukuManager
import kotlinx.coroutines.delay

object HappJsonImporter {

    private const val TAG = "HappJsonImporter"

    suspend fun import(context: Context, rawJson: String) {
        val json = HappJsonConfig.resolveConfig(rawJson)
            ?: throw HappSubscriptionException("Некорректный JSON Happ")

        copyToClipboard(context, json)
        launchHapp()
        delay(1_800)

        tapPlusMenu()
        delay(500)
        if (tapClipboardOption()) {
            Log.i(TAG, "import via clipboard UI")
            delay(1_500)
            return
        }

        Log.w(TAG, "import UI failed — JSON в буфере, импортируйте в Happ вручную (+ → буфер)")
    }

    suspend fun connectExisting(serverLabel: String) {
        launchHapp()
        delay(1_500)

        val label = serverLabel.trim()
        if (label.isNotBlank()) {
            ShizukuUiPicker.tapText(label)
            delay(700)
        }

        tapConnectButton()
        delay(500)
        toggleWidget()
    }

    private suspend fun tapPlusMenu() {
        val labels = listOf("+", "Добавить", "Add", "Импорт", "Import")
        for (label in labels) {
            if (ShizukuUiPicker.tapText(label)) return
        }
    }

    private suspend fun tapClipboardOption(): Boolean {
        val labels = listOf(
            "буфер обмена",
            "из буфера",
            "clipboard",
            "Clipboard",
            "буфер",
        )
        for (label in labels) {
            if (ShizukuUiPicker.tapText(label)) return true
        }
        return false
    }

    private suspend fun tapConnectButton() {
        val labels = listOf(
            "Подключить",
            "Connect",
            "Отключить",
            "Disconnect",
        )
        for (label in labels) {
            if (ShizukuUiPicker.tapText(label)) return
        }
    }

    private fun toggleWidget() {
        ShizukuManager.run(
            "am broadcast -a ${AppConstants.HAPP_WIDGET_ACTION} " +
                "-n ${AppConstants.HAPP_WIDGET_RECEIVER}",
        )
    }

    private fun copyToClipboard(context: Context, json: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("happ-json", json))
    }

    private fun launchHapp() {
        ShizukuManager.run(
            "monkey -p ${AppConstants.HAPP_PACKAGE} -c android.intent.category.LAUNCHER 1",
        )
    }
}
