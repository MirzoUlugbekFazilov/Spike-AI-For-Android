package com.spikeai.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules and cancels notification alarms via [AlarmManager].
 *
 * Each alarm fires a broadcast to [NotificationReceiver] carrying the extras needed to
 * post the right notification (type, title, text, notifId). Scheduling prefers exact,
 * idle-tolerant alarms where the OS allows it and otherwise falls back to an inexact
 * window so reminders still fire (just less precisely).
 *
 * All scheduling is wrapped in try/catch — some OEMs throw on exact-alarm APIs even when
 * the permission check passes.
 */
object NotificationScheduler {

    // Extra keys carried in the broadcast intent.
    const val EXTRA_TYPE = "type"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_NOTIF_ID = "notifId"
    const val EXTRA_WHEN = "whenMillis"

    // Notification "type" values understood by the receiver.
    const val TYPE_TASK = "task"
    const val TYPE_QUOTE = "quote"
    const val TYPE_GOAL = "goal"

    // Prefixes keep requestCodes from different features from colliding.
    private const val PREFIX_TASK = "task:"
    private const val PREFIX_GOAL = "goal:"
    private const val REQUEST_CODE_QUOTE = 100_001 // fixed, single daily quote alarm.

    // The daily quote fires at 5:00 AM local time.
    private val QUOTE_TIME: LocalTime = LocalTime.of(5, 0)

    // --- Public API ----------------------------------------------------------

    /** Schedules (or replaces) a reminder for the given task at [atEpochMillis]. */
    fun scheduleTaskReminder(context: Context, taskId: String, title: String, atEpochMillis: Long) {
        val code = requestCode(PREFIX_TASK + taskId)
        scheduleAt(
            context = context,
            triggerAtMillis = atEpochMillis,
            requestCode = code,
            type = TYPE_TASK,
            channel = Notifications.CHANNEL_TASKS,
            title = title,
            // Body is resolved by the receiver at fire time so it follows the live language.
            text = "",
            notifId = code
        )
    }

    /** Cancels any pending reminder for the given task. */
    fun cancelTaskReminder(context: Context, taskId: String) {
        cancel(context, requestCode(PREFIX_TASK + taskId))
    }

    /** Schedules (or replaces) a reminder for the given goal at [atEpochMillis]. */
    fun scheduleGoalReminder(context: Context, goalId: String, title: String, atEpochMillis: Long) {
        val code = requestCode(PREFIX_GOAL + goalId)
        scheduleAt(
            context = context,
            triggerAtMillis = atEpochMillis,
            requestCode = code,
            type = TYPE_GOAL,
            channel = Notifications.CHANNEL_REMINDERS,
            title = title,
            text = "",
            notifId = code
        )
    }

    /** Cancels any pending reminder for the given goal. */
    fun cancelGoalReminder(context: Context, goalId: String) {
        cancel(context, requestCode(PREFIX_GOAL + goalId))
    }

    /**
     * Schedules the next "Quote of the Day" notification for the upcoming 5:00 AM local time.
     * When it fires, [NotificationReceiver] posts the quote and calls this again to chain the
     * next day's alarm.
     */
    fun scheduleDailyQuote(context: Context) {
        val triggerAt = nextQuoteEpochMillis()
        scheduleAt(
            context = context,
            triggerAtMillis = triggerAt,
            requestCode = REQUEST_CODE_QUOTE,
            type = TYPE_QUOTE,
            channel = Notifications.CHANNEL_QUOTES,
            title = "",
            text = "",
            notifId = REQUEST_CODE_QUOTE
        )
    }

    /** Computes the epoch-millis of the next 5:00 AM in the device's local time zone. */
    fun nextQuoteEpochMillis(now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): Long {
        var next = now.with(QUOTE_TIME).withSecond(0).withNano(0)
        // If 5 AM today has already passed (or is exactly now), move to tomorrow.
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }

    // --- Internals -----------------------------------------------------------

    /** Stable, non-negative request code derived from a string id. */
    fun requestCode(id: String): Int = id.hashCode() and 0x7FFFFFFF

    private fun alarmManager(context: Context): AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** Builds the broadcast PendingIntent targeting [NotificationReceiver]. */
    private fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        type: String,
        title: String,
        text: String,
        notifId: Int,
        whenMillis: Long
    ): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            // A unique action ensures distinct intents aren't treated as equal (extras are
            // not part of intent identity).
            action = "com.spikeai.app.notifications.FIRE.$requestCode"
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_NOTIF_ID, notifId)
            putExtra(EXTRA_WHEN, whenMillis)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /** Core scheduling routine: picks exact vs. windowed delivery and never crashes. */
    private fun scheduleAt(
        context: Context,
        triggerAtMillis: Long,
        requestCode: Int,
        type: String,
        channel: String,
        title: String,
        text: String,
        notifId: Int
    ) {
        val am = alarmManager(context) ?: return
        val pi = buildPendingIntent(context, requestCode, type, title, text, notifId, triggerAtMillis)

        try {
            if (canScheduleExact(context, am)) {
                // Exact even in Doze. Best precision for reminders the user expects on time.
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // Inexact fallback: a 10-minute window around the target time.
                val windowLength = 10 * 60 * 1000L
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, windowLength, pi)
            }
        } catch (_: SecurityException) {
            // Exact-alarm permission revoked at runtime — degrade to a plain set().
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } catch (_: Exception) {
                // Give up silently; better than crashing.
            }
        } catch (_: Exception) {
            // OEM quirk — swallow.
        }
    }

    /** True if we may schedule exact alarms (always true pre-API 31). */
    private fun canScheduleExact(context: Context, am: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                am.canScheduleExactAlarms()
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    /** Cancels the alarm (and its PendingIntent) for a given request code. */
    private fun cancel(context: Context, requestCode: Int) {
        val am = alarmManager(context) ?: return
        // Rebuild a matching PendingIntent (same requestCode + action) to cancel it.
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.spikeai.app.notifications.FIRE.$requestCode"
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        try {
            am.cancel(pi)
            pi.cancel()
        } catch (_: Exception) {
            // Ignore.
        }
    }
}
