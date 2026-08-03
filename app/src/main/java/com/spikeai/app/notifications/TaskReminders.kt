package com.spikeai.app.notifications

import android.content.Context
import com.spikeai.app.data.LocalPrefs
import kotlinx.coroutines.flow.first

/**
 * Single entry point for task reminders. Keeps the on-device store ([LocalPrefs])
 * and the OS alarm ([NotificationScheduler]) in lock-step so the UI never has to
 * juggle both. All times are epoch-millis in the device's local zone.
 */
object TaskReminders {

    /**
     * Sets (or replaces) a reminder for [taskId]. A time already in the past is
     * treated as "no reminder": we clear any previous alarm instead of firing
     * immediately. Returns true if a future reminder was actually scheduled.
     */
    suspend fun set(
        context: Context,
        taskId: String,
        title: String,
        atEpochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val prefs = LocalPrefs(context.applicationContext)
        if (atEpochMillis <= nowMillis) {
            clear(context, taskId)
            return false
        }
        prefs.setTaskReminder(taskId, atEpochMillis, title)
        NotificationScheduler.scheduleTaskReminder(context, taskId, title, atEpochMillis)
        return true
    }

    /** Cancels and forgets any reminder for [taskId]. */
    suspend fun clear(context: Context, taskId: String) {
        LocalPrefs(context.applicationContext).clearTaskReminder(taskId)
        NotificationScheduler.cancelTaskReminder(context, taskId)
    }

    /**
     * Re-arms every still-future reminder with AlarmManager. Called after a reboot
     * (alarms are wiped on reboot) and is safe to call repeatedly. Past reminders
     * are pruned from the store so it doesn't grow without bound.
     */
    suspend fun rescheduleAll(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val prefs = LocalPrefs(context.applicationContext)
        val reminders = prefs.taskReminders.first()
        reminders.forEach { (taskId, reminder) ->
            if (reminder.atMillis > nowMillis) {
                NotificationScheduler.scheduleTaskReminder(
                    context, taskId, reminder.title, reminder.atMillis,
                )
            } else {
                prefs.clearTaskReminder(taskId)
            }
        }
    }
}
