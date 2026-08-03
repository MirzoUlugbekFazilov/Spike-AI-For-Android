package com.spikeai.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.spikeai.app.notifications.Notifications

/**
 * Watches foreground app changes and enforces the active focus session.
 *
 * On TYPE_WINDOW_STATE_CHANGED we read the foreground package. If a blocking
 * session is active and that package is in the blocked set (and isn't us, a
 * launcher, or system UI/Settings), we draw the [BlockOverlay] on top of it.
 *
 * The actual blocking UI is a WindowManager overlay (not a separate Activity)
 * so the shared AndroidManifest needs no changes.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        /** Running instance, used by [FocusController] to dismiss the overlay. */
        @Volatile
        var instance: AppBlockerAccessibilityService? = null
            private set

        // Packages we should never block (ourselves, system surfaces, launchers).
        private val SYSTEM_PACKAGE_PREFIXES = listOf(
            "com.android.systemui",
            "com.android.settings",
            "android",
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: BlockOverlay? = null

    // Debounce so rapid window events for the same app don't re-trigger the overlay.
    private var lastBlockedPackage: String? = null
    private var lastEventAtMs: Long = 0L
    private val debounceMs = 400L

    // Medium/"Focus" mode: after the user chooses "Yes, open app" we let them stay
    // in THAT one package without re-prompting on every in-app screen change. The
    // pass is single-use and tied to staying in the app — the moment the user
    // leaves it (home, launcher, another app) it is consumed, so re-opening the
    // blocked app prompts the "Are you sure?" shield again.
    private var bypassPackage: String? = null

    // After the user taps "Close" we send them home and briefly suppress the shield.
    // The home transition emits more window events for the app we're leaving, and
    // without this grace window those would instantly re-draw the overlay — making
    // Lock-in's Close button appear to ask a second time before letting the user out.
    private var suppressUntilMs: Long = 0L
    private val homeNavGraceMs = 1500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = BlockOverlay(this)

        // The user can reach Android's accessibility toggle without ever having opened
        // Spike AI, in which case they have not seen our prominent disclosure and we
        // have no consent. The service stays inert in that state (see the gate in
        // onAccessibilityEvent); tell the user why, with a tap target that opens the
        // disclosure, rather than leaving blocking silently doing nothing.
        if (!AccessibilityConsent.isGranted(this)) {
            Notifications.postConsentRequired(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // ── Consent gate — must stay the first thing this method does ──────────
        //
        // Google Play requires in-app prominent disclosure and consent before an app
        // uses the AccessibilityService API for a non-accessibility purpose. Enabling
        // the service in Android Settings is not consent: that path never shows our
        // disclosure. Until the user has affirmatively agreed in-app, we read nothing —
        // note this returns above the eventType check and long before event.packageName
        // is touched, so no data about any other app is observed, not even transiently.
        //
        // Do not move this lower, and do not weaken it to a check on whether the OS
        // toggle is on. This ordering is the app's compliance guarantee.
        if (!AccessibilityConsent.isGranted(this)) {
            clearOverlayIfShown()
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Only engage when a blocking session is live.
        if (!FocusController.active || !FocusController.isBlockingMode(FocusController.mode)) {
            clearOverlayIfShown()
            return
        }

        // Task gate: Focus and Lock-in only enforce while the user still has an
        // uncompleted daily task for today. Once today's list is cleared there's
        // nothing to focus for, so we let blocked apps through (Sweat is exercise-
        // gated, not task-gated). The controller also downgrades the session to
        // Chill when this flips, so this is a belt-and-braces guard for the brief
        // window before that lands.
        if (FocusController.isTaskGatedMode(FocusController.mode) &&
            !FocusController.hasUncompletedTasks
        ) {
            clearOverlayIfShown()
            return
        }

        // The foreground package — the only thing this service ever reads.
        //
        // There is deliberately no rootInActiveWindow fallback: that needs the
        // canRetrieveWindowContent capability, which the service does not declare so
        // that the OS can never hand us screen content (see the service config). A
        // typeWindowStateChanged event without a package name is simply skipped; the
        // next event for that window carries one.
        val pkg = event.packageName?.toString() ?: return

        // Events from our OWN app must never tear down the shield. Crucially, the
        // block overlay is itself a window owned by us, so showing it fires a
        // TYPE_WINDOW_STATE_CHANGED for our package — if we cleared the overlay
        // here it would immediately dismiss itself and nothing would be blocked.
        if (pkg == packageName) return

        // The Focus-mode "open this once" pass only survives while the user stays
        // inside the bypassed app. As soon as the foreground moves to anything else
        // (the launcher, another app), the pass is spent — so coming back to the
        // blocked app re-prompts. Transient system UI (status bar / shade) is
        // ignored so a passing notification doesn't revoke the pass mid-use.
        if (bypassPackage != null && pkg != bypassPackage && !isTransientSystemUi(pkg)) {
            bypassPackage = null
        }

        // System surfaces and the launcher are always allowed → drop any overlay.
        if (isSystemOrLauncher(pkg)) {
            clearOverlayIfShown()
            return
        }

        // Not a blocked app → make sure no stale overlay lingers.
        if (pkg !in FocusController.blockedPackages) {
            clearOverlayIfShown()
            return
        }

        // Sweat mode honours an active temporary unlock window.
        if (FocusController.mode == "sweat" && FocusController.isTemporarilyUnlocked) {
            clearOverlayIfShown()
            return
        }

        // Medium-mode "open this once" pass: while the user remains in the app they
        // explicitly chose to open, don't re-shield it. Leaving the app clears this
        // above, so the next visit prompts again.
        if (pkg == bypassPackage) {
            clearOverlayIfShown()
            return
        }

        // Just sent the user home via "Close" — don't re-shield the app we're
        // leaving while the home transition is still settling. This is what stops
        // Lock-in from prompting a second time after the first Close.
        if (SystemClock.elapsedRealtime() < suppressUntilMs) {
            clearOverlayIfShown()
            return
        }

        // Debounce repeated events for the same foreground package — but only while
        // the overlay is actually up. If it was dismissed (or never drawn) we must
        // re-assert it even for the same package, otherwise re-entering a blocked
        // app would slip through.
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage &&
            now - lastEventAtMs < debounceMs &&
            overlay?.isShowing == true
        ) {
            return
        }
        lastBlockedPackage = pkg
        lastEventAtMs = now

        showBlockOverlay(FocusController.mode)
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: Intent?): Boolean {
        clearOverlayIfShown()
        instance = null
        return super.onUnbind(intent)
    }

    /** Public hook so [FocusController] can dismiss the overlay (e.g. on unlock/stop). */
    fun hideOverlay() {
        mainHandler.post { clearOverlayIfShown() }
    }

    // ── internals ──────────────────────────────────────────────────────────

    private fun showBlockOverlay(mode: String) {
        mainHandler.post {
            val o = overlay ?: BlockOverlay(this).also { overlay = it }
            o.show(
                mode = mode,
                onClose = { goHome() },
                onBypass = {
                    // Medium-mode escape hatch: let the user into this one app for this
                    // one visit. The pass lasts only while they stay in the app — leaving
                    // it (handled in onAccessibilityEvent) revokes the pass, so re-opening
                    // the blocked app asks "Are you sure?" again.
                    bypassPackage = lastBlockedPackage
                },
                onMoveToUnlock = { openSweatUnlock() },
            )
        }
    }

    private fun clearOverlayIfShown() {
        if (overlay?.isShowing == true) overlay?.hide()
    }

    /** Press the OS home button to leave the blocked app. */
    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        lastBlockedPackage = null
        // Hold off re-asserting the shield while the home navigation completes.
        suppressUntilMs = SystemClock.elapsedRealtime() + homeNavGraceMs
    }

    /** Open the Spike app on its Focus/Sweat screen so the user can earn an unlock. */
    private fun openSweatUnlock() {
        runCatching {
            val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("open_focus", true)
                putExtra("open_sweat", true)
            }
            if (launch != null) startActivity(launch) else goHome()
        }.onFailure { goHome() }
    }

    /**
     * Transient system UI (status bar, notification shade, quick settings) that can
     * briefly take foreground while the user is inside an app. We must NOT treat
     * these as "leaving" the bypassed app, or a passing notification would revoke
     * the user's one-time pass mid-use.
     */
    private fun isTransientSystemUi(pkg: String): Boolean =
        pkg == "com.android.systemui" || pkg.startsWith("com.android.systemui.")

    private fun isSystemOrLauncher(pkg: String): Boolean {
        if (SYSTEM_PACKAGE_PREFIXES.any { pkg == it || pkg.startsWith("$it.") }) return true
        // Treat the current default launcher as un-blockable.
        return runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = packageManager.resolveActivity(home, 0)
            resolved?.activityInfo?.packageName == pkg
        }.getOrDefault(false)
    }
}
