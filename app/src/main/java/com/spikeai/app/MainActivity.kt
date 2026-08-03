package com.spikeai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.spikeai.app.blocking.FocusController
import com.spikeai.app.data.Supabase
import com.spikeai.app.notifications.Notifications
import com.spikeai.app.ui.navigation.AppNavSignals
import com.spikeai.app.ui.navigation.SpikeRoot
import com.spikeai.app.ui.permissions.showAccessibilityDisclosure
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Result is ignored — reminders degrade gracefully if the user declines.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Complete a magic-link / OAuth sign-in if we were opened via the callback URL.
        handleAuthDeepLink(intent)
        ensureNotificationPermission()
        routeFromIntent(intent)
        refreshTaskGate()

        val initialTab = if (intent.hasFocusDeepLink()) FOCUS_TAB else HOME_TAB
        setContent {
            SpikeRoot(initialTab = initialTab)
        } 
    }

    /** Android 13+ requires runtime consent to post notifications (task reminders). */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
        // App was already running — drive navigation through the observable signal,
        // since setContent (and thus initialTab) won't run again here.
        routeFromIntent(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        intent ?: return
        runCatching { Supabase.client.handleDeeplinks(intent) }
    }

    /**
     * Sync the Focus/Lock-in task gate at launch so enforcement reflects the
     * current task list even before the user opens the Focus or Home tab.
     * Best-effort: failures keep the last cached value.
     */
    private fun refreshTaskGate() {
        lifecycleScope.launch {
            runCatching { FocusController.refreshTaskGate(applicationContext) }
        }
    }

    /** Translate a focus/sweat deep-link intent into an observable navigation request. */
    private fun routeFromIntent(intent: Intent?) {
        intent ?: return
        if (intent.hasFocusDeepLink()) {
            AppNavSignals.requestFocus(openSweatSheet = intent.getBooleanExtra("open_sweat", false))
        }
        // Opened from the "app blocking needs your permission" notification: show the
        // prominent disclosure. This only displays the dialog — consent still requires
        // the user to tap the affirmative button in it.
        if (intent.getBooleanExtra(Notifications.EXTRA_OPEN_A11Y_CONSENT, false)) {
            showAccessibilityDisclosure(this)
        }
    }

    private fun Intent.hasFocusDeepLink(): Boolean =
        getBooleanExtra("open_focus", false) || getBooleanExtra("open_sweat", false)

    companion object {
        private const val HOME_TAB = 0
        private const val FOCUS_TAB = 2
    }
}
