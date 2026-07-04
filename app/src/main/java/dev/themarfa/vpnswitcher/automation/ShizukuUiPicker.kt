package dev.themarfa.vpnswitcher.automation

import android.util.Log
import dev.themarfa.vpnswitcher.shizuku.ShizukuManager
import kotlinx.coroutines.delay
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Выбор элемента по тексту через shell uiautomator — без Accessibility у нашего APK.
 */
object ShizukuUiPicker {

    private const val TAG = "ShizukuUiPicker"
    private const val DUMP_PATH = "/data/local/tmp/vpnswitcher_ui.xml"

    suspend fun tapText(label: String, preferRightSide: Boolean = false): Boolean {
        val query = label.trim()
        if (query.isBlank()) return false
        if (!ShizukuManager.isReady) return false

        repeat(10) {
            val point = findTapPoint(dumpXml(), query, preferRightSide) ?: run {
                delay(300)
                return@repeat
            }
            tap(point)
            Log.i(TAG, "tapped '$query' at ${point.first},${point.second}")
            return true
        }
        Log.w(TAG, "text not found: $query")
        return false
    }

    suspend fun tapAny(labels: List<String>, preferRightSide: Boolean = false): Boolean {
        for (label in labels) {
            if (tapText(label, preferRightSide)) return true
        }
        return false
    }

    /** Крупная кнопка подключения без текста (ChatVPN и др.). */
    suspend fun tapMainConnectButton(): Boolean {
        if (!ShizukuManager.isReady) return false

        repeat(8) {
            val xml = dumpXml()
            val screenH = parseBounds(xml).maxOfOrNull { it.bottom } ?: 2_400
            val minY = (screenH * 0.3f).toInt()

            val target = parseBounds(xml)
                .filter { (it.clickable || it.checkable) && it.centerY >= minY }
                .maxByOrNull { it.area }
                ?: run {
                    delay(300)
                    return@repeat
                }

            tap(target.centerX to target.centerY)
            Log.i(TAG, "tapped main button at ${target.centerX},${target.centerY}")
            return true
        }
        return false
    }

    fun goHome() {
        ShizukuManager.run("input keyevent KEYCODE_HOME")
    }

    private fun tap(point: Pair<Int, Int>) {
        ShizukuManager.run("input tap ${point.first} ${point.second}")
    }

    private fun dumpXml(): String {
        ShizukuManager.run("uiautomator dump $DUMP_PATH")
        return ShizukuManager.run("cat $DUMP_PATH").stdout
    }

    private fun findTapPoint(xml: String, query: String, preferRightSide: Boolean): Pair<Int, Int>? {
        val q = query.lowercase()
        val matches = parseBounds(xml).filter { node ->
            node.text.lowercase().contains(q) || node.contentDescription.lowercase().contains(q)
        }
        if (matches.isEmpty()) return null

        val target = if (preferRightSide) {
            matches.maxByOrNull { it.centerX } ?: matches.first()
        } else {
            matches.first()
        }
        return target.centerX to target.centerY
    }

    private fun parseBounds(xml: String): List<UiNode> {
        val out = mutableListOf<UiNode>()
        if (xml.isBlank()) return out

        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(StringReader(xml))
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "node") {
                    val text = parser.getAttributeValue(null, "text").orEmpty()
                    val desc = parser.getAttributeValue(null, "content-desc").orEmpty()
                    val bounds = parser.getAttributeValue(null, "bounds").orEmpty()
                    val clickable = parser.getAttributeValue(null, "clickable") == "true"
                    val checkable = parser.getAttributeValue(null, "checkable") == "true"
                    val rect = parseRect(bounds) ?: continue
                    if (!clickable && !checkable && text.isBlank() && desc.isBlank()) continue
                    out += UiNode(text, desc, rect, clickable, checkable)
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "xml parse failed", e)
        }
        return out
    }

    private fun parseRect(bounds: String): Rect? {
        val nums = Regex("""\d+""").findAll(bounds).map { it.value.toInt() }.toList()
        if (nums.size < 4) return null
        return Rect(nums[0], nums[1], nums[2], nums[3])
    }

    private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
        val area get() = (right - left) * (bottom - top)
    }

    private data class UiNode(
        val text: String,
        val contentDescription: String,
        val rect: Rect,
        val clickable: Boolean,
        val checkable: Boolean,
    ) {
        val centerX get() = rect.centerX
        val centerY get() = rect.centerY
        val bottom get() = rect.bottom
        val area get() = rect.area
    }
}
