package com.spikeai.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spikeai.app.MainActivity
import com.spikeai.app.localization.AppLocalization

/**
 * Central helper for notification channels and posting notifications.
 *
 * Channels (Android O+):
 *  - "tasks"     : task reminders
 *  - "quotes"    : daily quote of the day nudge
 *  - "reminders" : goal reminders / general reminders
 *
 * Notes:
 *  - Uses [NotificationCompat] for backwards compatibility.
 *  - Small icon is the branded monochrome bell; the system tints it with [ACCENT_COLOR].
 *  - POST_NOTIFICATIONS (Android 13+) is assumed to be checked/granted elsewhere; all
 *    posting is still guarded with try/catch so a missing permission never crashes.
 */
object Notifications {

    // --- Channel identifiers -------------------------------------------------
    const val CHANNEL_TASKS = "tasks"
    const val CHANNEL_QUOTES = "quotes"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_BLOCKING = "blocking"

    /** Stable id for the "app blocking needs your consent" notification. */
    private const val ID_CONSENT_REQUIRED = 90_001

    /**
     * Intent extra that asks MainActivity to show the accessibility prominent
     * disclosure. Carrying an intent extra never grants anything on its own — it only
     * opens the dialog, which still requires an explicit affirmative tap.
     */
    const val EXTRA_OPEN_A11Y_CONSENT = "open_a11y_consent"

    // Branded monochrome small icon + Spike violet accent used to tint it and the title.
    private val SMALL_ICON = com.spikeai.app.R.drawable.ic_notification
    private const val ACCENT_COLOR = 0xFF8C66FF.toInt()

    /**
     * Creates (or updates) all notification channels. Safe to call repeatedly — the system
     * de-duplicates by channel id. No-op below Android O where channels don't exist.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_TASKS,
                "Task reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for your scheduled tasks"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_QUOTES,
                "Quote of the day",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A daily motivational nudge"
            },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Goal reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for your goals"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_BLOCKING,
                "App blocking setup",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tells you when app blocking still needs your permission"
            }
        )

        try {
            manager.createNotificationChannels(channels)
        } catch (_: Exception) {
            // OEM quirks — never crash on channel creation.
        }
    }

    /**
     * Builds a [PendingIntent] that opens [MainActivity] when the notification is tapped.
     * Returned intent uses a stable requestCode so repeated builds reuse the same slot.
     */
    fun mainActivityIntent(context: Context, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // Bring the existing task to the front rather than spawning a new one.
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * Tells the user that app blocking is switched on at the OS level but still needs
     * their in-app agreement before it can do anything.
     *
     * This exists because the accessibility service is inert until the user accepts the
     * prominent disclosure (see [com.spikeai.app.blocking.AccessibilityConsent]), and a
     * user who enabled the service straight from Android Settings has never been shown
     * that disclosure. Without this they would see blocking simply not work.
     *
     * Tapping it opens Spike AI and shows the disclosure — it does not grant anything.
     */
    fun postConsentRequired(context: Context) {
        val lang = AppLocalization.activeLanguage()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_A11Y_CONSENT, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            ID_CONSENT_REQUIRED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context = context,
            id = ID_CONSENT_REQUIRED,
            channel = CHANNEL_BLOCKING,
            // Non-Composable context (posted from the accessibility service), so this
            // resolves through AppLocalization directly rather than the tr() Composable.
            title = AppLocalization.string("a11y_consent_notif_title", lang),
            text = AppLocalization.string("a11y_consent_notif_body", lang),
            contentIntent = pending,
        )
    }

    /** Clears the consent prompt once the user has answered the disclosure. */
    fun cancelConsentRequired(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_CONSENT_REQUIRED) }
    }

    /**
     * Posts a notification. Any failure (e.g. missing POST_NOTIFICATIONS permission on
     * Android 13+) is swallowed so callers never crash.
     *
     * @param contentIntent optional tap action; if null, a default MainActivity intent is used.
     */
    fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        subText: String? = null,
        contentIntent: PendingIntent? = null
    ) {
        // Ensure the channel exists before posting (cheap & idempotent).
        createChannels(context)

        val notification: Notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(SMALL_ICON)
            .setColor(ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            // Header metadata, e.g. the scheduled time ("Spike AI • 10:00 PM").
            .apply { if (!subText.isNullOrBlank()) setSubText(subText) }
            // Long text expands nicely when the user pulls down the shade.
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(text)
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent ?: mainActivityIntent(context, id))
            .build()

        try {
            // notify() may throw a SecurityException on API 33+ without POST_NOTIFICATIONS.
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission not granted — silently ignore.
        } catch (_: Exception) {
            // Defensive: never let a notification crash the app.
        }
    }
}
