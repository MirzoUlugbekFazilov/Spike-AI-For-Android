package com.spikeai.app.ui.permissions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spikeai.app.blocking.AccessibilityConsent
import com.spikeai.app.blocking.AppBlockerAccessibilityService
import com.spikeai.app.blocking.BlockingPermissions
import com.spikeai.app.blocking.FocusController
import com.spikeai.app.notifications.Notifications

/**
 * Sequential setup flow for app blocking.
 *
 * App blocking needs two independent OS grants, and Google Play requires that they be
 * requested **separately**, each with its own dedicated full screen:
 *
 *  1. **Accessibility Service** — a data-access permission, so it needs a prominent
 *     disclosure before the request ([AccessibilityDisclosureScreen]).
 *  2. **Display over other apps** — requested only *after* step 1 succeeds, on its own
 *     screen ([OverlayDisclosureScreen]).
 *
 * A previous version asked for both at once in an AlertDialog titled "Permission
 * required" with Cancel / Continue buttons. That was non-compliant on four counts: it
 * was a dialog rather than a dedicated screen, "Continue" is not explicit consent, the
 * data usage was not explained, and it bundled two permissions into a single decision.
 * Do not reintroduce a combined prompt.
 *
 * ## State lives outside composition, deliberately
 *
 * The nav scaffold picks a different layout branch for compact vs. expanded widths, so
 * rotating a phone or unfolding a tablet disposes the whole screen subtree. Holding the
 * step here, and rendering it from [BlockingSetupHost] above that branch, means no
 * layout, tab or configuration change can take the disclosure off screen — which would
 * read exactly like the auto-dismissing message Play prohibits.
 */
object BlockingSetupFlow {

    enum class Step { NONE, ACCESSIBILITY, OVERLAY }

    var step by mutableStateOf(Step.NONE)
        private set

    /**
     * True while we are waiting for the user to come back from the system accessibility
     * screen, so that returning can advance the flow to the overlay step.
     *
     * This is NOT a re-prompt mechanism. If the user comes back without having enabled
     * the service, the flag is cleared and nothing is shown — see [onReturnFromSettings].
     */
    private var awaitingAccessibilityResult = false

    /**
     * Begin (or resume) setup. Always called from an explicit user tap — never from a
     * lifecycle callback, a timer, or app start. There are no auto-redirects and no
     * auto-popups anywhere in this flow.
     *
     * Resolves to exactly one outcome:
     *
     *  - **Accessibility not fully set up** — either no consent on record, or the OS
     *    toggle is off, or both → the full-screen accessibility disclosure. The system
     *    settings screen is reachable only from its "Allow Access" button.
     *  - **Accessibility fully set up, overlay missing** → the overlay screen, on its own.
     *  - **Everything already granted** → nothing happens.
     *
     * ## Why the disclosure is shown every time, not just the first
     *
     * This used to short-circuit when consent was already stored but the service was
     * switched off: it went straight to the system screen, on the reasoning that the
     * disclosure had been answered once and re-showing it would be nagging. That reads
     * fine from inside the code and badly from outside it. The reviewer's path is:
     * tap Grant → read the disclosure → tap Allow Access → land in Settings → *not*
     * enable the toggle → come back → tap Grant again → **the app opens system settings
     * with no disclosure at all**. That is indistinguishable from an app that requests a
     * sensitive permission without disclosing why, and this app has already been rejected
     * once under that policy.
     *
     * The cost of showing it again is one extra tap for a returning user. The cost of not
     * showing it is another rejection. Do not re-add the short-circuit.
     *
     * Note the condition is keyed on the stored consent flag *and* the OS toggle, never
     * the toggle alone. Skipping the disclosure because the service happens to be enabled
     * would let a user who switched it on from Android Settings bypass the disclosure
     * permanently — the exact non-compliant design this flow exists to prevent.
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        val consented = AccessibilityConsent.isGranted(appContext)
        val serviceEnabled = BlockingPermissions.isAccessibilityEnabled(appContext)

        step = when {
            // Both halves must hold before we consider step 1 done. Either one missing
            // sends the user through the disclosure again — no silent trip to Settings.
            !consented || !serviceEnabled -> Step.ACCESSIBILITY
            !BlockingPermissions.hasOverlay(appContext) -> Step.OVERLAY
            else -> Step.NONE
        }
    }

    /**
     * Begin the overlay step on its own, for the standalone "Grant" button on the
     * overlay card in [PermissionsScreen].
     *
     * That button used to call `BlockingPermissions.openOverlay` directly, which sent the
     * user straight to the system screen with no disclosure at all — the exact
     * "requests a permission before disclosing why" pattern Play rejects, and a direct
     * violation of the rule documented on [rememberBlockingSetup]. The sequential flow
     * always showed [OverlayDisclosureScreen], so the gap only existed on this one
     * entry point, which is precisely why it survived review of the main path.
     *
     * Deliberately does NOT fall back to the accessibility step: a user who tapped the
     * overlay card asked about the overlay permission, and silently redirecting them to a
     * different permission's disclosure is its own kind of bait-and-switch. The cards are
     * independent, and each one leads to its own disclosure.
     */
    fun startOverlay(context: Context) {
        val appContext = context.applicationContext
        step = if (BlockingPermissions.hasOverlay(appContext)) Step.NONE else Step.OVERLAY
    }

    /**
     * The user tapped "Allow Access" on the accessibility disclosure. This is the ONLY
     * place consent is recorded — no dismiss handler, lifecycle callback or OS toggle
     * change may write it.
     */
    fun onAccessibilityAllowed(context: Context) {
        val appContext = context.applicationContext
        step = Step.NONE

        // Persist before navigating: the user may enable the service while our process
        // is backgrounded and killable, and the service reads nothing until it can see
        // this flag.
        AccessibilityConsent.grant(appContext)
        Notifications.cancelConsentRequired(appContext)

        if (BlockingPermissions.isAccessibilityEnabled(appContext)) {
            // Already enabled in Android Settings — this user is consenting
            // retroactively. Don't bounce them to a screen whose toggle is already on;
            // move to the next permission instead.
            if (!BlockingPermissions.hasOverlay(appContext)) step = Step.OVERLAY
        } else {
            awaitingAccessibilityResult = true
            BlockingPermissions.openAccessibility(appContext)
        }
    }

    /** The user tapped "Allow Access" on the overlay screen. */
    fun onOverlayAllowed(context: Context) {
        step = Step.NONE
        BlockingPermissions.openOverlay(context.applicationContext)
    }

    /**
     * Withdraw **both** blocking permissions in one action.
     *
     * Google Play requires that consent be as easy to take back as it was to give. That
     * means undoing everything the two disclosures asked for, not just the flag this app
     * happens to own:
     *
     *  1. **Consent flag** — cleared first, so the service stops reading immediately even
     *     if the steps below fail.
     *  2. **Any running session** — stopped, so blocking cannot outlive the permission
     *     that justified it.
     *  3. **The accessibility service itself** — `disableSelf()` switches the OS toggle
     *     off, so Android's own Accessibility screen agrees with what the app just told
     *     the user. Without this the toggle would stay on and the user would reasonably
     *     believe withdrawal had failed. No-op if the service is not running.
     *  4. **Display over other apps** — Android provides **no API** for an app to drop
     *     `SYSTEM_ALERT_WINDOW`, so this is the one part the user must finish themselves.
     *     The settings screen is opened for them and the confirmation dialog says so in
     *     advance; silently leaving it granted while claiming both were withdrawn would
     *     be the misleading kind of claim that fails review.
     *
     * Opening a settings screen from here does not contradict the disclosure-first rule
     * on [rememberBlockingSetup]: that rule exists so a permission is never *requested*
     * before it is explained. This navigates so a permission can be *removed*, after an
     * explicit confirmation, and grants nothing.
     */
    fun withdrawAll(context: Context) {
        val appContext = context.applicationContext
        step = Step.NONE
        awaitingAccessibilityResult = false

        AccessibilityConsent.revoke(appContext)
        FocusController.stop(appContext)
        AppBlockerAccessibilityService.instance?.disableSelf()
        Notifications.cancelConsentRequired(appContext)

        if (BlockingPermissions.hasOverlay(appContext)) {
            BlockingPermissions.openOverlay(appContext)
        }
    }

    /**
     * Cancel, or back, on either screen. Both mean "no".
     *
     * Nothing is written, no settings screen opens, and no re-prompt is scheduled. The
     * user returns to a fully working app with blocking simply unavailable, and can
     * restart the flow whenever they like from Profile -> App blocking. Declining
     * repeatedly is not penalised: there is no counter and no escalation, so the tenth
     * decline behaves exactly like the first.
     */
    fun cancel(context: Context) {
        step = Step.NONE
        awaitingAccessibilityResult = false
        Notifications.cancelConsentRequired(context.applicationContext)
    }

    /**
     * Called on every app resume by [BlockingSetupHost].
     *
     * Its only job is to continue a flow the user already started: if they just enabled
     * the accessibility service, move on to the overlay step. Everything else is a
     * no-op.
     *
     * In particular, if the user opened the system screen and came back **without**
     * enabling anything, the pending flag is cleared and nothing is displayed. That is
     * the difference between continuing a flow and nagging: we never re-show a screen
     * the user has walked away from.
     */
    fun onReturnFromSettings(context: Context) {
        if (!awaitingAccessibilityResult) return
        awaitingAccessibilityResult = false

        val appContext = context.applicationContext
        val serviceEnabled = BlockingPermissions.isAccessibilityEnabled(appContext)
        val overlayMissing = !BlockingPermissions.hasOverlay(appContext)

        // Advance only on success. If the service was not enabled, we stop here and the
        // user is left in the app with no prompt of any kind.
        if (serviceEnabled && overlayMissing) step = Step.OVERLAY
    }
}

/**
 * Handle returned by [rememberBlockingSetup]. The single supported way to begin enabling
 * app blocking.
 */
@Stable
class BlockingSetupController internal constructor(private val appContext: Context) {
    /** Start the sequential setup flow. Never opens system settings unannounced. */
    fun start() = BlockingSetupFlow.start(appContext)

    /**
     * Start the overlay step alone, for the overlay card's own "Grant" button.
     * Shows [OverlayDisclosureScreen] first; only its "Allow Access" opens settings.
     */
    fun startOverlay() = BlockingSetupFlow.startOverlay(appContext)

    /**
     * Withdraw both permissions. Call only after an explicit confirmation — this stops
     * blocking, turns the accessibility service off, and opens the overlay settings
     * screen if that permission is still granted.
     */
    fun withdrawAll() = BlockingSetupFlow.withdrawAll(appContext)
}

/**
 * Hosts the setup screens. Call this **once**, wrapping the whole app UI, above any
 * layout that can be swapped out — it is mounted at the app root in `AppNav`.
 *
 * While a step is active it *replaces* [content] rather than floating over it. That is
 * what makes these dedicated full screens rather than overlays, and it removes any
 * possibility of interacting with the app behind them.
 */
@Composable
fun BlockingSetupHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    // Continue the flow when the user comes back from a system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) BlockingSetupFlow.onReturnFromSettings(appContext)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (BlockingSetupFlow.step) {
        BlockingSetupFlow.Step.NONE -> content()

        BlockingSetupFlow.Step.ACCESSIBILITY -> AccessibilityDisclosureScreen(
            onAllow = { BlockingSetupFlow.onAccessibilityAllowed(context) },
            onCancel = { BlockingSetupFlow.cancel(context) },
        )

        BlockingSetupFlow.Step.OVERLAY -> OverlayDisclosureScreen(
            onAllow = { BlockingSetupFlow.onOverlayAllowed(context) },
            onCancel = { BlockingSetupFlow.cancel(context) },
        )
    }
}

/**
 * The single supported way to send a user toward either permission screen.
 *
 * Never call `BlockingPermissions.openAccessibility` or `openOverlay` directly from UI —
 * doing so bypasses the disclosure and is exactly what got the app rejected before.
 */
@Composable
fun rememberBlockingSetup(): BlockingSetupController {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) { BlockingSetupController(appContext) }
}

/**
 * Show the accessibility disclosure from outside composition.
 *
 * Used by `MainActivity` when the app is opened from the "app blocking needs your
 * permission" notification, posted when the service is enabled in Android Settings by a
 * user who has never seen the disclosure. Showing it grants nothing — the user still has
 * to tap "Allow Access".
 */
fun showAccessibilityDisclosure(context: Context) {
    BlockingSetupFlow.start(context)
}
