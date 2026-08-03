package com.spikeai.app.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.spikeai.app.R
import com.spikeai.app.data.LocalPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Lightweight foreground service whose only job is to keep the process alive
 * while a Focus / Lock-in / Sweat session is running, so the accessibility
 * service keeps receiving events and isn't reaped under memory pressure.
 *
 * Declared in the manifest as foregroundServiceType="specialUse".
 */
class FocusForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "spike_focus_session"
        private const val NOTIFICATION_ID = 4201

        /** Re-post the notification from current [FocusController] state (e.g. after a Sweat unlock). */
        const val ACTION_REFRESH = "com.spikeai.app.action.REFRESH_FOCUS_NOTIFICATION"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        // A refresh request only needs the notification rebuilt (done above) — skip
        // the session-restore path so we don't touch a live session's state.
        if (intent?.action == ACTION_REFRESH) return START_STICKY
        // After a process restart the in-memory session is gone — re-arm it from
        // persisted prefs so the accessibility service keeps blocking.
        if (!FocusController.active) {
            runCatching {
                val prefs = LocalPrefs(applicationContext)
                runBlocking {
                    // Re-arm the task gate first so enforcement resumes correctly.
                    FocusController.restoreTaskGate(prefs.hasUncompletedTasks.first())
                    if (prefs.focusActive.first()) {
                        FocusController.restore(
                            mode = prefs.focusMode.first(),
                            packages = prefs.blockedPackages.first(),
                        )
                    }
                }
            }
        }
        // Restart if killed while a session is still meant to be active.
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        super.onDestroy()
    }

    private fun startInForeground() {
        ensureChannel()

        // Tapping the notification reopens Spike.
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("open_focus", true)
        }
        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pending = contentIntent?.let {
            PendingIntent.getActivity(this, 0, it, pendingFlags)
        }

        val notification = buildNotification(pending)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    /**
     * Builds the ongoing notification from the current session state. In Athlete
     * (Sweat) mode with earned access time still on the clock, it shows a live
     * count-down so the user always knows when blocking resumes — even from inside
     * a blocked app. Otherwise it shows the standard "blocking" notification.
     */
    private fun buildNotification(pending: PendingIntent?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val remainingMs = FocusController.unlockUntilMillis - SystemClock.elapsedRealtime()
        if (FocusController.mode == "sweat" && remainingMs > 0L) {
            // Notification chronometers are wall-clock based, so convert the
            // elapsed-realtime deadline to a System.currentTimeMillis() target.
            val endsAtWallClock = System.currentTimeMillis() + remainingMs
            builder
                .setContentTitle("Apps unlocked")
                .setContentText("Time left before blocking resumes — tap to do more reps")
                .setWhen(endsAtWallClock)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder
                .setContentTitle("Focus session active")
                .setContentText("Spike is blocking distracting apps.")
                .setShowWhen(false)
                .setUsesChronometer(false)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while an app-blocking focus session is running."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
