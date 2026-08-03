package com.spikeai.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spikeai.app.localization.AppLocalization
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Receives scheduled alarms from [NotificationScheduler] and posts the matching
 * notification via [Notifications].
 *
 * Copy is resolved here (at fire time) rather than baked in at schedule time, so a
 * notification always reflects the user's current in-app language. The scheduled
 * time travels in [NotificationScheduler.EXTRA_WHEN] and is surfaced as the header
 * sub-text (e.g. "Spike AI • 10:00 PM").
 *
 * Declared in the manifest as exported=false (only our own AlarmManager triggers it).
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(NotificationScheduler.EXTRA_TYPE)
            ?: return
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE).orEmpty()
        val notifId = intent.getIntExtra(NotificationScheduler.EXTRA_NOTIF_ID, type.hashCode())
        val whenMillis = intent.getLongExtra(NotificationScheduler.EXTRA_WHEN, 0L)

        val lang = AppLocalization.activeLanguage()
        fun str(key: String) = AppLocalization.string(key, lang)

        val timeText = formatTime(whenMillis, lang)

        when (type) {
            NotificationScheduler.TYPE_TASK -> {
                Notifications.post(
                    context = context,
                    id = notifId,
                    channel = Notifications.CHANNEL_TASKS,
                    title = title.ifBlank { str("notif_task_title_fallback") },
                    text = str("notif_task_text"),
                    subText = timeText,
                )
            }

            NotificationScheduler.TYPE_GOAL -> {
                Notifications.post(
                    context = context,
                    id = notifId,
                    channel = Notifications.CHANNEL_REMINDERS,
                    title = title.ifBlank { str("notif_goal_title_fallback") },
                    text = str("notif_goal_text"),
                    subText = timeText,
                )
            }

            NotificationScheduler.TYPE_QUOTE -> {
                // A generic, offline motivational nudge — we deliberately do NOT block the
                // receiver on a network fetch. The user opens the app to see today's quote.
                Notifications.post(
                    context = context,
                    id = notifId,
                    channel = Notifications.CHANNEL_QUOTES,
                    title = title.ifBlank { str("notif_quote_title") },
                    text = str("notif_quote_text"),
                )
                // Chain tomorrow's quote alarm so the daily cadence self-sustains.
                NotificationScheduler.scheduleDailyQuote(context)
            }

            else -> {
                // Unknown type — ignore safely.
            }
        }
    }

    /** Localized short time (e.g. "10:00 PM" / "22:00") for [millis], or null if unset. */
    private fun formatTime(millis: Long, lang: String): String? {
        if (millis <= 0L) return null
        return runCatching {
            val locale = Locale.forLanguageTag(lang)
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        }.getOrNull()
    }
}
