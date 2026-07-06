package dev.themarfa.vpnswitcher.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import dev.themarfa.vpnswitcher.R
import dev.themarfa.vpnswitcher.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateChecker {

    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val ALARM_REQUEST_CODE = 9001

    suspend fun run(context: Context, showToast: Boolean = false) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = AppPreferences(app)
        val current = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: return@withContext
        val update = GitHubUpdater.checkForUpdate(current)

        withContext(Dispatchers.Main) {
            if (update == null) {
                UpdateNotifier.dismiss(app)
                prefs.lastNotifiedUpdateVersion = ""
                if (showToast) {
                    Toast.makeText(app, R.string.about_update_none, Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }
            if (update.versionName == prefs.lastNotifiedUpdateVersion) return@withContext

            UpdateNotifier.show(app, update.versionName, update.releasePageUrl)
            prefs.lastNotifiedUpdateVersion = update.versionName
        }
    }

    fun schedulePeriodic(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, UpdateAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            app,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val trigger = SystemClock.elapsedRealtime() + CHECK_INTERVAL_MS
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            trigger,
            CHECK_INTERVAL_MS,
            pending,
        )
    }
}
