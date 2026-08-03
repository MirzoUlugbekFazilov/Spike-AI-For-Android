package com.spikeai.app.blocking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils

/**
 * Central place for the two OS-level permissions that real app blocking needs:
 *  1. Display-over-other-apps (SYSTEM_ALERT_WINDOW) — to draw the block overlay.
 *  2. The Spike accessibility service — to know which app is in the foreground.
 *
 * Usage Access (PACKAGE_USAGE_STATS) used to be requested here as a third permission,
 * but nothing ever queried UsageStatsManager — the foreground package comes from the
 * accessibility service. Google Play only permits sensitive permissions that the app's
 * functionality actually requires, so asking for it was both a policy violation and a
 * false claim to the user. Do not re-add it without a real UsageStatsManager call site.
 *
 * Each `has*` check is a plain (non-composable) function so it can be called from
 * Compose, the service, or anywhere else. Each `open*` launches the relevant
 * system settings screen. All intents add NEW_TASK so they work from any context.
 */
object BlockingPermissions {

    /** Fully-qualified component id of our accessibility service (matches the manifest). */
    private const val ACCESSIBILITY_SERVICE_ID =
        "com.spikeai.app/com.spikeai.app.blocking.AppBlockerAccessibilityService"

    // Settings deep-link extras used to jump straight to a single preference page.
    // These keys are honoured by AOSP Settings and Samsung One UI alike.
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    // Per-service accessibility page (added in Android 11). Referenced by string
    // because the public Settings constant isn't exposed on all compile SDKs.
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
        "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    // ── Status checks ────────────────────────────────────────────────────────

    /** True if Spike can draw overlays on top of other apps. */
    fun hasOverlay(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /**
     * True if our accessibility service is currently enabled. Reads the
     * Settings.Secure list of enabled services and checks for our component.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        return runCatching {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            // The list is colon-separated; match our component case-insensitively.
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (component in splitter) {
                if (component.equals(ACCESSIBILITY_SERVICE_ID, ignoreCase = true)) return true
            }
            false
        }.getOrDefault(false)
    }

    /** True only when BOTH permissions required for real blocking are granted. */
    fun hasAllForBlocking(context: Context): Boolean =
        hasOverlay(context) && isAccessibilityEnabled(context)

    // ── Settings launchers ───────────────────────────────────────────────────

    /** Opens the "Display over other apps" screen, pre-targeting our package when possible. */
    fun openOverlay(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Fallback to the generic list if the package-scoped intent is unavailable.
        if (!launch(context, intent)) {
            launch(
                context,
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Takes the user as close to Spike's accessibility toggle as the OS allows.
     *
     * We try three intents in order of directness:
     *  1. The per-service detail page (Android 11+), which on most phones — Samsung
     *     One UI included — opens the "Spike AI App Blocking" switch directly.
     *  2. The accessibility list with fragment args that scroll to / highlight our row.
     *  3. The plain accessibility list as a last resort.
     *
     * The fragment-args extras are added to every variant because some OEMs read
     * them on the list screen rather than the detail screen.
     */
    fun openAccessibility(context: Context) {
        val componentName = ComponentName(
            context.packageName,
            "com.spikeai.app.blocking.AppBlockerAccessibilityService",
        ).flattenToString()
        val fragmentArgs = Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, componentName) }

        fun Intent.withServiceArgs(): Intent = apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_FRAGMENT_ARG_KEY, componentName)
            putExtra(EXTRA_SHOW_FRAGMENT_ARGS, fragmentArgs)
        }

        // 1. Direct per-service page (API 30+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).withServiceArgs()
            if (launch(context, detail)) return
        }
        // 2. List, pre-targeting our row.
        if (launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).withServiceArgs())) return
        // 3. Plain list.
        launch(
            context,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Best-effort startActivity; returns true on success. */
    private fun launch(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)
}
