package dev.themarfa.vpnswitcher.service



import android.app.Notification

import android.app.NotificationChannel

import android.app.NotificationManager

import android.app.PendingIntent

import android.app.Service

import android.content.Intent

import android.net.ConnectivityManager

import android.net.Network

import android.net.NetworkCapabilities

import android.net.NetworkRequest

import android.os.Build

import android.os.IBinder

import android.os.SystemClock

import android.util.Log

import androidx.core.app.NotificationCompat

import dev.themarfa.vpnswitcher.AppConstants

import dev.themarfa.vpnswitcher.R

import dev.themarfa.vpnswitcher.network.NetworkTransport

import dev.themarfa.vpnswitcher.prefs.AppPreferences

import dev.themarfa.vpnswitcher.probe.TelegramProbe

import dev.themarfa.vpnswitcher.shizuku.ShizukuManager

import dev.themarfa.vpnswitcher.ui.MainActivity

import dev.themarfa.vpnswitcher.vpn.VpnMonitor

import dev.themarfa.vpnswitcher.vpn.VpnOrchestrator

import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.TimeoutCancellationException

import kotlinx.coroutines.cancel

import kotlinx.coroutines.delay

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import kotlinx.coroutines.sync.Mutex

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.withTimeout



class NetworkMonitorService : Service() {



    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val switchMutex = Mutex()

    private lateinit var connectivityManager: ConnectivityManager

    private lateinit var prefs: AppPreferences

    private lateinit var orchestrator: VpnOrchestrator



    private var lastTransport: Int? = null

    private var syncJob: Job? = null

    private var disconnectJob: Job? = null

    private var handlingDisconnect = false

    private var wasWifi = false

    private var suppressWifiReminderUntil = 0L

    private var suppressWifiOffUntil = 0L



    private val networkRequest = NetworkRequest.Builder()

        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        .build()



    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) = onNetworkEvent("available")

        override fun onLost(network: Network) = onNetworkEvent("lost")

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =

            onNetworkEvent("caps")

    }



    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) = onNetworkEvent("default-available")

        override fun onLost(network: Network) = onNetworkEvent("default-lost")

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =

            onNetworkEvent("default-caps")

    }



    override fun onCreate() {

        super.onCreate()

        ShizukuManager.start()

        connectivityManager = getSystemService(ConnectivityManager::class.java)

        prefs = AppPreferences(this)

        orchestrator = VpnOrchestrator(this)

        ensureChannels()



        lastTransport = NetworkTransport.resolve(connectivityManager)

        wasWifi = NetworkTransport.hasWifi(connectivityManager)

        suppressWifiReminderUntil = SystemClock.elapsedRealtime() + 3_000

        suppressWifiOffUntil = SystemClock.elapsedRealtime() + 15_000



        startForeground(NOTIFICATION_ID, buildMonitorNotification())

        updateStatus("Мониторинг: ${networkLabel()}", notify = false)



        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)

        }



        scope.launch {

            while (isActive) {

                delay(30_000)

                switchMutex.withLock { syncTransportState() }

            }

        }



        scope.launch {

            ShizukuManager.awaitUserService(10_000)

            ShizukuManager.shellReady(forceRefresh = true)

            syncTransportNow()

            updateStatus("Мониторинг: ${networkLabel()}", notify = false)

        }

    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY



    override fun onDestroy() {

        syncJob?.cancel()

        disconnectJob?.cancel()

        try {

            connectivityManager.unregisterNetworkCallback(networkCallback)

        } catch (e: Exception) {

            Log.w(TAG, "unregister network callback", e)

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            try {

                connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)

            } catch (e: Exception) {

                Log.w(TAG, "unregister default callback", e)

            }

        }

        MonitorWakeLock.release()

        ShizukuManager.stop()

        scope.cancel()

        super.onDestroy()

    }



    override fun onBind(intent: Intent?): IBinder? = null



    private fun onNetworkEvent(reason: String) {

        Log.d(TAG, "network event: $reason")

        scheduleTransportSync()

    }



    private fun scheduleTransportSync() {

        syncJob?.cancel()

        syncJob = scope.launch {

            delay(800)

            switchMutex.withLock { syncTransportState() }

        }

    }



    private fun syncTransportState() {
        val wifiNow = NetworkTransport.hasWifi(connectivityManager)
        val cellNow = NetworkTransport.hasCellular(connectivityManager)

        if (wasWifi && !wifiNow) {
            Log.i(TAG, "wifi edge: lost")
            maybeStartWifiOffFlow()
        }
        if (!wasWifi && wifiNow) {
            Log.i(TAG, "wifi edge: gained")
            disconnectJob?.cancel()
            handlingDisconnect = false
            onWifiReturned()
        }

        wasWifi = wifiNow
        lastTransport = when {
            wifiNow -> NetworkCapabilities.TRANSPORT_WIFI
            cellNow -> NetworkCapabilities.TRANSPORT_CELLULAR
            else -> lastTransport
        }
    }



    private fun maybeStartWifiOffFlow() {

        if (handlingDisconnect) return

        if (!prefs.switchAlways && !prefs.switchOnUnavailable) return

        if (SystemClock.elapsedRealtime() < suppressWifiOffUntil) return

        if (NetworkTransport.hasWifi(connectivityManager)) return



        disconnectJob?.cancel()

        disconnectJob = scope.launch { runWifiDisconnectedFlow() }

    }



    private suspend fun runWifiDisconnectedFlow() {

        if (handlingDisconnect) return

        handlingDisconnect = true

        MonitorWakeLock.acquire(this, 60_000L)

        try {

            updateStatus("Wi-Fi пропал, ждём…", notify = false)
            val waitMs = if (prefs.switchAlways) 3_000L else AppConstants.WIFI_LOST_PROBE_DELAY_MS
            delay(waitMs)

            if (!ShizukuManager.shellReady()) {
                updateStatus("Нужен Shizuku — нажмите «Настроить Shizuku»")
                return
            }



            if (NetworkTransport.hasWifi(connectivityManager)) {

                updateStatus("Wi-Fi снова есть — пропуск")

                syncTransportNow()

                return

            }



            if (!ShizukuManager.isReady && !ShizukuManager.awaitUserService(8_000)) {

                updateStatus("Shizuku недоступен — откройте приложение")

                return

            }



            if (prefs.switchAlways) {
                switchToHappWithStatus("Wi-Fi off → Happ…")
                return
            }

            if (!prefs.switchOnUnavailable) return

            val network = NetworkTransport.findCellularNetwork(connectivityManager)

            if (network == null) {
                updateStatus("Нет mobile сети", notify = false)
                return
            }

            updateStatus("Проверка сети (mobile)…", notify = false)

            if (TelegramProbe.isReachable(network)) {
                updateStatus("Сеть доступна — VPN без изменений", notify = false)
                return
            }

            switchToHappWithStatus("Сеть недоступна → Happ…")

        } catch (e: CancellationException) {

            updateStatus("Переключение отменено", notify = false)

            throw e

        } catch (e: Exception) {

            Log.e(TAG, "wifi disconnect flow failed", e)

            updateStatus("Ошибка: ${e.message ?: "переключение"}")

        } finally {

            handlingDisconnect = false

            MonitorWakeLock.release()

            syncTransportNow()

        }

    }



    private suspend fun switchToHappWithStatus(startMessage: String) {

        updateStatus(startMessage, notify = false)

        try {

            withTimeout(90_000) {

                orchestrator.switchToHapp("")

            }

            delay(1_500)

            updateStatus(happStatusAfterSwitch(), notify = false)

        } catch (_: TimeoutCancellationException) {

            updateStatus(

                if (VpnMonitor.isLikelyHappActive(this)) "Happ: подключён"

                else "Happ: таймаут — нажмите виджет вручную",

                notify = !VpnMonitor.isLikelyHappActive(this),

            )

        }

    }



    private fun happStatusAfterSwitch(): String = when {

        VpnMonitor.isLikelyHappActive(this) -> "Happ: подключён"

        VpnMonitor.isVpnActive(this) -> "VPN активен"

        else -> "Happ: не подключён — нажмите виджет"

    }



    private fun onWifiReturned() {
        scope.launch {
            if (prefs.switchAlways || prefs.switchOnUnavailable) {
                if (ShizukuManager.shellReady() || ShizukuManager.awaitUserService(5_000) && ShizukuManager.shellReady()) {
                    orchestrator.prepareWifiMode()
                }
            }
            onWifiConnectedMaybe()
        }
    }



    private fun syncTransportNow() {
        wasWifi = NetworkTransport.hasWifi(connectivityManager)
        NetworkTransport.resolve(connectivityManager)?.let { lastTransport = it }
    }



    private fun onWifiConnectedMaybe() {

        if (SystemClock.elapsedRealtime() < suppressWifiReminderUntil) return

        onWifiConnected()

    }



    private fun onWifiConnected() {
        updateStatus("Wi-Fi подключён", notify = false)
        if (!prefs.pushEnabled) return
        sendWifiVpnReminder()
    }



    private fun sendWifiVpnReminder() {

        val open = PendingIntent.getActivity(

            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,

        )



        val notification = NotificationCompat.Builder(this, CHANNEL_REMINDER)

            .setSmallIcon(R.drawable.ic_notification)

            .setContentTitle(getString(R.string.wifi_vpn_reminder_title))

            .setContentText(getString(R.string.wifi_vpn_reminder_text))

            .setContentIntent(open)

            .setAutoCancel(true)

            .setPriority(NotificationCompat.PRIORITY_HIGH)

            .build()



        getSystemService(NotificationManager::class.java)

            .notify(REMINDER_NOTIFICATION_ID, notification)

    }



    private fun networkLabel(): String =

        NetworkTransport.label(NetworkTransport.resolve(connectivityManager))



    private fun updateStatus(text: String, notify: Boolean = true) {

        Log.i(TAG, text)

        prefs.lastStatus = text

        if (!notify || !prefs.pushEnabled) {
            getSystemService(NotificationManager::class.java).cancel(ACTION_NOTIFICATION_ID)
            return
        }

        if (isActionRequired(text)) {
            showActionNotification(text)
        } else {
            getSystemService(NotificationManager::class.java).cancel(ACTION_NOTIFICATION_ID)
        }

    }



    private fun isActionRequired(text: String): Boolean {

        val t = text.lowercase()

        return t.contains("нажмите") ||

            t.contains("виджет") ||

            t.contains("shizuku") ||

            t.contains("разрешение") ||

            t.contains("ошибка")

    }



    private fun showActionNotification(text: String) {

        val open = PendingIntent.getActivity(

            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,

        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ACTION)

            .setSmallIcon(R.drawable.ic_notification)

            .setContentTitle(getString(R.string.app_name))

            .setContentText(text)

            .setContentIntent(open)

            .setAutoCancel(true)

            .setOnlyAlertOnce(true)

            .setPriority(NotificationCompat.PRIORITY_HIGH)

            .setCategory(NotificationCompat.CATEGORY_REMINDER)

            .build()

        getSystemService(NotificationManager::class.java).notify(ACTION_NOTIFICATION_ID, notification)

    }



    private fun ensureChannels() {

        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(

            NotificationChannel(CHANNEL_MONITOR, "Сервис", NotificationManager.IMPORTANCE_MIN).apply {

                setShowBadge(false)

                enableVibration(false)

                enableLights(false)

                setSound(null, null)

            },

        )

        nm.createNotificationChannel(

            NotificationChannel(CHANNEL_ACTION, getString(R.string.action_notification_channel), NotificationManager.IMPORTANCE_HIGH),

        )

        nm.createNotificationChannel(

            NotificationChannel(CHANNEL_REMINDER, "Напоминания", NotificationManager.IMPORTANCE_HIGH),

        )

    }



    private fun buildMonitorNotification(): Notification {

        val open = PendingIntent.getActivity(

            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,

        )



        return NotificationCompat.Builder(this, CHANNEL_MONITOR)

            .setSmallIcon(R.drawable.ic_notification)

            .setContentTitle(getString(R.string.app_name))

            .setContentText(getString(R.string.monitor_notification_text))

            .setContentIntent(open)

            .setOngoing(true)

            .setSilent(true)

            .setShowWhen(false)

            .setCategory(NotificationCompat.CATEGORY_SERVICE)

            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            .setPriority(NotificationCompat.PRIORITY_MIN)

            .build()

    }



    companion object {

        private const val TAG = "NetworkMonitor"

        private const val NOTIFICATION_ID = 1

        private const val REMINDER_NOTIFICATION_ID = 2

        private const val ACTION_NOTIFICATION_ID = 3

        private const val CHANNEL_MONITOR = "vpn_switcher_monitor"

        private const val CHANNEL_ACTION = "vpn_switcher_action"

        private const val CHANNEL_REMINDER = "vpn_switcher_reminder"



        fun start(context: android.content.Context) {

            context.startForegroundService(Intent(context, NetworkMonitorService::class.java))

        }



        fun stop(context: android.content.Context) {

            context.stopService(Intent(context, NetworkMonitorService::class.java))

        }

    }

}


