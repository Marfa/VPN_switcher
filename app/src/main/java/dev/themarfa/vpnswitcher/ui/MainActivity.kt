package dev.themarfa.vpnswitcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import dev.themarfa.vpnswitcher.debug.DebugLogCollector
import dev.themarfa.vpnswitcher.prefs.AppPreferences
import dev.themarfa.vpnswitcher.service.NetworkMonitorService
import dev.themarfa.vpnswitcher.shizuku.ShizukuManager
import dev.themarfa.vpnswitcher.update.UpdateChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private var refreshJob: Job? = null
    private var suppressSwitchListener = false
    private var vpnPermissionOk: Boolean? = null

    private val shizukuReadyListener = { refreshUi() }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        refreshUi()
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            ShizukuManager.bindUserService()
            Toast.makeText(this, "Shizuku разрешён", Toast.LENGTH_SHORT).show()
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshUi()
        if (VpnService.prepare(this) == null) {
            vpnPermissionOk = true
            Toast.makeText(this, "VPN разрешение получено", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
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

        val ver = packageManager.getPackageInfo(packageName, 0)
        binding.versionText.text = "v${ver.versionName}"

        bindSwitches()
        binding.shizukuButton.setOnClickListener { requestShizuku() }
        binding.vpnPermissionButton.setOnClickListener { requestVpnPermission() }
        binding.logButton.setOnClickListener { handleLogButton() }
        binding.aboutButton.setOnClickListener { showAboutDialog() }

        setupProxyBanner()
        updateLogButton()
        UpdateChecker.schedulePeriodic(this)
        requestNotificationPermissionIfNeeded()
        refreshUi()
        syncServiceState()

        lifecycleScope.launch {
            delay(2_000)
            UpdateChecker.run(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        NetworkMonitorService.onUiForegroundChanged(true)
    }

    override fun onPause() {
        NetworkMonitorService.onUiForegroundChanged(false)
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        ShizukuManager.bindUserService()
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
        val ready = isSetupReady()
        binding.statusChipText.text = if (ready) {
            getString(R.string.status_ready)
        } else {
            getString(R.string.status_setup)
        }
        binding.statusChipText.setTextColor(
            getColor(if (ready) R.color.vg_secondary else R.color.vg_on_surface_variant),
        )
        updateLogButton()
    }

    private fun isSetupReady(): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (!ShizukuManager.hasPermission()) return false
        if (!ShizukuManager.isReady) return false
        if (!hasVpnPermission()) return false
        return true
    }

    private fun hasVpnPermission(): Boolean {
        vpnPermissionOk?.let { return it }
        val ok = VpnService.prepare(this) == null
        vpnPermissionOk = ok
        return ok
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

    private fun handleLogButton() {
        if (DebugLogCollector.isCollecting) {
            if (DebugLogCollector.stopAndShare(this)) {
                updateLogButton()
            } else {
                Toast.makeText(this, R.string.logs_empty, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (DebugLogCollector.start(this)) {
            Toast.makeText(this, R.string.logs_started, Toast.LENGTH_SHORT).show()
            updateLogButton()
        } else {
            Toast.makeText(this, R.string.logs_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLogButton() {
        binding.logButton.text = if (DebugLogCollector.isCollecting) {
            getString(R.string.btn_stop_logs)
        } else {
            getString(R.string.btn_start_logs)
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
        if (!hasVpnPermission()) {
            Toast.makeText(this, "Нужно VPN-разрешение", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun showAboutDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        view.findViewById<android.view.View>(R.id.aboutGithub).setOnClickListener {
            openUrl(AppConstants.GITHUB_URL)
        }
        view.findViewById<android.view.View>(R.id.aboutDonate).setOnClickListener {
            openUrl(AppConstants.DONATE_URL)
        }
        view.findViewById<android.view.View>(R.id.aboutDonateCrypto).setOnClickListener {
            openUrl(AppConstants.DONATE_CRYPTO_URL)
        }
        view.findViewById<android.view.View>(R.id.aboutUpdate).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch { UpdateChecker.run(this@MainActivity, showToast = true) }
        }
        dialog.show()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private fun setupProxyBanner() {
        binding.proxyAdBanner.setOnClickListener { openUrl(AppConstants.PROXY_AD_URL) }
        try {
            val drawable = ImageDecoder.decodeDrawable(
                ImageDecoder.createSource(resources, R.drawable.proxy_ad_banner),
            )
            binding.proxyAdBanner.setImageDrawable(drawable)
            (drawable as? Animatable)?.start()
        } catch (e: Exception) {
            Log.w(TAG, "proxy banner decode failed", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
