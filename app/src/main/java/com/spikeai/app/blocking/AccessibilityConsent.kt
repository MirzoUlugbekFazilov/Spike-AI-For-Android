package com.spikeai.app.blocking

import android.content.Context

/**
 * Persisted record of the user's answer to the accessibility prominent disclosure.
 *
 * Google Play's User Data policy requires that an app which uses the AccessibilityService
 * API for a non-accessibility purpose obtain consent through an in-app prominent
 * disclosure *before* the capability is used. Enabling the service in Android's own
 * Settings is NOT that consent: the user can reach the system toggle without ever having
 * opened Spike AI, and the system screen is Android's disclosure, not ours.
 *
 * So this flag — not the OS toggle — is what gates
 * [AppBlockerAccessibilityService.onAccessibilityEvent]. Until it is set, the service
 * reads nothing at all, no matter what the OS says.
 *
 * ## Why SharedPreferences and not DataStore
 *
 * The rest of the app stores preferences in DataStore ([com.spikeai.app.data.LocalPrefs]),
 * and this key deliberately does not live there. `onAccessibilityEvent` is a synchronous
 * callback invoked on every window-state change across the whole device; DataStore is
 * coroutine-only, so reading it there would mean either blocking the callback thread with
 * `runBlocking` or letting events through while an async read is in flight. Both are
 * unacceptable when the thing being gated is "may we look at this at all?".
 *
 * SharedPreferences reads are synchronous and served from an in-memory map after the
 * first load, and [cached] collapses the steady-state cost to a volatile field read.
 * Do not migrate this to DataStore.
 */
object AccessibilityConsent {

    private const val PREFS_NAME = "spike_a11y_consent"
    private const val KEY_GRANTED = "accessibility_consent_granted"

    /**
     * Process-wide cache of the stored value. `null` means "not read from disk yet".
     * Volatile because the accessibility service reads it from the system's callback
     * thread while the UI writes it from the main thread.
     */
    @Volatile
    private var cached: Boolean? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * True only if the user has tapped the affirmative button on the in-app prominent
     * disclosure. Safe to call on the accessibility hot path.
     *
     * Defaults to false: absent an explicit grant we must assume no consent, so a fresh
     * install (or cleared data) never reads a foreground package.
     */
    fun isGranted(context: Context): Boolean {
        cached?.let { return it }
        val stored = prefs(context).getBoolean(KEY_GRANTED, false)
        cached = stored
        return stored
    }

    /**
     * Record consent. The ONLY caller is the affirmative button of the disclosure
     * dialog — see `AccessibilityConsentHost`. Never call this from a dismiss handler,
     * a back handler, a lifecycle callback, or in response to the OS toggle changing:
     * Play requires that consent come from an affirmative in-app action and nothing else.
     *
     * Written with `commit()` rather than `apply()` on purpose. The very next thing that
     * happens is `startActivity` to the accessibility settings screen, after which the
     * user may enable the service while our process is backgrounded and killable. An
     * async write that loses the race would leave the service enabled with no recorded
     * consent, which is precisely the state this class exists to make impossible.
     */
    fun grant(context: Context) {
        cached = true
        prefs(context).edit().putBoolean(KEY_GRANTED, true).commit()
    }

    /**
     * Withdraw consent from the in-app settings entry point. The accessibility service
     * stops reading anything immediately, whether or not the OS toggle is still on.
     */
    fun revoke(context: Context) {
        cached = false
        prefs(context).edit().putBoolean(KEY_GRANTED, false).commit()
    }
}
