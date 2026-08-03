package com.spikeai.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Re-establishes notification infrastructure after the device reboots.
 *
 * Alarms registered with AlarmManager are cleared on reboot, so we:
 *  1. Re-create notification channels.
 *  2. Reschedule the recurring daily quote.
 *  3. Re-arm every still-future task reminder (their times + titles are cached
 *     on-device in [com.spikeai.app.data.LocalPrefs], so no network is needed).
 *
 * Declared in the manifest as exported=true with the BOOT_COMPLETED filter.
 */
class BootReceiver : BroadcastReceiver() {

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        // Be liberal: handle the standard boot action and common OEM/quickboot variants.
        val action = intent.action
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        if (!isBoot) return

        try {
            Notifications.createChannels(context)
            NotificationScheduler.scheduleDailyQuote(context)
        } catch (_: Exception) {
            // Never crash during boot — degrade silently.
        }

        // Reading the reminder store is async; hop off the main thread. Use the app
        // context so the coroutine outlives this short-lived receiver.
        val appContext = context.applicationContext
        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                TaskReminders.rescheduleAll(appContext)
            } catch (_: Exception) {
                // Degrade silently — a missed reschedule is better than a boot crash.
            } finally {
                pending.finish()
            }
        }
    }
}
