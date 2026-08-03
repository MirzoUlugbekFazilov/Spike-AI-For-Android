package com.spikeai.app

import android.app.Application
import com.spikeai.app.auth.AuthManager
import com.spikeai.app.blocking.FocusController
import com.spikeai.app.data.Connectivity
import com.spikeai.app.data.LocalPrefs
import com.spikeai.app.data.SyncEngine
import com.spikeai.app.localization.AppLocalization
import com.spikeai.app.notifications.NotificationScheduler
import com.spikeai.app.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SpikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        // Track network reachability and auto-sync the offline cache when it returns.
        Connectivity.init(this)
        SyncEngine.init()

        // Begin observing Supabase auth/session state.
        AuthManager.start()

        // Notification channels + the recurring daily quote nudge.
        runCatching {
            Notifications.createChannels(this)
            NotificationScheduler.scheduleDailyQuote(this)
        }

        // Mirror the saved in-app language into AppLocalization as early as possible,
        // so the app-blocking overlay (drawn outside Compose) is localized even when
        // this process was started just to host the accessibility service.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val prefs = LocalPrefs(this@SpikeApp)
            runCatching {
                val saved = prefs.language.first()
                if (saved.isNotBlank()) AppLocalization.setActiveLanguage(saved)
            }
            // Re-arm an interrupted blocking session: if prefs say one is active but the
            // in-memory controller was reset (process death / reboot / app update), restore
            // it so the accessibility service resumes enforcing instead of just showing an
            // "active" banner that no longer blocks anything.
            runCatching {
                if (!FocusController.active &&
                    prefs.focusActive.first() &&
                    FocusController.isBlockingMode(prefs.focusMode.first())
                ) {
                    FocusController.restore(
                        mode = prefs.focusMode.first(),
                        packages = prefs.blockedPackages.first(),
                    )
                }
            }
        }
    }

    companion object {
        lateinit var instance: SpikeApp
            private set
    }
}
