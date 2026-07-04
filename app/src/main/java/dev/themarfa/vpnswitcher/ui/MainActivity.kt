package dev.themarfa.vpnswitcher.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.themarfa.vpnswitcher.AppConstants
import dev.themarfa.vpnswitcher.R
import dev.themarfa.vpnswitcher.databinding.ActivityMainBinding
import dev.themarfa.vpnswitcher.network.NetworkTransport
import dev.themarfa.vpnswitcher.prefs.AppPreferences
import dev.themarfa.vpnswitcher.service.NetworkMonitorService
import dev.themarfa.vpnswitcher.shizuku.ShizukuManager
import dev.themarfa.vpnswitcher.update.GitHubUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private var refreshJob: Job? = null
    private var suppressSwitchListener = false

    private val shizukuReadyListener = { refreshUi() }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        refreshUi()
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            ShizukuManager.bindUserService()
            Toast.makeText(this, "Shizuku разрешён", Toast.LENGTH_SHORT).show()
        }
    }

    private val statusPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppConstants.KEY_LAST_STATUS) {
            runOnUiThread { binding.statusText.text = prefs.lastStatus }
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshUi()
        if (VpnService.prepare(this) == null) {
            Toast.makeText(this, "VPN разрешение получено", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ShizukuManager.start()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()
        prefs = AppPreferences(this)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        ShizukuManager.addOnReadyListener(shizukuReadyListener)
        prefs.shared.registerOnSharedPreferenceChangeListener(statusPrefListener)

        val ver = packageManager.getPackageInfo(packageName, 0)
        binding.versionText.text = "v${ver.versionName}"

        bindSwitches()
        binding.shizukuButton.setOnClickListener { requestShizuku() }
        binding.vpnPermissionButton.setOnClickListener { requestVpnPermission() }
        binding.aboutButton.setOnClickListener { showAboutDialog() }

        requestNotificationPermissionIfNeeded()
        refreshUi()
        syncServiceState()

        lifecycleScope.launch {
            delay(2_000)
            checkForUpdates(silent = true)
        }
    }

    override fun onStart() {
        super.onStart()
        ShizukuManager.bindUserService()
        if (Shizuku.pingBinder() && !ShizukuManager.hasPermission()) {
            ShizukuManager.requestPermission()
        }
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                refreshUi()
                delay(4_000)
            }
        }
    }

    override fun onStop() {
        refreshJob?.cancel()
        refreshJob = null
        super.onStop()
    }

    override fun onDestroy() {
        ShizukuManager.removeOnReadyListener(shizukuReadyListener)
        prefs.shared.unregisterOnSharedPreferenceChangeListener(statusPrefListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        if (isFinishing && !prefs.shouldRunService()) {
            ShizukuManager.stop()
        }
        super.onDestroy()
    }

    private fun bindSwitches() {
        suppressSwitchListener = true
        binding.switchOnUnavailable.isChecked = prefs.switchOnUnavailable
        binding.switchAlways.isChecked = prefs.switchAlways
        binding.switchPush.isChecked = prefs.pushEnabled
        suppressSwitchListener = false

        binding.switchOnUnavailable.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            if (checked && !ensureReadyForSwitch()) {
                suppressSwitchListener = true
                binding.switchOnUnavailable.isChecked = false
                suppressSwitchListener = false
                return@setOnCheckedChangeListener
            }
            if (checked) {
                suppressSwitchListener = true
                binding.switchAlways.isChecked = false
                suppressSwitchListener = false
                prefs.switchAlways = false
            }
            prefs.switchOnUnavailable = checked
            syncServiceState()
        }

        binding.switchAlways.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            if (checked && !ensureReadyForSwitch()) {
                suppressSwitchListener = true
                binding.switchAlways.isChecked = false
                suppressSwitchListener = false
                return@setOnCheckedChangeListener
            }
            if (checked) {
                suppressSwitchListener = true
                binding.switchOnUnavailable.isChecked = false
                suppressSwitchListener = false
                prefs.switchOnUnavailable = false
            }
            prefs.switchAlways = checked
            syncServiceState()
        }

        binding.switchPush.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            prefs.pushEnabled = checked
            if (checked) requestNotificationPermissionIfNeeded()
            syncServiceState()
        }
    }

    private fun syncServiceState() {
        if (prefs.shouldRunService()) {
            NetworkMonitorService.start(this)
        } else {
            NetworkMonitorService.stop(this)
        }
    }

    private fun refreshUi() {
        binding.statusText.text = buildStatusLine()
        val ready = isSetupReady()
        binding.statusChipText.text = if (ready) {
            getString(R.string.status_ready)
        } else {
            getString(R.string.status_setup)
        }
        binding.statusChipText.setTextColor(
            getColor(if (ready) R.color.vg_secondary else R.color.vg_on_surface_variant),
        )
    }

    private fun buildStatusLine(): String {
        val cm = getSystemService(ConnectivityManager::class.java)
        val net = NetworkTransport.networkSummary(cm)
        return "${prefs.lastStatus} · $net"
    }

    private fun isSetupReady(): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (!ShizukuManager.hasPermission()) return false
        if (!ShizukuManager.shellReady()) return false
        if (VpnService.prepare(this) != null) return false
        return true
    }

    private fun applyWindowInsets() {
        val pad = (resources.displayMetrics.density * 12).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainScroll) { view, insets ->
            val cutout = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(
                left = cutout.left + pad,
                top = cutout.top + pad,
                right = cutout.right + pad,
                bottom = cutout.bottom + pad,
            )
            insets
        }
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Запустите Shizuku", Toast.LENGTH_LONG).show()
            return
        }
        if (!ShizukuManager.hasPermission()) {
            Shizuku.requestPermission(ShizukuManager.REQUEST_CODE)
            Toast.makeText(this, "Подтвердите доступ в Shizuku", Toast.LENGTH_LONG).show()
            return
        }
        ShizukuManager.bindUserService()
        if (ShizukuManager.shellReady(forceRefresh = true)) {
            Toast.makeText(this, "Shell работает", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Откройте Shizuku и подтвердите доступ", Toast.LENGTH_LONG).show()
            ShizukuManager.openManager(this)
        }
        refreshUi()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "VPN разрешение уже есть", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensureReadyForSwitch(): Boolean {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Запустите Shizuku", Toast.LENGTH_LONG).show()
            return false
        }
        if (!ShizukuManager.hasPermission()) {
            Toast.makeText(this, "Настройте Shizuku", Toast.LENGTH_LONG).show()
            ShizukuManager.requestPermission()
            return false
        }
        if (!ShizukuManager.shellReady(forceRefresh = true)) {
            Toast.makeText(this, "Shell не работает — дайте разрешение в Shizuku", Toast.LENGTH_LONG).show()
            ShizukuManager.openManager(this)
            return false
        }
        if (VpnService.prepare(this) != null) {
            Toast.makeText(this, "Нужно VPN-разрешение", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_body)
            .setItems(
                arrayOf(
                    getString(R.string.about_github),
                    getString(R.string.about_donate),
                    getString(R.string.about_donate_crypto),
                    getString(R.string.about_check_update),
                ),
            ) { _, which ->
                when (which) {
                    0 -> openUrl(AppConstants.GITHUB_URL)
                    1 -> openUrl(AppConstants.DONATE_URL)
                    2 -> openUrl(AppConstants.DONATE_CRYPTO_URL)
                    3 -> lifecycleScope.launch { checkForUpdates(silent = false) }
                }
            }
            .show()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private suspend fun checkForUpdates(silent: Boolean) {
        val current = packageManager.getPackageInfo(packageName, 0).versionName ?: return
        val update = GitHubUpdater.checkForUpdate(current)
        if (update == null) {
            if (!silent) {
                Toast.makeText(this, R.string.about_update_none, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (silent) {
            Toast.makeText(
                this,
                getString(R.string.about_update_available, update.versionName),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.about_update_available, update.versionName))
            .setMessage(update.releaseNotes.ifBlank { "Загрузить и установить?" })
            .setPositiveButton("Обновить") { _, _ ->
                lifecycleScope.launch { downloadAndInstall(update) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun downloadAndInstall(update: dev.themarfa.vpnswitcher.update.UpdateInfo) {
        val dir = File(cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "update.apk")
        Toast.makeText(this, R.string.about_update_downloading, Toast.LENGTH_SHORT).show()
        val ok = GitHubUpdater.downloadApk(update.downloadUrl, apk) { }
        if (!ok) {
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_LONG).show()
            return
        }
        installApk(apk)
    }

    private fun installApk(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:$packageName".toUri()
            }
            installPermissionLauncher.launch(intent)
            Toast.makeText(this, "Разрешите установку, затем повторите", Toast.LENGTH_LONG).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
