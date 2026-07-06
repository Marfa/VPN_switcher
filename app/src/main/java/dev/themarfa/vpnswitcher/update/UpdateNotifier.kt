package dev.themarfa.vpnswitcher.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import dev.themarfa.vpnswitcher.AppConstants
import dev.themarfa.vpnswitcher.R

object UpdateNotifier {

    private const val CHANNEL_ID = "vpn_switcher_update"
    private const val NOTIFICATION_ID = 4

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.update_notification_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, versionName: String, releasePageUrl: String) {
        ensureChannel(context)
        val url = releasePageUrl.takeIf { it.isNotBlank() } ?: AppConstants.GITHUB_RELEASES_URL
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, url.toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_title, versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }
}
