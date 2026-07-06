package dev.themarfa.vpnswitcher.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UpdateAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    UpdateChecker.schedulePeriodic(app)
                }
                UpdateChecker.run(app)
            } finally {
                pending.finish()
            }
        }
    }
}
